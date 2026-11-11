import { act, render, screen, waitFor } from '@testing-library/react'
import { StrictMode } from 'react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AppRoutes } from '../../app/router'
import { AuthProvider } from './AuthProvider'
import { createQueryClient } from '../../lib/query-client'
import { resetCsrf } from '../../lib/http'

const session = { user: { id: 'operator', email: 'operator@example.test', createdAt: '' }, company: { id: 'company', name: 'Example studio', demo: false, expiresAt: null, createdAt: '' } }
const emptyPage = () => Response.json({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
afterEach(() => { vi.unstubAllGlobals(); resetCsrf() })
function mount(pathname = '/customers', state?: unknown) {
  render(<StrictMode><MemoryRouter initialEntries={[{ pathname, state }]}><AuthProvider client={createQueryClient()}><AppRoutes /></AuthProvider></MemoryRouter></StrictMode>)
}
async function enterCredentials() {
  const user = userEvent.setup()
  await user.type(await screen.findByLabelText('Email'), 'operator@example.test')
  await user.type(screen.getByLabelText('Password'), 'test password')
  return user
}
describe('sign-in journey', () => {
  it('disables password login while an isolated demo session is being created', async () => {
    let completeDemo!: () => void
    const fetch = vi.fn().mockImplementation(async (path) => {
      if (path.endsWith('/auth/csrf')) return Response.json({ token: 'token', headerName: 'X-CSRF-TOKEN' })
      if (path.endsWith('/auth/me')) return Response.json({ code: 'UNAUTHENTICATED' }, { status: 401 })
      if (path.endsWith('/demo/sessions')) return new Promise<Response>((resolve) => { completeDemo = () => resolve(Response.json({ ...session, company: { ...session.company, demo: true, expiresAt: '2026-11-11T00:00:00Z' } }, { status: 201 })) })
      if (String(path).includes('/customers?') || String(path).includes('/payments?')) return emptyPage()
      throw new Error(`Unexpected request: ${path}`)
    })
    vi.stubGlobal('fetch', fetch)
    mount()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Explore demo workspace' }))
    await vi.waitFor(() => expect(completeDemo).toBeDefined())
    expect(screen.getByLabelText('Email')).toBeDisabled()
    expect(screen.getByLabelText('Password')).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Preparing demo…' })).toBeDisabled()
    await act(async () => { completeDemo() })
    expect(await screen.findByRole('heading', { name: 'Customers', level: 1 })).toBeVisible()
  })
  it('submits supplementary Unicode password characters and spaces unchanged', async () => {
    let submittedPassword: string | undefined
    vi.stubGlobal('fetch', vi.fn().mockImplementation(async (path, options) => {
      if (path.endsWith('/auth/csrf')) return Response.json({ token: 'token', headerName: 'X-CSRF-TOKEN' })
      if (path.endsWith('/auth/me')) return Response.json({ code: 'UNAUTHENTICATED' }, { status: 401 })
      if (String(path).includes('/customers?') || String(path).includes('/payments?')) return emptyPage()
      submittedPassword = (JSON.parse(options.body) as { password: string }).password
      return Response.json(session)
    }))
    mount()
    const user = userEvent.setup()
    await user.type(await screen.findByLabelText('Email'), 'operator@example.test')
    const password = ` ${'😀'.repeat(65)}e\u0301 `
    await user.type(screen.getByLabelText('Password'), password)
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByRole('heading', { level: 1, name: 'Customers' })).toBeVisible()
    expect(submittedPassword).toBe(password)
  })
  it('finishes a pending sign-in when the window regains focus', async () => {
    let complete!: () => void
    let loginSignal!: AbortSignal
    const fetch = vi.fn().mockImplementation(async (path, options) => {
      if (path.endsWith('/auth/csrf')) return Response.json({ token: 'token', headerName: 'X-CSRF-TOKEN' })
      if (path.endsWith('/auth/me')) return Response.json({ code: 'UNAUTHENTICATED' }, { status: 401 })
      if (String(path).includes('/customers?') || String(path).includes('/payments?')) return emptyPage()
      loginSignal = options.signal
      return new Promise<Response>((resolve) => { complete = () => resolve(Response.json(session)) })
    })
    vi.stubGlobal('fetch', fetch)
    mount()
    const user = await enterCredentials()
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    const identityCalls = fetch.mock.calls.filter(([path]) => path.endsWith('/auth/me')).length
    await act(async () => { window.dispatchEvent(new Event('focus')) })
    const aborted = loginSignal.aborted
    await act(async () => { complete() })
    expect(aborted).toBe(false)
    expect(fetch.mock.calls.filter(([path]) => path.endsWith('/auth/me'))).toHaveLength(identityCalls)
    expect(await screen.findByRole('heading', { level: 1, name: 'Customers' })).toBeVisible()
  })
  it('guards routes, signs in, returns to the requested page and signs out', async () => {
    const fetch = vi.fn().mockImplementation(async (path) => {
      if (path.endsWith('/auth/csrf')) return Response.json({ token: 'token', headerName: 'X-CSRF-TOKEN' })
      if (path.endsWith('/auth/me')) return Response.json({ code: 'UNAUTHENTICATED' }, { status: 401 })
      if (path.endsWith('/auth/logout')) return new Response(null, { status: 204 })
      if (String(path).includes('/customers?') || String(path).includes('/payments?')) return emptyPage()
      return Response.json(session)
    })
    vi.stubGlobal('fetch', fetch)
    mount()
    const user = await enterCredentials()
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByRole('heading', { level: 1, name: 'Customers' })).toBeVisible()
    expect(screen.getByText('Example studio')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Sign out' }))
    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeVisible()
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
  })
  it('disables duplicate submissions and exposes a focused error for a failed login', async () => {
    let rejectLogin!: () => void
    const fetch = vi.fn().mockImplementation(async (path) => {
      if (path.endsWith('/auth/csrf')) return Response.json({ token: 'token', headerName: 'X-CSRF-TOKEN' })
      if (path.endsWith('/auth/me')) return Response.json({ code: 'UNAUTHENTICATED' }, { status: 401 })
      return new Promise<Response>((resolve) => { rejectLogin = () => resolve(Response.json({ code: 'INVALID_CREDENTIALS', requestId: 'trace-login' }, { status: 401 })) })
    })
    vi.stubGlobal('fetch', fetch)
    mount()
    const user = await enterCredentials()
    await user.dblClick(screen.getByRole('button', { name: 'Sign in' }))
    expect(screen.getByRole('button', { name: 'Signing in…' })).toBeDisabled()
    expect(fetch.mock.calls.filter(([path]) => path.endsWith('/auth/login'))).toHaveLength(1)
    rejectLogin()
    expect(await screen.findByRole('alert')).toHaveTextContent('The email or password is incorrect.')
    await waitFor(() => expect(screen.getByRole('alert')).toHaveFocus())
    expect(screen.getByLabelText('Password')).toHaveValue('')
    expect(screen.getByRole('alert')).toHaveTextContent('trace-login')
  })
  it('renders explicit expiry feedback', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json({ code: 'DEMO_EXPIRED' }, { status: 401 })))
    mount()
    expect(await screen.findByText('Your session has expired. Sign in again to continue.')).toBeVisible()
    expect(screen.getByRole('button', { name: /explore demo workspace/i })).toBeInTheDocument()
  })
  it.each([
    [429, 'RATE_LIMITED', 'Too many demo attempts. Please wait and try again.'],
    [503, 'DEMO_CAPACITY_REACHED', 'Demo workspaces are temporarily full. Please try again shortly.'],
  ])('keeps fresh demo entry failure %s visible on LoginPage after logout', async (status, code, message) => {
    const activeDemo = { ...session, company: { ...session.company, demo: true, expiresAt: '2026-11-11T00:00:00Z' } }
    const fetch = vi.fn().mockImplementation(async (path) => {
      if (String(path).endsWith('/auth/csrf')) return Response.json({ token: 'token', headerName: 'X-CSRF-TOKEN' })
      if (String(path).endsWith('/auth/me')) return Response.json(activeDemo)
      if (String(path).endsWith('/auth/logout')) return new Response(null, { status: 204 })
      if (String(path).endsWith('/demo/sessions')) return Response.json({ code, requestId: 'fresh-demo-trace' }, { status })
      throw new Error(`Unexpected request: ${path}`)
    })
    vi.stubGlobal('fetch', fetch)
    mount('/login', { from: '/demo', freshDemoWorkspace: true })
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Start fresh demo workspace' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(screen.getByRole('heading', { name: 'Welcome back' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Explore demo workspace' })).toBeEnabled()
    expect(fetch.mock.calls.filter(([path]) => String(path).endsWith('/auth/logout'))).toHaveLength(1)
    expect(fetch.mock.calls.filter(([path]) => String(path).endsWith('/demo/sessions'))).toHaveLength(1)
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
  })
  it('keeps proxy login failures readable without rendering their body', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(async (path) => {
      if (path.endsWith('/auth/csrf')) return Response.json({ token: 'token', headerName: 'X-CSRF-TOKEN' })
      if (path.endsWith('/auth/me')) return Response.json({ code: 'UNAUTHENTICATED' }, { status: 401 })
      return new Response('<html>internal proxy details</html>', { status: 502 })
    }))
    mount()
    const user = await enterCredentials()
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('We could not sign you in.')
    expect(screen.queryByText(/internal proxy details/)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeEnabled()
  })
})
