import { useCancelPayment, useProcessPayment } from './hooks'
import type { Payment } from './api'
import { ApiError } from '../../lib/http'
import { Button } from '../../components/ui/button'

export function PaymentActions({ scope, payment }: { scope: string; payment: Payment }) {
  const process = useProcessPayment(scope, payment.id)
  const cancel = useCancelPayment(scope, payment.id)
  const busy = process.isPending || cancel.isPending
  const error = process.error ?? cancel.error

  return <section className="payment-actions" aria-labelledby="payment-actions-title">
    <div>
      <h2 id="payment-actions-title">Available actions</h2>
      <p>{payment.status === 'PENDING' ? 'Choose one deterministic action for this pending payment.' : 'Final payment states cannot be changed.'}</p>
    </div>
    {payment.status === 'PENDING'
      ? <div className="payment-actions-buttons">
        <Button disabled={busy} aria-busy={process.isPending} onClick={() => process.mutate()}>{process.isPending ? 'Processing…' : 'Process payment'}</Button>
        <Button variant="destructive" disabled={busy} aria-busy={cancel.isPending} onClick={() => cancel.mutate()}>{cancel.isPending ? 'Cancelling…' : 'Cancel payment'}</Button>
      </div>
      : <div className="payment-actions-buttons">
        <Button disabled aria-label="Process payment unavailable for final payment">Process payment</Button>
        <Button variant="destructive" disabled aria-label="Cancel payment unavailable for final payment">Cancel payment</Button>
      </div>}
    {error && <p className="form-error payment-action-error" role="alert">{actionErrorMessage(error)}{error instanceof ApiError && error.requestId && <small>Reference: {error.requestId}</small>}</p>}
    {process.isSuccess && <p className="form-success" role="status">The server confirmed the payment transition.</p>}
    {cancel.isSuccess && <p className="form-success" role="status">The server confirmed cancellation.</p>}
  </section>
}

function actionErrorMessage(error: Error) {
  if (error instanceof ApiError && error.status === 409) return 'This payment changed before the action completed. Its current state and history have been refreshed.'
  if (error instanceof ApiError && error.status === 404) return 'This payment is no longer available in this workspace.'
  if (error instanceof ApiError && error.status === 401) return 'Your session ended. Sign in again before changing this payment.'
  return error.message || 'The action could not be completed. Check the connection and try again.'
}
