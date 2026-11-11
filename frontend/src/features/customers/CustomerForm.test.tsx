import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { resetCsrf } from '../../lib/http'
import { CustomerForm } from './CustomerForm'

afterEach(() => { vi.restoreAllMocks(); resetCsrf() })

function renderForm() {
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false }, queries: { retry: false } } })
  return render(<QueryClientProvider client={client}><CustomerForm scope="company:user" /></QueryClientProvider>)
}

describe('customer creation form', () => {
  it('submits normalized fields and announces persisted success', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'customer-1', name: 'Acme', email: 'ops@example.com' }), { status: 201 }))
    const user = userEvent.setup()
    renderForm()
    await user.type(screen.getByLabelText('Customer name'), '  Acme  ')
    await user.type(screen.getByLabelText('Email address'), ' OPS@EXAMPLE.COM ')
    await user.click(screen.getByRole('button', { name: 'Save customer' }))
    await screen.findByRole('status')
    const body = JSON.parse(String(fetch.mock.calls[1][1]?.body))
    expect(body).toEqual({ name: 'Acme', email: 'ops@example.com' })
  })

  it('shows backend field errors without clearing the entered values', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: 'VALIDATION_FAILED', detail: 'Review the highlighted fields.', fieldErrors: [{ field: 'email', code: 'INVALID_FORMAT', message: 'Email is already invalid.' }] }), { status: 400, headers: { 'Content-Type': 'application/problem+json' } }))
    const user = userEvent.setup()
    renderForm()
    await user.type(screen.getByLabelText('Customer name'), 'Acme')
    await user.type(screen.getByLabelText('Email address'), 'ops@example.com')
    await user.click(screen.getByRole('button', { name: 'Save customer' }))
    expect(await screen.findByText('Email is already invalid.')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByLabelText('Email address')).toHaveValue('ops@example.com'))
  })
})
