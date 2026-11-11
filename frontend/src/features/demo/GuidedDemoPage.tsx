import { useState } from 'react'
import type { FormEvent } from 'react'
import { ArrowRight, RotateCcw, ShieldCheck } from 'lucide-react'
import { Link, useNavigate } from 'react-router'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Button } from '../../components/ui/button'
import { StatusBadge } from '../../components/shared/status-badge'
import { ApiError } from '../../lib/http'
import { sessionKey } from '../../lib/query-client'
import { listCustomers } from '../customers/api'
import type { Customer } from '../customers/api'
import { useAuth } from '../auth/auth-context'
import type { Payment, PaymentEvent, PaymentStatus } from '../payments/api'
import { createPaymentWithMetadata, getPayment, getPaymentEvents, processPayment } from '../payments/api'
import type { PaymentPayload } from '../payments/payment-attempt'
import { beginAttempt } from '../payments/payment-attempt'
import { paymentFormSchema } from '../payments/schemas'
import { invalidatePaymentViews } from '../payments/hooks'
import { formatBrl } from '../payments/money'
import './guided-demo.css'

const interruptedKey = 'flowpay.guided-demo.active'
type GuideFields = { customerId: string; amount: string; description: string; simulationScenario: 'APPROVE' | 'DECLINE' }
const initialFields: GuideFields = { customerId: '', amount: '', description: '', simulationScenario: 'APPROVE' }

interface ReplayEvidence {
  status: number
  payment: Payment
  replayHeader: string | null
  location: string | null
}

