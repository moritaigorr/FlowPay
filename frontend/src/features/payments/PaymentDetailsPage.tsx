import { ArrowLeft, CalendarClock, CreditCard, Fingerprint, UserRound } from 'lucide-react'
import { Link, useParams } from 'react-router'
import { ApiError } from '../../lib/http'
import { useAuth } from '../auth/auth-context'
import { Button } from '../../components/ui/button'
import { StatusBadge } from '../../components/shared/status-badge'
import { formatBrl } from './money'
import { usePayment, usePaymentEvents } from './hooks'
import { PaymentActions } from './PaymentActions'
import { PaymentTimeline } from './PaymentTimeline'
import './payment-details.css'

export function PaymentDetailsPage() {
  const auth = useAuth()
  const scope = auth.scope ?? ''
  const paymentId = useParams().paymentId ?? ''
  const paymentQuery = usePayment(scope, paymentId)
  const eventsQuery = usePaymentEvents(scope, paymentId)

  if (paymentQuery.isPending || eventsQuery.isPending) return <section className="business-page"><p className="list-message" role="status">Loading payment details…</p></section>
  if (paymentQuery.isError || eventsQuery.isError) {
    const error = paymentQuery.error ?? eventsQuery.error
    const unavailable = error instanceof ApiError && error.status === 404
    return <section className="business-page">
      <div className="page-heading business-heading"><div><h1 tabIndex={-1}>Payment details</h1><p>Review the current payment and its recorded history.</p></div></div>
      <div className="details-error" role="alert"><h2>{unavailable ? 'Payment not found' : 'Payment details could not be loaded'}</h2><p>{unavailable ? 'This payment is not available in the current workspace.' : error instanceof ApiError && error.status === 401 ? 'Your session ended. Sign in again to continue.' : error instanceof Error ? error.message : 'Check your connection and try again.'}</p><div><Button variant="secondary" onClick={() => { void paymentQuery.refetch(); void eventsQuery.refetch() }}>Try again</Button><Link className="text-link" to="/payments">Back to payments</Link></div></div>
    </section>
  }

  const payment = paymentQuery.data
  const expectedOutcome = payment.simulationScenario === 'APPROVE' ? 'Approved' : 'Rejected (simulated decline)'
  return <section className="business-page payment-details-page">
    <div className="details-back"><Link to="/payments"><ArrowLeft aria-hidden="true" />Back to payments</Link></div>
    <div className="page-heading business-heading payment-details-heading">
      <div><p className="eyebrow">Payment details</p><h1 tabIndex={-1}>{payment.description || 'Simulated payment'}</h1><p>Created {formatTimestamp(payment.createdAt)}</p></div>
      <StatusBadge status={payment.status} />
    </div>
    <section className="payment-overview-panel" aria-label="Payment summary">
      <div className="payment-amount-block"><span>Amount</span><strong>{formatBrl(payment.amount)}</strong><small>{payment.currency} · simulated only</small></div>
      <dl className="payment-facts">
        <div><dt><CreditCard aria-hidden="true" />Payment ID</dt><dd className="detail-mono">{payment.id}</dd></div>
        <div><dt><UserRound aria-hidden="true" />Customer ID</dt><dd className="detail-mono">{payment.customerId}</dd></div>
        <div><dt><Fingerprint aria-hidden="true" />Configured scenario</dt><dd>{payment.simulationScenario === 'APPROVE' ? 'Approve' : 'Decline'}</dd></div>
        <div><dt><CalendarClock aria-hidden="true" />Last updated</dt><dd>{formatTimestamp(payment.updatedAt)}</dd></div>
      </dl>
      <p className="scenario-explanation">If processed, this configured simulation will deterministically result in <strong>{expectedOutcome}</strong>. This describes the scenario, not an unconfirmed payment status.</p>
    </section>
    <PaymentActions scope={scope} payment={payment} />
    <PaymentTimeline events={eventsQuery.data} />
  </section>
}

function formatTimestamp(value: string) { return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) }
