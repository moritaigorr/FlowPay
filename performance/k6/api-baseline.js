import http from 'k6/http'
import { check, group } from 'k6'
import { Counter, Trend } from 'k6/metrics'
import exec from 'k6/execution'

const baseUrl = (__ENV.FLOWPAY_PERF_BASE_URL || 'http://host.docker.internal:8080').replace(/\/$/, '')
const email = __ENV.FLOWPAY_PERF_EMAIL
const password = __ENV.FLOWPAY_PERF_PASSWORD
const customerId = __ENV.FLOWPAY_PERF_CUSTOMER_ID
const detailPaymentId = __ENV.FLOWPAY_PERF_DETAIL_PAYMENT_ID
const createdFrom = __ENV.FLOWPAY_R20_CREATED_FROM
const createdTo = __ENV.FLOWPAY_R20_CREATED_TO
const profile = __ENV.FLOWPAY_PERF_PROFILE || 'smoke'

const unexpectedFailures = new Counter('unexpected_failures')
const expectedConflicts = new Counter('expected_conflicts')
const listPaymentsMs = new Trend('list_payments_ms', true)
const paymentDetailMs = new Trend('payment_detail_ms', true)
const paymentEventsMs = new Trend('payment_events_ms', true)
const paymentCreateMs = new Trend('payment_create_ms', true)
const paymentReplayMs = new Trend('payment_replay_ms', true)
const paymentProcessMs = new Trend('payment_process_ms', true)
const paymentCancelMs = new Trend('payment_cancel_ms', true)
const transitionRaceMs = new Trend('transition_race_ms', true)

const apiHeaders = { Accept: 'application/json, application/problem+json' }
const loadUserAgent = 'FlowPay-R20-k6/1.0'

function ramp(execName) {
  return {
    executor: 'ramping-vus',
    exec: execName,
    startVUs: 1,
    stages: [
      { duration: '15s', target: 2 },
      { duration: '15s', target: 4 },
      { duration: '15s', target: 1 },
      { duration: '5s', target: 0 },
    ],
    gracefulRampDown: '2s',
  }
}

function selectedScenario() {
  if (profile === 'smoke') {
    return { smoke: { executor: 'shared-iterations', exec: 'mixedFlow', vus: 1, iterations: 1 } }
  }
  if (profile === 'reads') return { reads: ramp('readFlow') }
  if (profile === 'mixed') return { mixed: ramp('mixedFlow') }
  if (profile === 'conflict') {
    return { conflict: { executor: 'shared-iterations', exec: 'conflictFlow', vus: 2, iterations: 4 } }
  }
  throw new Error('FLOWPAY_PERF_PROFILE must be smoke, reads, mixed, or conflict.')
}

export const options = {
  scenarios: selectedScenario(),
  noCookiesReset: true,
  thresholds: {
    unexpected_failures: ['count==0'],
  },
}

export function setup() {
  if (!email || !password || !customerId || !detailPaymentId || !createdFrom || !createdTo) {
    throw new Error('The disposable fixture is incomplete; run performance/k6/run-r20.ps1.')
  }
  return { customerId, detailPaymentId, createdFrom, createdTo }
}

function fail(operation, response, expected) {
  const ok = check(response, { [`${operation} returned ${expected}`]: result => result && result.status === expected })
  if (!ok) {
    unexpectedFailures.add(1, { operation })
    let code = 'unavailable'
    let requestId = 'unavailable'
    let fields = 'none'
    try {
      const body = response.json()
      if (body && typeof body.code === 'string' && /^[A-Z0-9_.-]{1,64}$/i.test(body.code)) code = body.code
      if (body && typeof body.requestId === 'string' && /^[A-Za-z0-9_-]{1,80}$/.test(body.requestId)) requestId = body.requestId
      const allowedFields = new Set(['createdFrom', 'createdTo', 'customerId', 'status', 'page', 'size', 'q'])
      if (body && Array.isArray(body.fieldErrors)) {
        const rejected = body.fieldErrors.map(error => error && error.field).filter(field => allowedFields.has(field))
        if (rejected.length) fields = [...new Set(rejected)].join(',')
      }
    } catch (_) { /* An empty or non-JSON error response contains no safe diagnostic fields. */ }
    if (!__VU_STATE.lastFailureAt || Date.now() - __VU_STATE.lastFailureAt > 5000) {
      console.error(`R20 unexpected response operation=${operation} expected=${expected} status=${response ? response.status : 'no-response'} code=${code} fields=${fields} requestId=${requestId}`)
      __VU_STATE.lastFailureAt = Date.now()
    }
  }
  return ok
}