export function GuidedDemoPage() {
  const auth = useAuth()
  const navigate = useNavigate()
  const scope = auth.scope ?? ''
  const client = useQueryClient()
  const [fields, setFields] = useState<GuideFields>(initialFields)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [attempt, setAttempt] = useState<ReturnType<typeof beginAttempt> | null>(null)
  const [created, setCreated] = useState<Payment | null>(null)
  const [createdStatus, setCreatedStatus] = useState<number>()
  const [replay, setReplay] = useState<ReplayEvidence | null>(null)
  const [finalPayment, setFinalPayment] = useState<Payment | null>(null)
  const [events, setEvents] = useState<PaymentEvent[]>([])
  const [busy, setBusy] = useState<'create' | 'replay' | 'process' | 'demo' | null>(null)
  const [error, setError] = useState<Error | null>(null)
  const [successMessage, setSuccessMessage] = useState<string>()
  const [restartAfterReload, setRestartAfterReload] = useState(readInterruptedMarker)
  const customersQuery = useQuery({
    queryKey: sessionKey(scope, 'guided-demo-customers'),
    queryFn: ({ signal }) => listCustomers({ page: 0, size: 100, q: '' }, signal),
    enabled: auth.session?.company.demo === true && !restartAfterReload,
  })
  const customers: Customer[] = customersQuery.data?.items ?? []
  const replayVerified = !!created && !!replay && replay.payment.id === created.id && replay.replayHeader === 'true'

  async function startDemoWorkspace() {
    setBusy('demo')
    setError(null)
    try {
      await auth.enterDemo()
    } catch (failure) {
      setError(asError(failure))
    } finally {
      setBusy(null)
    }
  }

  function openFreshDemoWorkspace() {
    clearGuideMarker()
    navigate('/login', { replace: true, state: { from: '/demo', freshDemoWorkspace: true } })
  }

  async function submitCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    setFieldErrors({})
    setError(null)
    const parsed = paymentFormSchema.safeParse(fields)
    if (!parsed.success) {
      setFieldErrors(Object.fromEntries(parsed.error.issues.map((issue) => [String(issue.path[0] ?? 'form'), issue.message])))
      return
    }
    const payload: PaymentPayload = {
      customerId: parsed.data.customerId,
      amount: parsed.data.amount,
      currency: 'BRL',
      description: parsed.data.description || null,
      simulationScenario: parsed.data.simulationScenario,
    }
    await createAttempt(beginAttempt(payload))
  }

  async function createAttempt(selected: ReturnType<typeof beginAttempt>) {
    setAttempt(selected)
    setCreated(null)
    setReplay(null)
    setFinalPayment(null)
    setEvents([])
    setBusy('create')
    setError(null)
    setSuccessMessage(undefined)
    markGuideActive()
    try {
      const response = await createPaymentWithMetadata(selected.payload, selected.key)
      setCreated(response.data)
      setCreatedStatus(response.status)
      await invalidatePaymentViews(client, scope)
      setSuccessMessage(`Payment ${response.data.id} was created as Pending.`)
    } catch (failure) {
      setError(asError(failure))
    } finally {
      setBusy(null)
    }
  }

  async function replayOriginalRequest() {
    if (!attempt || !created || busy) return
    setBusy('replay')
    setError(null)
    setSuccessMessage(undefined)
    try {
      const response = await createPaymentWithMetadata(attempt.payload, attempt.key)
      const evidence: ReplayEvidence = {
        status: response.status,
        payment: response.data,
        replayHeader: response.headers.get('Idempotency-Replayed'),
        location: response.headers.get('Location'),
      }
      setReplay(evidence)
      if (evidence.payment.id !== created.id || evidence.replayHeader !== 'true') {
        setError(new Error('The API response did not confirm a replay of the original payment. The guide will not continue.'))
        return
      }
      setSuccessMessage(`Replay confirmed: the original payment ID ${created.id} was returned again.`)
    } catch (failure) {
      setError(asError(failure))
    } finally {
      setBusy(null)
    }
  }

  async function processAndLoadHistory() {
    if (!created || !replayVerified || busy) return
    setBusy('process')
    setError(null)
    setSuccessMessage(undefined)
    try {
      const updated = await processPayment(created.id)
      await invalidatePaymentViews(client, scope, created.id)
      const history = await getPaymentEvents(created.id)
      setFinalPayment(updated)
      setEvents(history)
      setSuccessMessage(`The server confirmed ${labelStatus(updated.status)}. Event history was loaded from the API.`)
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 409) {
        try {
          const current = await getPayment(created.id)
          const history = await getPaymentEvents(created.id)
          await invalidatePaymentViews(client, scope, created.id)
          setFinalPayment(current)
          setEvents(history)
          setError(new ApiError(409, failure.code, 'This payment changed before the guide could process it. The current server state and history have been refreshed.', failure.requestId))
        } catch (refreshFailure) {
          setError(asError(refreshFailure))
        }
      } else {
        setError(asError(failure))
      }
    } finally {
      setBusy(null)
    }
  }

  function restartGuide() {
    clearGuideMarker()
    setRestartAfterReload(false)
    setFields(initialFields)
    setFieldErrors({})
    setAttempt(null)
    setCreated(null)
    setCreatedStatus(undefined)
    setReplay(null)
    setFinalPayment(null)
    setEvents([])
    setError(null)
    setSuccessMessage(undefined)
  }

  if (restartAfterReload) return <section className="guided-demo-page">
    <header className="page-heading"><p className="guided-eyebrow">Interactive walkthrough</p><h1 tabIndex={-1}>Restart the guided demo</h1><p>The request snapshot and idempotency key existed only in memory and are no longer available after this page reload.</p></header>
    <div className="guided-notice" role="status"><ShieldCheck aria-hidden="true" /><p>No replay will be claimed from the previous run. Start again to create a fresh simulated payment and capture a new request.</p></div>
    <Button onClick={restartGuide}><RotateCcw aria-hidden="true" />Restart guided demo</Button>
  </section>

  if (auth.session?.company.demo !== true) return <section className="guided-demo-page">
    <header className="page-heading"><p className="guided-eyebrow">Interactive walkthrough</p><h1 tabIndex={-1}>Enter an isolated demo workspace</h1><p>The walkthrough uses a synthetic workspace so its customer and payment data stay separate from a provisioned operator account.</p></header>
    {error && <ErrorNotice error={error} />}
    <Button onClick={() => void startDemoWorkspace()} disabled={busy === 'demo'}>{busy === 'demo' ? 'Preparing demo…' : 'Enter isolated demo workspace'}<ArrowRight aria-hidden="true" /></Button>
  </section>

  const stage = finalPayment ? 4 : replayVerified ? 3 : created ? 2 : 1
  return <section className="guided-demo-page">
    <header className="page-heading guided-heading">
      <div><p className="guided-eyebrow">Interactive walkthrough · Step {stage} of 4</p><h1 tabIndex={-1}>Prove a payment replay</h1><p>Create a simulated payment, repeat its exact request, then process it and inspect the API history.</p></div>
      <Button variant="secondary" onClick={restartGuide} disabled={!!busy}><RotateCcw aria-hidden="true" />Restart guide</Button>
    </header>

    <div className="guided-notice" role="note"><ShieldCheck aria-hidden="true" /><p><strong>Isolated demo data.</strong> This is a personal project simulation; no real money is processed.</p></div>
    {error && <ErrorNotice error={error} onOpenFreshDemo={error instanceof ApiError && error.code === 'DEMO_LIMIT_REACHED' ? openFreshDemoWorkspace : undefined} />}
    {successMessage && <p className="guided-success" role="status">{successMessage}</p>}

    {!created && <section className="guided-panel" aria-labelledby="guided-create-title">
      <div className="guided-panel-heading"><span className="guided-step-index">1</span><div><h2 id="guided-create-title">Create a pending payment</h2><p>Choose one of the seeded customers and a deterministic outcome.</p></div></div>
      {customersQuery.isPending && <p className="guided-feedback" role="status">Loading demo customers…</p>}
      {customersQuery.isError && <div className="guided-feedback" role="alert"><p>{messageFor(customersQuery.error)}</p><Button variant="secondary" onClick={() => void customersQuery.refetch()}>Retry customer list</Button></div>}
      {customersQuery.isSuccess && customers.length === 0 && <div className="guided-feedback" role="status"><p>No synthetic customers are available in this workspace.</p><Link to="/customers">Open customers</Link></div>}
      {customersQuery.isSuccess && customers.length > 0 && <form className="guided-form" onSubmit={(event) => void submitCreate(event)} noValidate>
        <div className="guided-field"><label htmlFor="guided-customer">Customer</label><select id="guided-customer" value={fields.customerId} onChange={(event) => setFields((value) => ({ ...value, customerId: event.target.value }))} aria-invalid={!!fieldErrors.customerId} aria-describedby={fieldErrors.customerId ? 'guided-customer-error' : undefined}><option value="">Choose a synthetic customer</option>{customers.map((customer) => <option value={customer.id} key={customer.id}>{customer.name} · {customer.email}</option>)}</select>{fieldErrors.customerId && <p className="field-error" id="guided-customer-error">{fieldErrors.customerId}</p>}</div>
        <div className="guided-field"><label htmlFor="guided-amount">Amount (BRL)</label><input id="guided-amount" inputMode="decimal" autoComplete="off" value={fields.amount} onChange={(event) => setFields((value) => ({ ...value, amount: event.target.value }))} placeholder="49.90" aria-invalid={!!fieldErrors.amount} aria-describedby={fieldErrors.amount ? 'guided-amount-error' : 'guided-amount-help'} />{fieldErrors.amount ? <p className="field-error" id="guided-amount-error">{fieldErrors.amount}</p> : <p className="field-help" id="guided-amount-help">Use a decimal point and up to two decimal places.</p>}</div>
        <div className="guided-field"><label htmlFor="guided-description">Description <span className="guided-optional">Optional</span></label><input id="guided-description" maxLength={500} value={fields.description} onChange={(event) => setFields((value) => ({ ...value, description: event.target.value }))} aria-invalid={!!fieldErrors.description} aria-describedby={fieldErrors.description ? 'guided-description-error' : undefined} />{fieldErrors.description && <p className="field-error" id="guided-description-error">{fieldErrors.description}</p>}</div>
        <fieldset className="guided-scenarios"><legend>Simulation outcome</legend><label><input type="radio" name="guided-scenario" checked={fields.simulationScenario === 'APPROVE'} onChange={() => setFields((value) => ({ ...value, simulationScenario: 'APPROVE' }))} />Simulate approval</label><label><input type="radio" name="guided-scenario" checked={fields.simulationScenario === 'DECLINE'} onChange={() => setFields((value) => ({ ...value, simulationScenario: 'DECLINE' }))} />Simulate decline</label><p>The payment begins Pending. Processing applies this fixed outcome.</p></fieldset>
        {fieldErrors.form && <p className="form-error" role="alert">{fieldErrors.form}</p>}
        <Button type="submit" disabled={!!busy || !fields.customerId}>{busy === 'create' ? 'Creating payment…' : 'Create simulated payment'}<ArrowRight aria-hidden="true" /></Button>
      </form>}
    </section>}

    {attempt && !created && error && canRetryCreate(error) && <section className="guided-panel" aria-labelledby="retry-attempt-title"><h2 id="retry-attempt-title">Keep the original attempt</h2><p>If the response was lost or the service is temporarily unavailable, retry with this same request and key. The API decides whether the original operation committed.</p><Button variant="secondary" disabled={!!busy} onClick={() => void createAttempt(attempt)}>{busy === 'create' ? 'Retrying…' : 'Retry same request'}</Button></section>}

    {created && attempt && <section className="guided-panel guided-step-panel" aria-labelledby="guided-replay-title">
      <div className="guided-panel-heading"><span className="guided-step-index">{replayVerified ? '✓' : '2'}</span><div><h2 id="guided-replay-title">Repeat the exact request</h2><p>The original JSON payload and Idempotency-Key are held in this page's memory.</p></div></div>
      <div className="guided-payment-summary"><span>Original payment</span><strong>{created.id}</strong><div><StatusBadge status={created.status} /><span>{formatBrl(created.amount)}</span></div></div>
      {replay && <div className={replayVerified ? 'guided-replay-result is-confirmed' : 'guided-replay-result'} role={replayVerified ? 'status' : 'alert'}>
        <strong>{replayVerified ? 'Replay confirmed by the API' : 'Replay could not be confirmed'}</strong>
        <p>Original ID: <code>{created.id}</code></p><p>Returned ID: <code>{replay.payment.id}</code></p>
        <p>Idempotency-Replayed: <code>{replay.replayHeader ?? 'header missing'}</code></p>
        <p>HTTP status: <code>{replay.status}</code></p>
      </div>}
      {!replayVerified && <Button onClick={() => void replayOriginalRequest()} disabled={!!busy}>{busy === 'replay' ? 'Replaying…' : 'Replay exact request'}<ArrowRight aria-hidden="true" /></Button>}
      {replayVerified && !finalPayment && <div className="guided-next-step"><div className="guided-panel-heading"><span className="guided-step-index">3</span><div><h2>Process the pending payment</h2><p>The backend will apply the configured outcome and commit a history event.</p></div></div><Button onClick={() => void processAndLoadHistory()} disabled={!!busy}>{busy === 'process' ? 'Processing…' : 'Process payment'}<ArrowRight aria-hidden="true" /></Button></div>}
      {finalPayment && <div className="guided-final-payment"><h2>Server-confirmed outcome</h2><StatusBadge status={finalPayment.status} /><p>{formatBrl(finalPayment.amount)} · {finalPayment.currency} · {finalPayment.simulationScenario === 'APPROVE' ? 'Approval scenario' : 'Simulated decline scenario'}</p><p className="guided-replay-explanation">The replay above returns the original 201 response with Pending status. This outcome and history come from separate API calls after processing.</p><h3>Payment history</h3>{events.length === 0 ? <p role="status">The API returned no history events for this payment.</p> : <ol className="guided-event-list" aria-label="Payment history">{events.map((item) => <li key={item.id}><strong>{item.previousStatus === null ? 'Created' : 'Status changed'}</strong><StatusBadge status={item.newStatus as PaymentStatus} /><p>{item.previousStatus === null ? 'Payment was created as Pending.' : `Changed from ${labelStatus(item.previousStatus)} to ${labelStatus(item.newStatus)}.`}</p>{item.reasonCode && <p>Reason: simulated decline.</p>}<time dateTime={item.createdAt}>{formatTimestamp(item.createdAt)}</time></li>)}</ol>}</div>}
      <details className="guided-inspector"><summary>Inspect request and response</summary><div><h3>Captured request</h3><pre>{JSON.stringify({ method: 'POST', path: '/api/v1/payments', idempotencyKey: attempt.key, body: attempt.payload }, null, 2)}</pre>{createdStatus !== undefined && <><h3>Original response</h3><pre>{JSON.stringify({ status: createdStatus, payment: created }, null, 2)}</pre></>}{replay && <><h3>Replay response</h3><pre>{JSON.stringify({ status: replay.status, idempotencyReplayed: replay.replayHeader, location: replay.location, payment: replay.payment }, null, 2)}</pre></>}</div></details>
    </section>}
  </section>
}

