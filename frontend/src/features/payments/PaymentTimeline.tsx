import { StatusBadge } from '../../components/shared/status-badge'
import type { PaymentEvent } from './api'

export function PaymentTimeline({ events }: { events: PaymentEvent[] }) {
  const orderedEvents = [...events].sort((left, right) => left.sequence - right.sequence)
  return <section className="payment-timeline-panel" aria-labelledby="payment-history-title">
    <div className="payment-section-heading"><div><h2 id="payment-history-title">Payment history</h2><p>Immutable events recorded by the API.</p></div></div>
    {orderedEvents.length === 0
      ? <p className="list-message" role="status">No history is available for this payment.</p>
      : <ol className="payment-timeline" aria-label="Payment history">{orderedEvents.map((event) => <li key={event.id}>
        <span className="timeline-marker" aria-hidden="true" />
        <div className="timeline-entry">
          <div className="timeline-event-heading"><strong>{event.previousStatus === null ? 'Created' : 'Status changed'}</strong><StatusBadge status={event.newStatus} /></div>
          <p>{event.previousStatus === null ? 'Payment was created as pending.' : `Changed from ${labelStatus(event.previousStatus)} to ${labelStatus(event.newStatus)}.`}</p>
          {event.reasonCode === 'SIMULATED_DECLINE' && <p className="timeline-reason">Reason: simulated decline.</p>}
          <time dateTime={event.createdAt}>{formatTimestamp(event.createdAt)}</time>
        </div>
      </li>)}</ol>}
  </section>
}

function labelStatus(status: string) { return status[0] + status.slice(1).toLowerCase() }
function formatTimestamp(value: string) { return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) }