function auth() {
  // Reuse the VU's authenticated session across iterations; k6's noCookiesReset keeps its jar.
  if (__VU_STATE.csrfToken) return __VU_STATE.csrfToken

  const anonymousCsrf = http.get(`${baseUrl}/api/v1/auth/csrf`, {
    headers: { ...apiHeaders, 'User-Agent': loadUserAgent },
    tags: { operation: 'auth_csrf', name: '/api/v1/auth/csrf' },
  })
  if (!fail('auth_csrf', anonymousCsrf, 200)) return null
  const token = anonymousCsrf.json('token')
  const csrfHeader = anonymousCsrf.json('headerName')
  if (!token || csrfHeader !== 'X-CSRF-TOKEN') {
    unexpectedFailures.add(1, { operation: 'auth_csrf_contract' })
    return null
  }

  const login = http.post(`${baseUrl}/api/v1/auth/login`, JSON.stringify({ email, password }), {
    headers: { ...apiHeaders, 'User-Agent': loadUserAgent, 'Content-Type': 'application/json', [csrfHeader]: token },
    tags: { operation: 'auth_login', name: '/api/v1/auth/login' },
  })
  if (!fail('auth_login', login, 200)) return null
  const identity = login.json()
  if (identity.company.demo || !identity.company.id || identity.company.id === '') {
    unexpectedFailures.add(1, { operation: 'auth_tenant_contract' })
    return null
  }

  const authenticatedCsrf = http.get(`${baseUrl}/api/v1/auth/csrf`, {
    headers: { ...apiHeaders, 'User-Agent': loadUserAgent },
    tags: { operation: 'auth_csrf_after_login', name: '/api/v1/auth/csrf' },
  })
  if (!fail('auth_csrf_after_login', authenticatedCsrf, 200)) return null
  const renewedToken = authenticatedCsrf.json('token')
  if (!renewedToken) {
    unexpectedFailures.add(1, { operation: 'auth_csrf_contract' })
    return null
  }
  __VU_STATE.csrfToken = renewedToken
  __VU_STATE.csrfHeader = authenticatedCsrf.json('headerName')
  __VU_STATE.userId = identity.user.id
  __VU_STATE.companyId = identity.company.id
  return renewedToken
}

// k6 gives each VU its own JS runtime; this cache deliberately keeps one cookie jar/session per VU.
let __VU_STATE = { csrfToken: null, csrfHeader: null, userId: null, companyId: null }

