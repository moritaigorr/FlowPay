const baseUrl = process.env.FLOWPAY_PERF_HOST_BASE_URL ?? 'http://127.0.0.1:8080'
const email = process.env.FLOWPAY_PERF_EMAIL
const password = process.env.FLOWPAY_PERF_PASSWORD

if (!email || !password) throw new Error('Set FLOWPAY_PERF_EMAIL and FLOWPAY_PERF_PASSWORD in the process environment.')

const cookies = new Map()
function saveCookies(response) {
  for (const cookie of response.headers.getSetCookie()) {
    const pair = cookie.split(';', 1)[0]
    const separator = pair.indexOf('=')
    if (separator < 1) continue
    const name = pair.slice(0, separator)
    const value = pair.slice(separator + 1)
    if (!value) cookies.delete(name)
    else cookies.set(name, value)
  }
}

async function request(path, { method = 'GET', body, headers = {} } = {}) {
  const requestHeaders = new Headers({ Accept: 'application/json, application/problem+json', ...headers })
  if (body !== undefined) requestHeaders.set('Content-Type', 'application/json')
  if (cookies.size) requestHeaders.set('Cookie', [...cookies].map(([name, value]) => `${name}=${value}`).join('; '))
  const response = await fetch(`${baseUrl}/api/v1${path}`, {
    method,
    headers: requestHeaders,
    body: body === undefined ? undefined : JSON.stringify(body),
    redirect: 'manual',
  })
  saveCookies(response)
  return response
}

async function expect(response, status, operation) {
  if (response.status !== status) throw new Error(`${operation} returned HTTP ${response.status}; expected ${status}.`)
  if (status === 204) return undefined
  return response.json()
}

const csrfAnonymous = await expect(await request('/auth/csrf'), 200, 'Initial CSRF read')
if (csrfAnonymous.headerName !== 'X-CSRF-TOKEN' || !csrfAnonymous.token) throw new Error('Initial CSRF response is invalid.')
await expect(await request('/auth/login', {
  method: 'POST',
  headers: { [csrfAnonymous.headerName]: csrfAnonymous.token },
  body: { email, password },
}), 200, 'Operator login')
const csrfAuthenticated = await expect(await request('/auth/csrf'), 200, 'Authenticated CSRF read')
if (csrfAuthenticated.headerName !== 'X-CSRF-TOKEN' || !csrfAuthenticated.token) throw new Error('Authenticated CSRF response is invalid.')

const identity = await expect(await request('/auth/me'), 200, 'Identity read')
if (identity.company.demo) throw new Error('The load-test tenant must be a conventional, non-demo company.')

const customer = await expect(await request('/customers', {
  method: 'POST',
  headers: { [csrfAuthenticated.headerName]: csrfAuthenticated.token },
  body: { name: 'R20 Synthetic Customer', email: 'r20-fixture@example.test' },
}), 201, 'Synthetic customer creation')

const fixtureStartedAt = new Date(Date.now() - 60_000).toISOString()
let pendingPaymentId
const seedCount = 60
for (let index = 0; index < seedCount; index++) {
  const cents = 1900 + (index % 300)
  const amount = `${Math.floor(cents / 100)}.${String(cents % 100).padStart(2, '0')}`
  const scenario = index % 3 === 2 ? 'DECLINE' : 'APPROVE'
  const creationKey = `r20-seed-${index}`
  const payment = await expect(await request('/payments', {
    method: 'POST',
    headers: { [csrfAuthenticated.headerName]: csrfAuthenticated.token, 'Idempotency-Key': creationKey },
    body: {
      customerId: customer.id,
      amount,
      currency: 'BRL',
      description: `R20 seed ${index}`,
      simulationScenario: scenario,
    },
  }), 201, `Payment fixture ${index} creation`)
  if (payment.customerId !== customer.id || payment.amount !== amount || payment.status !== 'PENDING') {
    throw new Error(`Payment fixture ${index} did not preserve its request values.`)
  }

  if (index === 0) pendingPaymentId = payment.id
  else if (index % 3 !== 1) {
    const expectedStatus = scenario === 'DECLINE' ? 'REJECTED' : 'APPROVED'
    const finalPayment = await expect(await request(`/payments/${payment.id}/process`, {
      method: 'POST',
      headers: { [csrfAuthenticated.headerName]: csrfAuthenticated.token },
    }), 200, `Payment fixture ${index} processing`)
    if (finalPayment.id !== payment.id || finalPayment.status !== expectedStatus) {
      throw new Error(`Payment fixture ${index} reached an unexpected final state.`)
    }
  }
}

// Capture the upper bound after all inserts and transitions, which can take several seconds.
const fixtureEndedAt = new Date(Date.now() + 60_000).toISOString()
const page = await expect(await request(`/payments?page=0&size=100&customerId=${customer.id}&createdFrom=${encodeURIComponent(fixtureStartedAt)}&createdTo=${encodeURIComponent(fixtureEndedAt)}`), 200, 'Seed postcondition read')
if (page.totalElements !== seedCount || page.items.length !== seedCount || page.items.some(payment => typeof payment.amount !== 'string')) {
  throw new Error('The disposable tenant dataset postcondition failed.')
}

const pending = await expect(await request(`/payments/${pendingPaymentId}`), 200, 'Pending detail postcondition')
const pendingEvents = await expect(await request(`/payments/${pendingPaymentId}/events`), 200, 'Pending history postcondition')
if (pending.status !== 'PENDING' || !Array.isArray(pendingEvents) || pendingEvents.length !== 1) {
  throw new Error('The pending payment fixture is not ready for the concurrency scenario.')
}

await expect(await request('/auth/logout', {
  method: 'POST',
  headers: { [csrfAuthenticated.headerName]: csrfAuthenticated.token },
}), 204, 'Fixture setup logout')

process.stdout.write(JSON.stringify({
  companyId: identity.company.id,
  customerId: customer.id,
  detailPaymentId: pendingPaymentId,
  fixtureFrom: fixtureStartedAt,
  fixtureTo: fixtureEndedAt,
  seededPayments: seedCount,
  expectedStates: { PENDING: 21, APPROVED: 19, REJECTED: 20 },
}) + '\n')
