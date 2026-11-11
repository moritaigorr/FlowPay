import type { FieldValues, Path, UseFormSetError } from 'react-hook-form'
import { ApiError } from '../../lib/http'

interface ServerFieldError { field?: unknown; message?: unknown }

export function applyServerFieldErrors<T extends FieldValues>(error: unknown, setError: UseFormSetError<T>): string | undefined {
  if (!(error instanceof ApiError)) return 'We could not confirm the result. Retry this exact attempt to safely check whether it was created.'
  if (error.status === 401) return 'Your session ended. Sign in again before creating a payment. This form will not be submitted automatically.'
  if (Array.isArray(error.fieldErrors)) {
    for (const item of error.fieldErrors as ServerFieldError[]) {
      if (typeof item.field === 'string' && typeof item.message === 'string') {
        setError(item.field as Path<T>, { type: 'server', message: item.message })
      }
    }
  }
  if (error.status === 409 && error.code === 'IDEMPOTENCY_KEY_REUSED') {
    return 'This attempt key was used with different data. Edit the payment to start a new attempt.'
  }
  if (error.status >= 500 || error.status === 0) {
    return `We could not confirm the result. Retry this exact attempt to safely check whether it was created.${error.requestId ? ` Request ID: ${error.requestId}` : ''}`
  }
  return error.message || 'The request could not be completed. Review the fields and try again.'
}