function nextKey(operation) {
  return `r20-${operation}-${exec.vu.idInTest}-${exec.scenario.iterationInTest}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

function timed(metric, operation, request) {
  const response = request()
  metric.add(response.timings.duration)
  return response
}

function guarded(operation, callback) {
  return function (data) {
    try {
      callback(data)
    } catch (_) {
      unexpectedFailures.add(1, { operation })
    }
  }
}

function performReadFlow(data) {
  if (!auth()) return false
  return group('read_list_detail_history', () => {
    const query = `page=0&size=20&status=PENDING&customerId=${encodeURIComponent(data.customerId)}&createdFrom=${encodeURIComponent(data.createdFrom)}&createdTo=${encodeURIComponent(data.createdTo)}`
    const list = timed(listPaymentsMs, 'payments_list', () => http.get(`${baseUrl}/api/v1/payments?${query}`, {
      headers: apiHeaders,
      tags: { operation: 'payments_list', name: '/api/v1/payments' },
    }))
    if (!fail('payments_list', list, 200)) return false
    const listBody = list.json()
    if (!Array.isArray(listBody.items) || listBody.items.length > 20 || listBody.totalElements < 1 || listBody.size !== 20) {
      unexpectedFailures.add(1, { operation: 'payments_list_contract' })
      return false
    }

    const detail = timed(paymentDetailMs, 'payment_detail', () => http.get(`${baseUrl}/api/v1/payments/${data.detailPaymentId}`, {
      headers: apiHeaders,
      tags: { operation: 'payment_detail', name: '/api/v1/payments/{id}' },
    }))
    if (!fail('payment_detail', detail, 200)) return false
    const payment = detail.json()
    if (payment.id !== data.detailPaymentId || payment.status !== 'PENDING' || typeof payment.amount !== 'string') {
      unexpectedFailures.add(1, { operation: 'payment_detail_contract' })
      return false
    }

    const history = timed(paymentEventsMs, 'payment_events', () => http.get(`${baseUrl}/api/v1/payments/${data.detailPaymentId}/events`, {
      headers: apiHeaders,
      tags: { operation: 'payment_events', name: '/api/v1/payments/{id}/events' },
    }))
    if (!fail('payment_events', history, 200)) return false
    if (!Array.isArray(history.json()) || history.json().length < 1) {
      unexpectedFailures.add(1, { operation: 'payment_events_contract' })
      return false
    }
    return true
  })
}

function createPending(operation) {
  const csrfToken = auth()
  if (!csrfToken) return null
  const amount = '19.85'
  const payload = {
    customerId,
    amount,
    currency: 'BRL',
    description: `R20 ${operation} synthetic payment`,
    simulationScenario: 'APPROVE',
  }
  const key = nextKey(operation)
  const response = timed(paymentCreateMs, 'payment_create_fresh', () => http.post(`${baseUrl}/api/v1/payments`, JSON.stringify(payload), {
    headers: { ...apiHeaders, 'Content-Type': 'application/json', [__VU_STATE.csrfHeader]: csrfToken, 'Idempotency-Key': key },
    tags: { operation: 'payment_create_fresh', name: '/api/v1/payments' },
  }))
  if (!fail('payment_create_fresh', response, 201)) return null
  const payment = response.json()
  if (!payment.id || payment.status !== 'PENDING' || payment.amount !== amount || payment.customerId !== customerId) {
    unexpectedFailures.add(1, { operation: 'payment_create_contract' })
    return null
  }
  return { id: payment.id, key, payload, responseBody: response.body }
}

function replayPayment(payment) {
  const response = timed(paymentReplayMs, 'payment_create_replay', () => http.post(`${baseUrl}/api/v1/payments`, JSON.stringify(payment.payload), {
    headers: { ...apiHeaders, 'Content-Type': 'application/json', [__VU_STATE.csrfHeader]: __VU_STATE.csrfToken, 'Idempotency-Key': payment.key },
    tags: { operation: 'payment_create_replay', name: '/api/v1/payments' },
  }))
  if (!fail('payment_create_replay', response, 201)) return false
  if (response.headers['Idempotency-Replayed'] !== 'true' || response.body !== payment.responseBody || response.json('id') !== payment.id) {
    unexpectedFailures.add(1, { operation: 'payment_replay_contract' })
    return false
  }
  return true
}

function transition(payment, action, expectedState) {
  const metric = action === 'process' ? paymentProcessMs : paymentCancelMs
  const response = timed(metric, `payment_${action}`, () => http.post(`${baseUrl}/api/v1/payments/${payment.id}/${action}`, null, {
    headers: { ...apiHeaders, [__VU_STATE.csrfHeader]: __VU_STATE.csrfToken },
    tags: { operation: `payment_${action}`, name: `/api/v1/payments/{id}/${action}` },
  }))
  if (!fail(`payment_${action}`, response, 200)) return false
  if (response.json('id') !== payment.id || response.json('status') !== expectedState) {
    unexpectedFailures.add(1, { operation: `payment_${action}_contract` })
    return false
  }
  return true
}

function performMixedFlow(data) {
  if (!performReadFlow(data)) return
  const approved = createPending('process')
  if (!approved || !replayPayment(approved) || !transition(approved, 'process', 'APPROVED')) return

  const cancelled = createPending('cancel')
  if (!cancelled || !transition(cancelled, 'cancel', 'CANCELLED')) return
}

function performConflictFlow() {
  if (!auth()) return
  const pending = createPending('race')
  if (!pending) return

  const processUrl = `${baseUrl}/api/v1/payments/${pending.id}/process`
  const cancelUrl = `${baseUrl}/api/v1/payments/${pending.id}/cancel`
  const requestHeaders = { ...apiHeaders, [__VU_STATE.csrfHeader]: __VU_STATE.csrfToken }
  const started = Date.now()
  const responses = http.batch([
    ['POST', processUrl, null, { headers: requestHeaders, responseCallback: http.expectedStatuses(200, 409), tags: { operation: 'transition_race_process', name: '/api/v1/payments/{id}/process' } }],
    ['POST', cancelUrl, null, { headers: requestHeaders, responseCallback: http.expectedStatuses(200, 409), tags: { operation: 'transition_race_cancel', name: '/api/v1/payments/{id}/cancel' } }],
  ])
  transitionRaceMs.add(Date.now() - started)
  const statuses = responses.map(response => response.status).sort((a, b) => a - b)
  const validRace = check(statuses, { 'one competing transition commits and one conflicts': values => values[0] === 200 && values[1] === 409 })
  if (!validRace) {
    unexpectedFailures.add(1, { operation: 'transition_race' })
    return
  }
  expectedConflicts.add(1)

  const detail = http.get(`${baseUrl}/api/v1/payments/${pending.id}`, {
    headers: apiHeaders,
    tags: { operation: 'transition_race_detail', name: '/api/v1/payments/{id}' },
  })
  if (!fail('transition_race_detail', detail, 200)) return
  const finalStatus = detail.json('status')
  if (finalStatus !== 'APPROVED' && finalStatus !== 'CANCELLED') {
    unexpectedFailures.add(1, { operation: 'transition_race_final_state' })
    return
  }
  const history = http.get(`${baseUrl}/api/v1/payments/${pending.id}/events`, {
    headers: apiHeaders,
    tags: { operation: 'transition_race_events', name: '/api/v1/payments/{id}/events' },
  })
  if (!fail('transition_race_events', history, 200)) return
  const events = history.json()
  if (!Array.isArray(events) || events.length !== 2 || events[1].newStatus !== finalStatus) {
    unexpectedFailures.add(1, { operation: 'transition_race_history' })
  }
}

export const readFlow = guarded('read_flow_exception', performReadFlow)
export const mixedFlow = guarded('mixed_flow_exception', performMixedFlow)
export const conflictFlow = guarded('conflict_flow_exception', performConflictFlow)

export function teardown() {
  // Keep the temporary synthetic password out of k6's teardown lifecycle data.
}

