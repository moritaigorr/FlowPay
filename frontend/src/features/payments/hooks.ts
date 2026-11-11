import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../lib/http'
import { sessionKey } from '../../lib/query-client'
import { cancelPayment, createPayment, getPayment, getPaymentEvents, listPayments, processPayment } from './api'
import type { PaymentFilters } from './api'

export function usePayments(scope: string, filters: PaymentFilters, enabled = true) {
  return useQuery({
    queryKey: sessionKey(scope, 'payments', filters),
    queryFn: ({ signal }) => listPayments(filters, signal),
    enabled,
  })
}

export function usePayment(scope: string, paymentId: string) {
  return useQuery({
    queryKey: sessionKey(scope, 'payment', paymentId),
    queryFn: ({ signal }) => getPayment(paymentId, signal),
    enabled: !!paymentId,
  })
}

export function usePaymentEvents(scope: string, paymentId: string) {
  return useQuery({
    queryKey: sessionKey(scope, 'payment-events', paymentId),
    queryFn: ({ signal }) => getPaymentEvents(paymentId, signal),
    enabled: !!paymentId,
  })
}

function usePaymentAction(scope: string, paymentId: string, action: typeof processPayment) {
  const client = useQueryClient()
  return useMutation({
    mutationKey: sessionKey(scope, 'payment-action', paymentId),
    mutationFn: () => action(paymentId),
    onSuccess: async () => {
      await invalidatePaymentViews(client, scope, paymentId)
    },
    onError: async (error) => {
      if (error instanceof ApiError && error.status === 409) {
        await invalidatePaymentViews(client, scope, paymentId)
      }
    },
  })
}

export function useProcessPayment(scope: string, paymentId: string) {
  return usePaymentAction(scope, paymentId, processPayment)
}

export function useCancelPayment(scope: string, paymentId: string) {
  return usePaymentAction(scope, paymentId, cancelPayment)
}

export function useCreatePayment(scope: string) {
  const client = useQueryClient()
  return useMutation({
    mutationKey: sessionKey(scope, 'payment-create'),
    mutationFn: ({ payload, key }: { payload: Parameters<typeof createPayment>[0]; key: string }) => createPayment(payload, key),
    onSuccess: () => invalidatePaymentViews(client, scope),
  })
}

// Reuse after every payment state mutation so the list, summary, and recent activity stay in sync.
export async function invalidatePaymentViews(client: ReturnType<typeof useQueryClient>, scope: string, paymentId?: string) {
  const queries = [
    client.invalidateQueries({ queryKey: sessionKey(scope, 'payments') }),
    client.invalidateQueries({ queryKey: sessionKey(scope, 'dashboard-summary') }),
    client.invalidateQueries({ queryKey: sessionKey(scope, 'dashboard-recent-payments') }),
  ]
  if (paymentId) {
    queries.push(
      client.invalidateQueries({ queryKey: sessionKey(scope, 'payment', paymentId) }),
      client.invalidateQueries({ queryKey: sessionKey(scope, 'payment-events', paymentId) }),
    )
  }
  await Promise.all(queries)
}
