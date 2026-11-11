import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { sessionKey } from '../../lib/query-client'
import { useCreateCustomer } from './hooks'

afterEach(() => vi.unstubAllGlobals())

function MutationHarness({ scope }: { scope: string }) {
  const mutation = useCreateCustomer(scope)
  return <button onClick={() => mutation.mutate({ name: 'Acme', email: 'ops@example.com' })}>Create</button>
}

describe('customer query invalidation', () => {
  it('refreshes cached customer options after creating a customer', async () => {
    const scope = 'company:operator-session'
    const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
    const invalidate = vi.spyOn(client, 'invalidateQueries')
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(Response.json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' }))
      .mockResolvedValueOnce(Response.json({ id: 'customer-102', name: 'New customer', email: 'new@example.com' }, { status: 201 })))
    render(<QueryClientProvider client={client}><MutationHarness scope={scope} /></QueryClientProvider>)

    await userEvent.click(screen.getByRole('button', { name: 'Create' }))

    expect(await screen.findByRole('button', { name: 'Create' })).toBeEnabled()
    expect(invalidate).toHaveBeenCalledWith({ queryKey: sessionKey(scope, 'customer-options') })
  })
})
