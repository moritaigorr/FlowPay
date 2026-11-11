import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { resetCsrf } from '../../lib/http'
import { CreatePaymentForm } from './CreatePaymentForm'

afterEach(() => { vi.restoreAllMocks(); resetCsrf() })

const customerId = '11111111-1111-4111-8111-111111111111'

function renderForm() {
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false }, queries: { retry: false } } })
  return render(<QueryClientProvider client={client}><CreatePaymentForm scope="company:user" /></QueryClientProvider>)
}

function customerPage(items = [{ id: customerId, name: 'Acme', email: 'ops@example.com', createdAt: '', updatedAt: '' }]) {
  return Response.json({ items, page: 0, size: 20, totalElements: items.length, totalPages: items.length ? 1 : 0 })
}

describe('payment creation form', () => {
  it('keeps maximum money exact and reuses the same key and payload after an uncertain result', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(customerPage())
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' }), { status: 200 }))
      .mockRejectedValueOnce(new TypeError('connection reset after commit'))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'payment-1', customerId: 'customer-1', amount: '99999999999999999.99' }), { status: 201 }))
    const user = userEvent.setup()
    renderForm()
    await screen.findByRole('option', { name: /Acme · ops@example.com/ })
    await user.selectOptions(screen.getByLabelText('Customer'), customerId)
    await user.type(screen.getByLabelText('Amount (BRL)'), '99999999999999999.99')
    await user.click(screen.getByRole('button', { name: 'Create payment' }))
    expect(await screen.findByText(/could not confirm the result/i)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Retry same attempt' }))
    const paymentCalls = fetch.mock.calls.filter(([path]) => String(path).includes('/payments'))
    const firstHeaders = new Headers(paymentCalls[0][1]?.headers)
    const retryHeaders = new Headers(paymentCalls[1][1]?.headers)
    expect(retryHeaders.get('Idempotency-Key')).toBe(firstHeaders.get('Idempotency-Key'))
    expect(JSON.parse(String(paymentCalls[1][1]?.body)).amount).toBe('99999999999999999.99')
  })

  it('shows local amount validation and sends no payment request for invalid input', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(customerPage())
    const user = userEvent.setup()
    renderForm()
    await screen.findByRole('option', { name: /Acme · ops@example.com/ })
    await user.selectOptions(screen.getByLabelText('Customer'), customerId)
    await user.type(screen.getByLabelText('Amount (BRL)'), '1.001')
    await user.click(screen.getByRole('button', { name: 'Create payment' }))
    expect(await screen.findByText(/up to two decimal places/i)).toBeInTheDocument()
    expect(fetch.mock.calls.filter(([path]) => String(path).includes('/payments'))).toHaveLength(0)
  })

  it('finds customers after page zero by server search and retains the selected customer when search changes', async () => {
    const laterCustomerId = '99999999-9999-4999-8999-999999999999'
    const fetch = vi.spyOn(globalThis, 'fetch').mockImplementation(async (path) => {
      const url = String(path)
      if (!url.includes('/customers?')) throw new Error(`Unexpected request: ${url}`)
      const query = new URLSearchParams(url.split('?')[1]).get('q') ?? ''
      const items = query.toLowerCase().includes('customer 102')
        ? [{ id: laterCustomerId, name: 'Customer 102', email: '102@example.com', createdAt: '', updatedAt: '' }]
        : query ? [{ id: customerId, name: 'Another customer', email: 'another@example.com', createdAt: '', updatedAt: '' }] : []
      return Response.json({ items, page: 0, size: 20, totalElements: query ? 1 : 102, totalPages: query ? 1 : 6 })
    })
    const user = userEvent.setup()
    renderForm()
    await user.type(screen.getByLabelText('Find a customer'), 'Customer 102')
    await user.selectOptions(await screen.findByLabelText('Customer'), laterCustomerId)
    await user.clear(screen.getByLabelText('Find a customer'))
    await user.type(screen.getByLabelText('Find a customer'), 'Another')

    expect(await screen.findByRole('option', { name: /Customer 102 · 102@example.com/ })).toBeInTheDocument()
    expect(screen.getByLabelText('Customer')).toHaveValue(laterCustomerId)
    expect(fetch.mock.calls.some(([path]) => String(path).includes('q=Customer+102'))).toBe(true)
  })
})
