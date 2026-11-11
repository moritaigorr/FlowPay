import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthContext } from '../auth/auth-context'
import { PaymentsPage } from './PaymentsPage'

const company = { user: { id: 'operator', email: 'ops@example.com', createdAt: '' }, company: { id: 'company', name: 'Workspace', demo: false, expiresAt: null, createdAt: '' } }
const customerId = '11111111-1111-4111-8111-111111111111'
const paymentId = '22222222-2222-4222-8222-222222222222'

afterEach(() => vi.unstubAllGlobals())

function renderPayments(initialEntry = '/payments') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}><AuthContext value={{ status: 'authenticated', expired: false, session: company, scope: 'company:operator-session', login: async () => {}, enterDemo: async () => {}, logout: async () => {}, refresh: async () => {} }}><MemoryRouter initialEntries={[initialEntry]}><PaymentsPage /></MemoryRouter></AuthContext></QueryClientProvider>)
}

describe('payment list customer names', () => {
  it('labels the column Customer and shows a known customer name without per-row requests', async () => {
    const fetch = vi.fn().mockImplementation(async (path: string) => {
      if (path.includes('/customers?')) return Response.json({ items: [{ id: customerId, name: 'Acme Studio', email: 'ops@example.com', createdAt: '', updatedAt: '' }], page: 0, size: 100, totalElements: 1, totalPages: 1 })
      if (path.includes('/payments?')) return Response.json({ items: [{ id: paymentId, customerId, amount: '12.34', currency: 'BRL', status: 'PENDING', description: null, simulationScenario: 'APPROVE', createdAt: '2026-11-10T00:00:00Z', updatedAt: '2026-11-10T00:00:00Z' }], page: 0, size: 20, totalElements: 1, totalPages: 1 })
      throw new Error(`Unexpected request: ${path}`)
    })
    vi.stubGlobal('fetch', fetch)
    renderPayments()

    expect(await screen.findByRole('columnheader', { name: 'Customer' })).toBeVisible()
    expect(await screen.findByText(`Customer ${customerId.slice(0, 8)}`)).toBeVisible()
    expect(fetch.mock.calls.filter(([path]) => String(path).includes('/customers?')).map(([path]) => String(path))).toEqual(['/api/v1/customers?page=0&size=20'])
    expect(fetch.mock.calls.filter(([path]) => String(path).includes('/customers/'))).toHaveLength(0)
  })

  it('pages through and searches server-side customer filters while preserving the selected customer', async () => {
    const laterCustomerId = '99999999-9999-4999-8999-999999999999'
    const laterCustomer = { id: laterCustomerId, name: 'Customer 150', email: '150@example.com', createdAt: '', updatedAt: '' }
    const anotherCustomer = { id: customerId, name: 'Another customer', email: 'another@example.com', createdAt: '', updatedAt: '' }
    const fetch = vi.fn().mockImplementation(async (path: string) => {
      const url = new URL(path, 'http://flowpay.test')
      if (url.pathname.endsWith('/customers')) {
        const page = Number(url.searchParams.get('page'))
        const q = url.searchParams.get('q') ?? ''
        const size = Number(url.searchParams.get('size'))
        if (size === 100) return Response.json({ items: [anotherCustomer], page: 0, size, totalElements: 150, totalPages: 2 })
        if (q.toLowerCase() === 'another') return Response.json({ items: [anotherCustomer], page: 0, size, totalElements: 1, totalPages: 1 })
        return Response.json({ items: page === 0 ? [anotherCustomer] : [laterCustomer], page, size, totalElements: 150, totalPages: 8 })
      }
      if (url.pathname.endsWith('/payments')) return Response.json({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
      throw new Error(`Unexpected request: ${url.pathname}`)
    })
    vi.stubGlobal('fetch', fetch)
    const user = userEvent.setup()
    renderPayments()

    const customerFilter = await screen.findByLabelText('Filter by customer')
    await user.click(await screen.findByRole('button', { name: 'Next customer results' }))
    await user.selectOptions(customerFilter, laterCustomerId)
    await user.type(screen.getByLabelText('Search customers'), 'Another')
    await user.click(screen.getByRole('button', { name: 'Apply customer search' }))

    expect(await screen.findByRole('option', { name: /Customer 150/ })).toBeInTheDocument()
    expect(screen.getByLabelText('Filter by customer')).toHaveValue(laterCustomerId)
    expect(fetch.mock.calls.some(([path]) => new URL(String(path), 'http://flowpay.test').searchParams.get('page') === '1')).toBe(true)
    expect(fetch.mock.calls.some(([path]) => new URL(String(path), 'http://flowpay.test').searchParams.get('q') === 'Another')).toBe(true)
  })
})