function ErrorNotice({ error, onOpenFreshDemo, busy = false }: { error: Error; onOpenFreshDemo?: () => void; busy?: boolean }) {
  const title = error instanceof ApiError && error.status === 401 ? 'Demo session expired'
    : error instanceof ApiError && error.status === 409 ? 'The API rejected this conflict'
    : error instanceof ApiError && error.status === 429 ? 'Too many requests'
    : error instanceof ApiError && error.status === 503 ? 'The demo service is temporarily unavailable'
    : 'The guided demo could not continue'
  const message = messageFor(error)
  return <div className="guided-error" role="alert"><h2>{title}</h2><p>{message}</p>{error instanceof ApiError && error.requestId && <small>Reference: {error.requestId}</small>}{error instanceof ApiError && error.status === 401 && <Link to="/login">Sign in again</Link>}{onOpenFreshDemo && <Button variant="secondary" onClick={onOpenFreshDemo} disabled={busy}>{busy ? 'Preparing a fresh workspace…' : 'Open a fresh demo workspace'}</Button>}</div>
}

function messageFor(error: Error): string {
  if (!(error instanceof ApiError)) return error.message || 'Check your connection and try again.'
  if (error.status === 401 || error.code === 'DEMO_EXPIRED') return 'Your demo session ended. Sign in again; the saved request is cleared when authentication changes.'
  if (error.status === 409 && error.code === 'DEMO_LIMIT_REACHED') return 'This demo workspace reached its payment limit. A fresh isolated workspace has its own quota; choose below to enter one. No workspace is started automatically.'
  if (error.status === 409 && error.code === 'IDEMPOTENCY_KEY_REUSED') return 'The key is already associated with a different request. The guide will not generate a replacement key silently; restart it to begin a new attempt.'
  if (error.status === 409) return 'The payment state or request conflicts with a server-side change. Review the current state before starting a new attempt.'
  if (error.status === 429) return 'The request rate limit was reached. Wait before entering a new demo session or retrying.'
  if (error.status === 503 && error.code === 'DEMO_CAPACITY_REACHED') return 'All demo workspaces are temporarily occupied. Please try again shortly.'
  if (error.status === 503) return 'The server could not confirm the operation. You may retry using the same captured request and idempotency key.'
  return error.message || 'The guided demo could not continue. Please try again.'
}

function readInterruptedMarker() {
  try { return typeof window !== 'undefined' && window.sessionStorage.getItem(interruptedKey) === 'active' } catch { return false }
}
function canRetryCreate(error: Error) { return !(error instanceof ApiError) || error.status === 429 || error.status === 503 }
function markGuideActive() { try { window.sessionStorage.setItem(interruptedKey, 'active') } catch { /* The request and key remain in memory even if storage is unavailable. */ } }
function clearGuideMarker() { try { window.sessionStorage.removeItem(interruptedKey) } catch { /* Storage is only a reload marker. */ } }
function asError(value: unknown): Error { return value instanceof Error ? value : new Error('The guided demo could not continue. Please try again.') }
function labelStatus(status: string) { return status[0] + status.slice(1).toLowerCase() }
function formatTimestamp(value: string) { return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) }
