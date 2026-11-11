import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthContext } from '../auth/auth-context'
import { PaymentDetailsPage } from './PaymentDetailsPage'

const paymentId = '22222222-2222-4222-8222-222222222222'
const payment = { id: paymentId, customerId: '11111111-1111-4111-8111-111111111111', amount: '73.19', currency: 'BRL', status: 'PENDING', description: 'Monthly service', simulationScenario: 'DECLINE', createdAt: '2026-11-10T12:00:00Z', updatedAt: '2026-11-10T12:00:00Z' }
const events = [
  { id: 'event-2', paymentId, sequence: 2, previousStatus: 'PENDING', newStatus: 'REJECTED', reasonCode: 'SIMULATED_DECLINE', actorUserId: 'operator', createdAt: '2026-11-10T12:01:00Z' },
  { id: 'event-1', paymentId, sequence: 1, previousStatus: null, newStatus: 'PENDING', reasonCode: null, actorUserId: 'operator', createdAt: '2026-11-10T12:00:00Z' },
]
const auth = { status: 'authenticated' as const, expired: false, session: { user: { id: 'operator', email: 'ops@example.com', createdAt: '' }, company: { id: 'company', name: 'Workspace', demo: false, expiresAt: null, createdAt: '' } }, scope: 'company:operator-session', login: async () => {}, enterDemo: async () => {}, logout: async () => {}, refresh: async () => {} }

afterEach(() => vi.unstubAllGlobals())

function renderDetails() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}><AuthContext value={auth}><MemoryRouter initialEntries={[`/payments/${paymentId}`]}><Routes><Route path="/payments/:paymentId" element={<PaymentDetailsPage />} /></Routes></MemoryRouter></AuthContext></QueryClientProvider>)
  return client
}

describe('payment details', () => {
  it('loads persisted details and displays events in sequence order with the deterministic outcome', async () => {
    const fetch = vi.fn(async (path: string) => {
      if (path.includes('/payments/') && path.endsWith('/events')) return Response.json(events)
      if (path.endsWith(`/payments/${paymentId}`)) return Response.json(payment)
      throw new Error(`Unexpected request: ${path}`)
    })
    vi.stubGlobal('fetch', fetch)

    renderDetails()

    expect(await screen.findByRole('heading', { name: 'Monthly service' })).toBeVisible()
    expect(screen.getByText(/R\$.*73,19/)).toBeVisible()
    expect(screen.getByText((_, element) => !!element?.classList.contains('scenario-explanation'))).toHaveTextContent(/deterministically result in Rejected/)
    const timeline = await screen.findByRole('list', { name: 'Payment history' })
    expect(timeline.children[0]).toHaveTextContent('Created')
    expect(timeline.children[1]).toHaveTextContent('Rejected')
  })

  it('does not show a final state until the server confirms processing', async () => {
    let current = { ...payment }
    let releaseProcess!: () => void
    const processGate = new Promise<void>((resolve) => { releaseProcess = resolve })
    const fetch = vi.fn(async (path: string, options?: RequestInit) => {
      if (path.endsWith('/auth/csrf')) return Response.json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' })
      if (path.includes('/payments/') && path.endsWith('/events')) return Response.json(current.status === 'PENDING' ? [events[1]] : [events[1], { ...events[0], newStatus: 'APPROVED', reasonCode: null }])
      if (path.endsWith(`/payments/${paymentId}/process`)) {
        expect(options?.headers).toBeDefined()
        await processGate
        current = { ...current, status: 'APPROVED' }
        return Response.json(current)
      }
      if (path.endsWith(`/payments/${paymentId}`)) return Response.json(current)
      throw new Error(`Unexpected request: ${path}`)
    })
    vi.stubGlobal('fetch', fetch)
    const user = userEvent.setup()
    renderDetails()

    const process = await screen.findByRole('button', { name: 'Process payment' })
    await user.click(process)
    expect(screen.getAllByText('Pending').length).toBeGreaterThan(0)
    expect(process).toBeDisabled()
    releaseProcess()
    await waitFor(() => expect(screen.getAllByText('Approved').length).toBeGreaterThan(0))
    await waitFor(() => expect(screen.getByRole('list', { name: 'Payment history' }).children).toHaveLength(2))
  })

  it('refetches current details and history after a state conflict', async () => {
    let paymentReads = 0
    let eventsReads = 0
    let current = { ...payment }
    const fetch = vi.fn(async (path: string) => {
      if (path.endsWith('/auth/csrf')) return Response.json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' })
      if (path.endsWith(`/payments/${paymentId}/process`)) { current = { ...current, status: 'APPROVED' }; return Response.json({ status: 409, code: 'PAYMENT_FINAL_STATE', detail: 'Payment is already final.' }, { status: 409 }) }
      if (path.includes('/payments/') && path.endsWith('/events')) { eventsReads++; return Response.json(eventsReads > 1 ? [events[1], events[0]] : [events[1]]) }
      if (path.endsWith(`/payments/${paymentId}`)) { paymentReads++; return Response.json(current) }
      throw new Error(`Unexpected request: ${path}`)
    })
    vi.stubGlobal('fetch', fetch)
    const user = userEvent.setup()
    renderDetails()

    await user.click(await screen.findByRole('button', { name: 'Process payment' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/changed before/i)
    await waitFor(() => expect(screen.getByText('Approved')).toBeVisible())
    expect(paymentReads).toBeGreaterThan(1)
  })

  it('treats a foreign or missing payment as unavailable and offers a retry', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => Response.json({ status: 404, code: 'NOT_FOUND', detail: 'Not found.' }, { status: 404 })))
    renderDetails()
    expect(await screen.findByRole('heading', { name: 'Payment not found' })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Back to payments' })).toHaveAttribute('href', '/payments')
  })

  it('cancels a pending payment and disables actions after the server confirms the final state', async () => {
    let current = { ...payment }
    const fetch = vi.fn(async (path: string) => {
      if (path.endsWith('/auth/csrf')) return Response.json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' })
      if (path.endsWith(`/payments/${paymentId}/cancel`)) { current = { ...current, status: 'CANCELLED' }; return Response.json(current) }
      if (path.includes('/payments/') && path.endsWith('/events')) return Response.json(current.status === 'PENDING' ? [events[1]] : [events[1], { ...events[0], newStatus: 'CANCELLED' }])
      if (path.endsWith(`/payments/${paymentId}`)) return Response.json(current)
      throw new Error(`Unexpected request: ${path}`)
    })
    vi.stubGlobal('fetch', fetch)
    const user = userEvent.setup()
    renderDetails()

    await user.click(await screen.findByRole('button', { name: 'Cancel payment' }))
    await waitFor(() => expect(screen.getAllByText('Cancelled').length).toBeGreaterThan(0))
    expect(screen.getByRole('button', { name: 'Process payment unavailable for final payment' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Cancel payment unavailable for final payment' })).toBeDisabled()
  })
})
