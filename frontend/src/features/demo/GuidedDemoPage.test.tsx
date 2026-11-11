import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { resetCsrf } from '../../lib/http'
import { AuthContext } from '../auth/auth-context'
import { GuidedDemoPage } from './GuidedDemoPage'

const customerId = '11111111-1111-4111-8111-111111111111'
const paymentId = '22222222-2222-4222-8222-222222222222'
const customer = { id: customerId, name: 'Cedar Studio', email: 'cedar@example.test', createdAt: '', updatedAt: '' }
const pending = { id: paymentId, customerId, amount: '49.90', currency: 'BRL', status: 'PENDING', description: 'Demo invoice', simulationScenario: 'APPROVE', createdAt: '2026-11-10T12:00:00Z', updatedAt: '2026-11-10T12:00:00Z' }
const approved = { ...pending, status: 'APPROVED', updatedAt: '2026-11-10T12:01:00Z' }
const auth = { status: 'authenticated' as const, expired: false, session: { user: { id: 'operator', email: 'ops@example.test', createdAt: '' }, company: { id: 'demo-company', name: 'Demo workspace', demo: true, expiresAt: '2026-11-11T12:00:00Z', createdAt: '' } }, scope: 'demo:operator-session', login: async () => {}, enterDemo: async () => {}, logout: async () => {}, refresh: async () => {} }

afterEach(() => {
  vi.unstubAllGlobals()
  resetCsrf()
  window.sessionStorage.clear()
})

function renderPage(sessionContext = auth) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(<QueryClientProvider client={client}><AuthContext value={sessionContext}><MemoryRouter initialEntries={['/demo']}><GuidedDemoPage /><LocationProbe /></MemoryRouter></AuthContext></QueryClientProvider>)
}

function LocationProbe() { const location = useLocation(); return <output data-testid="location-probe">{location.pathname}:{String((location.state as { freshDemoWorkspace?: boolean } | null)?.freshDemoWorkspace ?? false)}</output> }

function customerResponse() { return Response.json({ items: [customer], page: 0, size: 100, totalElements: 1, totalPages: 1 }) }

