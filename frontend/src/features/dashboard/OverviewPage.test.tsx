import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext } from '../auth/auth-context'
import { OverviewPage } from './OverviewPage'
import type { DashboardSummary } from './api'

const emptyPage = { items: [], page: 0, size: 5, totalElements: 0, totalPages: 0 }
const zeroSummary: DashboardSummary = {
  from: '2026-10-11T12:34:56Z', to: '2026-11-10T12:34:56Z', currency: 'BRL',
  approvedVolume: '0.00', pendingCount: 0, pendingAmount: '0.00', approvedCount: 0,
  rejectedCount: 0, totalPayments: 0, approvalRate: null,
}

function mount() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  render(<MemoryRouter><QueryClientProvider client={client}>
    <AuthContext value={{ status: 'authenticated', expired: false,
      session: { user: { id: 'operator', email: 'operator@example.test', createdAt: '' }, company: { id: 'company', name: 'Portfolio workspace', demo: false, expiresAt: null, createdAt: '' } },
      scope: 'test-session', login: async () => {}, enterDemo: async () => {}, logout: async () => {}, refresh: async () => {},
    }}><OverviewPage /></AuthContext>
  </QueryClientProvider></MemoryRouter>)
}

function successFetch(summary: DashboardSummary = zeroSummary) {
  return vi.fn(async (input: RequestInfo | URL) => {
    const path = String(input)
    return path.includes('/dashboard/summary')
      ? Response.json(summary)
      : Response.json(emptyPage)
  })
}

beforeEach(() => vi.useFakeTimers({ shouldAdvanceTime: true }))
afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals() })

describe('overview dashboard', () => {
  it('announces loading while the real summary request is pending', async () => {
    vi.setSystemTime(new Date('2026-11-10T12:34:56.000Z'))
    let resolveSummary!: (response: Response) => void
    const fetch = vi.fn((input: RequestInfo | URL) => String(input).includes('/dashboard/summary')
      ? new Promise<Response>((resolve) => { resolveSummary = resolve })
      : Promise.resolve(Response.json(emptyPage)))
    vi.stubGlobal('fetch', fetch)
    mount()

    expect(await screen.findByRole('status')).toHaveTextContent(/loading dashboard metrics/i)
    await act(async () => resolveSummary(Response.json(zeroSummary)))
    expect(await screen.findByText('No data')).toBeVisible()
  })

  it('shows a recoverable summary failure without inventing metrics', async () => {
    vi.setSystemTime(new Date('2026-11-10T12:34:56.000Z'))
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => String(input).includes('/dashboard/summary')
      ? Response.json({ code: 'INTERNAL_ERROR', detail: 'Summary unavailable.' }, { status: 503 })
      : Response.json(emptyPage)))
    mount()

    expect(await screen.findByRole('alert')).toHaveTextContent(/summary unavailable/i)
    expect(screen.queryByText(/R\$/)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Retry metrics' })).toBeEnabled()
  })

  it('distinguishes no approval denominator from a measured zero and renders recent API data', async () => {
    vi.setSystemTime(new Date('2026-11-10T12:34:56.000Z'))
    const fetch = vi.fn(async (input: RequestInfo | URL) => {
      if (String(input).includes('/dashboard/summary')) {
        return Response.json({ ...zeroSummary, totalPayments: 1, pendingCount: 1, pendingAmount: '30.00' })
      }
      return Response.json({ ...emptyPage, items: [{
        id: 'payment-1', customerId: 'customer-1', amount: '30.00', currency: 'BRL', status: 'PENDING',
        description: 'Order 42', simulationScenario: 'APPROVE', createdAt: '2026-11-10T12:20:00Z', updatedAt: '2026-11-10T12:20:00Z',
      }], totalElements: 1, totalPages: 1 })
    })
    vi.stubGlobal('fetch', fetch)
    mount()

    expect(await screen.findByText('No data')).toBeVisible()
    expect(screen.getByRole('cell', { name: /30,00/ })).toBeVisible()
    expect(screen.getByText('Order 42')).toBeVisible()
    expect(screen.queryByText(/revenue|balance|change from last period/i)).not.toBeInTheDocument()
  })

  it('formats a measured zero as 0% with its approval counts', async () => {
    vi.setSystemTime(new Date('2026-11-10T12:34:56.000Z'))
    vi.stubGlobal('fetch', successFetch({ ...zeroSummary, totalPayments: 2, rejectedCount: 2, approvalRate: '0.0000' }))
    mount()

    expect(await screen.findByText('0.0%')).toBeVisible()
    expect(screen.getByText('0 approved · 2 declined')).toBeVisible()
  })

  it('sends the selected rolling period as explicit UTC instants', async () => {
    vi.setSystemTime(new Date('2026-11-10T12:34:56.000Z'))
    const fetch = successFetch()
    vi.stubGlobal('fetch', fetch)
    mount()
    await screen.findByText('No data')

    const summaryUrls = () => fetch.mock.calls.map(([input]) => new URL(String(input), 'http://flowpay.test'))
      .filter((url) => url.pathname.endsWith('/dashboard/summary'))
    const initial = summaryUrls()[0]
    expect(initial.searchParams.get('from')).toBe('2026-10-11T12:34:56.000Z')
    expect(initial.searchParams.get('to')).toBe('2026-11-10T12:34:56.000Z')

    const periodSelect = screen.getByLabelText('Reporting period')
    await act(async () => {
      fireEvent.change(periodSelect, { target: { value: '7' } })
      await vi.advanceTimersByTimeAsync(0)
    })
    expect(periodSelect).toHaveValue('7')
    const selected = summaryUrls().find((url) => {
      const from = url.searchParams.get('from')
      const to = url.searchParams.get('to')
      return from !== null && to !== null && Date.parse(to) - Date.parse(from) === 7 * 24 * 60 * 60 * 1000
    })
    expect(selected).toBeDefined()
    for (const name of ['from', 'to'] as const) {
      const instant = selected?.searchParams.get(name)
      expect(instant).not.toBeNull()
      expect(new Date(instant!).toISOString()).toBe(instant)
    }
  })
})
