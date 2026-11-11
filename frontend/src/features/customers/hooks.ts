import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createCustomer, listCustomers } from './api'
import type { Customer, CustomerFilters } from './api'
import { sessionKey } from '../../lib/query-client'

export function useCustomers(scope: string, filters: CustomerFilters) {
  return useQuery({
    queryKey: sessionKey(scope, 'customers', filters),
    queryFn: ({ signal }) => listCustomers(filters, signal),
  })
}

export function useCreateCustomer(scope: string) {
  const client = useQueryClient()
  return useMutation<Customer, Error, { name: string; email: string }>({
    mutationKey: sessionKey(scope, 'customer-create'),
    mutationFn: (payload) => createCustomer(payload),
    onSuccess: async () => {
      await Promise.all([
        client.invalidateQueries({ queryKey: sessionKey(scope, 'customers') }),
        client.invalidateQueries({ queryKey: sessionKey(scope, 'customer-options') }),
      ])
    },
  })
}
