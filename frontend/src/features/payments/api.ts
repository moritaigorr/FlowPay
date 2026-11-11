import { apiRequest, apiRequestWithMetadata } from '../../lib/http'
import type { PaymentPayload } from './payment-attempt'

export type PaymentStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'
export interface Payment {
  id: string
  customerId: string
  amount: string
  currency: 'BRL'
  status: PaymentStatus
  description: string | null
  simulationScenario: 'APPROVE' | 'DECLINE'
  createdAt: string
  updatedAt: string
}
export interface PaymentEvent {
  id: string
  paymentId: string
  sequence: number
  previousStatus: PaymentStatus | null
  newStatus: PaymentStatus
  reasonCode: 'SIMULATED_DECLINE' | null
  actorUserId: string
  createdAt: string
}
export interface PaymentPage {
  items: Payment[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
export interface PaymentFilters {
  page: number
  size: number
  q: string
  status?: PaymentStatus
  customerId?: string
  createdFrom?: string
  createdTo?: string
}

export async function listPayments(filters: PaymentFilters, signal?: AbortSignal): Promise<PaymentPage> {
  const params = new URLSearchParams({ page: String(filters.page), size: String(filters.size) })
  if (filters.q) params.set('q', filters.q)
  if (filters.status) params.set('status', filters.status)
  if (filters.customerId) params.set('customerId', filters.customerId)
  if (filters.createdFrom) params.set('createdFrom', filters.createdFrom)
  if (filters.createdTo) params.set('createdTo', filters.createdTo)
  return apiRequest<PaymentPage>(`/payments?${params}`, { signal })
}

export async function createPayment(payload: PaymentPayload, key: string, signal?: AbortSignal): Promise<Payment> {
  return (await createPaymentWithMetadata(payload, key, signal)).data
}

export async function createPaymentWithMetadata(payload: PaymentPayload, key: string, signal?: AbortSignal) {
  return apiRequestWithMetadata<Payment>('/payments', {
    method: 'POST',
    headers: { 'Idempotency-Key': key },
    body: JSON.stringify(payload),
    signal,
  })
}

export async function getPayment(id: string, signal?: AbortSignal): Promise<Payment> {
  return apiRequest<Payment>(`/payments/${encodeURIComponent(id)}`, { signal })
}

export async function getPaymentEvents(id: string, signal?: AbortSignal): Promise<PaymentEvent[]> {
  return apiRequest<PaymentEvent[]>(`/payments/${encodeURIComponent(id)}/events`, { signal })
}

export async function processPayment(id: string): Promise<Payment> {
  return apiRequest<Payment>(`/payments/${encodeURIComponent(id)}/process`, { method: 'POST' })
}

export async function cancelPayment(id: string): Promise<Payment> {
  return apiRequest<Payment>(`/payments/${encodeURIComponent(id)}/cancel`, { method: 'POST' })
}
