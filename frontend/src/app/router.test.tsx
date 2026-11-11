import { render as renderUI, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppRoutes } from './router'
import { AuthContext } from '../features/auth/auth-context'

function render(children: ReactNode) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return renderUI(<QueryClientProvider client={queryClient}><AuthContext value={{ status: 'authenticated', expired: false,
    session: { user: { id: 'operator', email: 'operator@example.test', createdAt: '' }, company: { id: 'company', name: 'Test workspace', demo: false, expiresAt: null, createdAt: '' } },
    scope: 'test-session', login: async () => {}, enterDemo: async () => {}, logout: async () => {}, refresh: async () => {},
  }}>{children}</AuthContext></QueryClientProvider>)
}

beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({
    items: [], page: 0, size: 20, totalElements: 0, totalPages: 0,
  }), { status: 200, headers: { 'Content-Type': 'application/json' } })))
})

afterEach(() => vi.unstubAllGlobals())

describe('workspace navigation', () => {
  it.each(['/payments/', '/Payments', '/PAYMENTS/'])(
    'keeps the page, title, breadcrumb and selected navigation consistent at %s',
    async (path) => {
      render(<MemoryRouter initialEntries={[path]}><AppRoutes /></MemoryRouter>)
      expect(await screen.findByRole('heading', { level: 1, name: 'Payments' })).toBeVisible()
      expect(document.title).toBe('FlowPay · Payments')
      expect(screen.getByLabelText('Breadcrumb')).toHaveTextContent('Workspace/Payments')
      expect(screen.getByRole('link', { name: 'Payments' })).toHaveAttribute('aria-current', 'page')
    },
  )

  it('does not select a known route for an unknown nested destination', () => {
    render(<MemoryRouter initialEntries={['/payments/missing']}><AppRoutes /></MemoryRouter>)
    expect(screen.getByRole('heading', { level: 1, name: 'Page not found' })).toBeVisible()
    expect(document.title).toBe('FlowPay · Page not found')
    expect(screen.getByLabelText('Breadcrumb')).toHaveTextContent('Workspace/Page not found')
    expect(screen.getByRole('link', { name: 'Payments' })).not.toHaveAttribute('aria-current')
  })

  it('navigates with the keyboard and moves focus to the new page heading', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/payments']}><AppRoutes /></MemoryRouter>)
    const customers = screen.getByRole('link', { name: 'Customers' })
    customers.focus()
    await user.keyboard('{Enter}')
    const heading = await screen.findByRole('heading', { level: 1, name: 'Customers' })
    expect(heading).toHaveFocus()
    expect(customers).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('button', { name: 'Add customer' })).toBeVisible()
  })

  it('lets keyboard users skip navigation and reach main content', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/payments']}><AppRoutes /></MemoryRouter>)
    await user.tab()
    expect(screen.getByRole('link', { name: 'Skip to content' })).toHaveFocus()
    await user.keyboard('{Enter}')
    expect(screen.getByRole('main')).toHaveFocus()
  })

  it('offers a working recovery link for an unknown route without pretending data exists', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/unknown']}><AppRoutes /></MemoryRouter>)
    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeVisible()
    await user.click(screen.getByRole('link', { name: 'Back to payments' }))
    expect(await screen.findByRole('heading', { level: 1, name: 'Payments' })).toBeVisible()
    expect(await screen.findByRole('button', { name: 'Create payment' })).toBeVisible()
  })

  it('opens the guided demo from Overview with its own breadcrumb and title', async () => {
    const fetch = vi.fn(async (path: string) => {
      if (path.includes('/dashboard/summary?')) return Response.json({
        from: '2026-10-01T00:00:00Z', to: '2026-11-01T00:00:00Z', currency: 'BRL',
        approvedVolume: '0.00', pendingCount: 0, pendingAmount: '0.00', approvedCount: 0,
        rejectedCount: 0, totalPayments: 0, approvalRate: null,
      })
      return Response.json({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
    })
    vi.stubGlobal('fetch', fetch)
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/overview']}><AppRoutes /></MemoryRouter>)

    expect(await screen.findByRole('heading', { level: 1, name: 'Overview' })).toBeVisible()
    await user.click(screen.getByRole('link', { name: 'Run guided demo' }))
    expect(await screen.findByRole('heading', { level: 1, name: 'Enter an isolated demo workspace' })).toBeVisible()
    expect(document.title).toBe('FlowPay · Guided demo')
    expect(screen.getByLabelText('Breadcrumb')).toHaveTextContent('Workspace/Guided demo')
  })
})
