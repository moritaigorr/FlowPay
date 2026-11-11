import { apiRequest } from '../../lib/http'

export interface Customer {
  id: string
  name: string
  email: string
  createdAt: string
  updatedAt: string
}

export interface CustomerPage {
  items: Customer[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface CustomerFilters { page: number; size: number; q: string }

export async function listCustomers(filters: CustomerFilters, signal?: AbortSignal): Promise<CustomerPage> {
  const params = new URLSearchParams({ page: String(filters.page), size: String(filters.size) })
  if (filters.q) params.set('q', filters.q)
  return apiRequest<CustomerPage>(`/customers?${params}`, { signal })
}

export function createCustomer(payload: { name: string; email: string }, signal?: AbortSignal): Promise<Customer> {
  return apiRequest<Customer>('/customers', { method: 'POST', body: JSON.stringify(payload), signal })
}