describe('guided demo', () => {
  it('replays the exact captured body and key, verifies the real replay header and shows server history', async () => {
    let body: string | undefined
    let idempotencyKey: string | null = null
    let payment = pending
    let createCount = 0
    const history = [
      { id: 'event-1', paymentId, sequence: 1, previousStatus: null, newStatus: 'PENDING', reasonCode: null, actorUserId: 'operator', createdAt: pending.createdAt },
      { id: 'event-2', paymentId, sequence: 2, previousStatus: 'PENDING', newStatus: 'APPROVED', reasonCode: null, actorUserId: 'operator', createdAt: approved.updatedAt },
    ]
    const fetch = vi.fn(async (path: string, options?: RequestInit) => {
      if (path === '/api/v1/auth/csrf') return Response.json({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' })
      if (path.includes('/customers?')) return customerResponse()
      if (path === '/api/v1/payments' && options?.method === 'POST') {
        createCount++
        const headers = new Headers(options.headers)
        const nextBody = String(options.body)
        if (createCount === 1) { body = nextBody; idempotencyKey = headers.get('Idempotency-Key'); return Response.json(pending, { status: 201, headers: { 'Idempotency-Replayed': 'false', Location: `/api/v1/payments/${paymentId}` } }) }
        expect(nextBody).toBe(body)
        expect(headers.get('Idempotency-Key')).toBe(idempotencyKey)
        return Response.json(pending, { status: 201, headers: { 'Idempotency-Replayed': 'true', Location: `/api/v1/payments/${paymentId}` } })
      }
      if (path === `/api/v1/payments/${paymentId}/process`) { payment = approved; return Response.json(approved) }
      if (path === `/api/v1/payments/${paymentId}/events`) return Response.json(history)
      if (path === `/api/v1/payments/${paymentId}`) return Response.json(payment)
      throw new Error(`Unexpected request: ${path}`)
    })
    vi.stubGlobal('fetch', fetch)
    const user = userEvent.setup()
    renderPage()

    await user.selectOptions(await screen.findByLabelText('Customer'), customerId)
    await user.type(screen.getByLabelText('Amount (BRL)'), '49.90')
    await user.type(screen.getByLabelText('Description Optional'), 'Demo invoice')
    await user.click(screen.getByRole('button', { name: 'Create simulated payment' }))
    expect(await screen.findByText(`Payment ${paymentId} was created as Pending.`)).toBeVisible()

    await user.click(screen.getByRole('button', { name: 'Replay exact request' }))
    expect(await screen.findByText('Replay confirmed by the API')).toBeVisible()
    expect(screen.getByText('Idempotency-Replayed:', { exact: false }).parentElement).toHaveTextContent('true')
    expect(createCount).toBe(2)

    await user.click(screen.getByRole('button', { name: 'Process payment' }))
    expect(await screen.findByText('Payment history')).toBeVisible()
    expect(screen.getByText('Changed from Pending to Approved.')).toBeVisible()
    expect(screen.getByText(/replay above returns the original 201 response with Pending status/i)).toBeVisible()
    expect(screen.getAllByText('Approved').length).toBeGreaterThan(0)
    await waitFor(() => expect(window.sessionStorage.getItem('flowpay.guided-demo.active')).toBe('active'))
  })

  it('requires an explicit restart after reload when only the active marker survives', async () => {
    window.sessionStorage.setItem('flowpay.guided-demo.active', 'active')
    vi.stubGlobal('fetch', vi.fn(async (path: string) => {
      if (path.includes('/customers?')) return customerResponse()
      if (path === '/api/v1/auth/csrf') return Response.json({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' })
      throw new Error(`Unexpected request: ${path}`)
    }))
    const user = userEvent.setup()
    renderPage()

    expect(screen.getByRole('heading', { name: 'Restart the guided demo' })).toBeVisible()
    expect(screen.getByText(/no replay will be claimed from the previous run/i)).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Replay exact request' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Restart guided demo' }))
    expect(await screen.findByRole('heading', { name: 'Create a pending payment' })).toBeVisible()
    expect(window.sessionStorage.getItem('flowpay.guided-demo.active')).toBeNull()
  })

  it('does not retry a quota-rejected create and hands off to the explicit fresh-session login flow', async () => {
    vi.stubGlobal('fetch', vi.fn(async (path: string) => {
      if (path.includes('/customers?')) return customerResponse()
      if (path === '/api/v1/auth/csrf') return Response.json({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' })
      if (path === '/api/v1/payments') return Response.json({ status: 409, code: 'DEMO_LIMIT_REACHED' }, { status: 409 })
      throw new Error(`Unexpected request: ${path}`)
    }))
    const user = userEvent.setup()
    renderPage()
    await user.selectOptions(await screen.findByLabelText('Customer'), customerId)
    await user.type(screen.getByLabelText('Amount (BRL)'), '49.90')
    await user.click(screen.getByRole('button', { name: 'Create simulated payment' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/fresh isolated workspace has its own quota/i)
    expect(screen.queryByRole('button', { name: 'Retry same request' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Open a fresh demo workspace' }))
    expect(screen.getByTestId('location-probe')).toHaveTextContent('/login:true')
    expect(window.sessionStorage.getItem('flowpay.guided-demo.active')).toBeNull()
  })

  it.each([
    [409, 'DEMO_LIMIT_REACHED', 'This demo workspace reached its payment limit. A fresh isolated workspace has its own quota; choose below to enter one. No workspace is started automatically.'],
    [409, 'IDEMPOTENCY_KEY_REUSED', 'The key is already associated with a different request. The guide will not generate a replacement key silently; restart it to begin a new attempt.'],
    [429, 'RATE_LIMITED', 'The request rate limit was reached. Wait before entering a new demo session or retrying.'],
    [503, 'DEMO_CAPACITY_REACHED', 'All demo workspaces are temporarily occupied. Please try again shortly.'],
    [401, 'DEMO_EXPIRED', 'Your demo session ended. Sign in again; the saved request is cleared when authentication changes.'],
  ])('explains API failure %s %s without claiming success', async (status, code, message) => {
    vi.stubGlobal('fetch', vi.fn(async (path: string) => {
      if (path.includes('/customers?')) return customerResponse()
      if (path === '/api/v1/auth/csrf') return Response.json({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' })
      if (path === '/api/v1/payments') return Response.json({ status, code, detail: message }, { status })
      throw new Error(`Unexpected request: ${path}`)
    }))
    const user = userEvent.setup()
    renderPage()
    await user.selectOptions(await screen.findByLabelText('Customer'), customerId)
    await user.type(screen.getByLabelText('Amount (BRL)'), '49.90')
    await user.click(screen.getByRole('button', { name: 'Create simulated payment' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(screen.queryByText(/was created as Pending/)).not.toBeInTheDocument()
  })
})
