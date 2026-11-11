import { afterEach, describe, expect, it, vi } from 'vitest'
import { SessionController } from './session'
import { createQueryClient, sessionKey, retryRead } from '../../lib/query-client'
import { ApiError, resetCsrf } from '../../lib/http'

const identity = (company = 'company-a') => ({ user: { id: 'user-a', email: 'operator@example.test', createdAt: '2026-11-09T00:00:00Z' }, company: { id: company, name: company, demo: false, expiresAt: null, createdAt: '2026-11-09T00:00:00Z' } })
afterEach(() => { vi.unstubAllGlobals(); resetCsrf() })
describe('session lifecycle', () => {
  it('enters an isolated demo through the CSRF-protected demo endpoint and scopes its session', async () => {
    const fetch = vi.fn().mockImplementation(async (path) => {
      if (path.endsWith('/auth/csrf')) return Response.json({ token: 'token', headerName: 'X-CSRF-TOKEN' })
      if (path.endsWith('/demo/sessions')) return Response.json({ ...identity('demo-company'), company: { ...identity('demo-company').company, demo: true, expiresAt: '2026-11-11T00:00:00Z' } }, { status: 201 })
      throw new Error(`Unexpected request: ${path}`)
    })
    vi.stubGlobal('fetch', fetch)
    const auth = new SessionController(createQueryClient())
    await auth.enterDemo()
    expect(fetch.mock.calls.map(([path]) => String(path))).toEqual(['/api/v1/auth/csrf', '/api/v1/demo/sessions'])
    expect(auth.getSnapshot().status).toBe('authenticated')
    expect(auth.getSnapshot().session?.company.demo).toBe(true)
    expect(auth.getSnapshot().scope).toContain('demo-company')
    auth.dispose()
  })
  it.each(['login', 'logout'] as const)('does not let identity refresh abort a pending %s', async (action) => {
    let complete!: () => void
    let writeSignal!: AbortSignal
    const fetch = vi.fn().mockImplementation(async (path, options) => {
      if (path.endsWith('/auth/csrf')) return Response.json({ token: 'token', headerName: 'X-CSRF-TOKEN' })
      if (path.endsWith(`/auth/${action}`)) {
        writeSignal = options.signal
        return new Promise<Response>((resolve) => { complete = () => resolve(action === 'login' ? Response.json(identity()) : new Response(null, { status: 204 })) })
      }
      return Response.json({ code: 'UNAUTHENTICATED' }, { status: 401 })
    })
    vi.stubGlobal('fetch', fetch)
    const client = createQueryClient()
    const auth = new SessionController(client)
    const pending = (action === 'login' ? auth.login('operator@example.test', 'password') : auth.logout()).catch((error: unknown) => error)
    await vi.waitFor(() => expect(complete).toBeDefined())
    await auth.refresh()
    const aborted = writeSignal.aborted
    complete()
    const result = await pending
    expect(aborted).toBe(false)
    expect(result).toBeUndefined()
    expect(fetch.mock.calls.some(([path]) => path.endsWith('/auth/me'))).toBe(false)
    expect(auth.getSnapshot().status).toBe(action === 'login' ? 'authenticated' : 'anonymous')
    auth.dispose()
  })
  it('ignores an identity response that completes after a new login', async () => {
    let completeIdentity!: () => void
    let identitySignal!: AbortSignal
    vi.stubGlobal('fetch', vi.fn().mockImplementation(async (path, options) => {
      if (path.endsWith('/auth/csrf')) return Response.json({ token: 'token', headerName: 'X-CSRF-TOKEN' })
      if (path.endsWith('/auth/login')) return Response.json(identity('company-new'))
      identitySignal = options.signal
      return new Promise<Response>((resolve) => { completeIdentity = () => resolve(Response.json(identity('company-old'))) })
    }))
    const client = createQueryClient()
    const auth = new SessionController(client)
    const oldRefresh = auth.refresh()
    await vi.waitFor(() => expect(completeIdentity).toBeDefined())
    await auth.login('operator@example.test', 'password')
    expect(identitySignal.aborted).toBe(true)
    completeIdentity()
    await oldRefresh
    expect(auth.getSnapshot().session?.company.id).toBe('company-new')
    auth.dispose()
  })
  it('cannot restore the workspace from a stale identity response after logout', async () => {
    let completeIdentity!: () => void
    let deferIdentity = false
    vi.stubGlobal('fetch', vi.fn().mockImplementation(async (path) => {
      if (path.endsWith('/auth/csrf')) return Response.json({ token: 'token', headerName: 'X-CSRF-TOKEN' })
      if (path.endsWith('/auth/logout')) return new Response(null, { status: 204 })
      if (!deferIdentity) return Response.json(identity())
      return new Promise<Response>((resolve) => { completeIdentity = () => resolve(Response.json(identity())) })
    }))
    const client = createQueryClient()
    const auth = new SessionController(client)
    await auth.refresh()
    deferIdentity = true
    const oldRefresh = auth.refresh()
    await vi.waitFor(() => expect(completeIdentity).toBeDefined())
    await auth.logout()
    completeIdentity()
    await oldRefresh
    expect(auth.getSnapshot().status).toBe('anonymous')
    expect(auth.getSnapshot().session).toBeUndefined()
    expect(client.getQueryCache().getAll()).toHaveLength(0)
    auth.dispose()
  })
  it('cannot reopen the workspace with a successful focus response started during logout', async () => {
    let completeLogout!: () => void
    let completeIdentity: (() => void) | undefined
    let loggingOut = false
    vi.stubGlobal('fetch', vi.fn().mockImplementation(async (path) => {
      if (path.endsWith('/auth/csrf')) return Response.json({ token: 'token', headerName: 'X-CSRF-TOKEN' })
      if (path.endsWith('/auth/logout')) return new Promise<Response>((resolve) => { completeLogout = () => resolve(new Response(null, { status: 204 })) })
      if (!loggingOut) return Response.json(identity())
      return new Promise<Response>((resolve) => { completeIdentity = () => resolve(Response.json(identity())) })
    }))
    const client = createQueryClient()
    const auth = new SessionController(client)
    await auth.refresh()
    loggingOut = true
    const logout = auth.logout()
    await vi.waitFor(() => expect(completeLogout).toBeDefined())
    const refresh = auth.refresh()
    completeLogout()
    await logout
    completeIdentity?.()
    await refresh
    expect(auth.getSnapshot().status).toBe('anonymous')
    expect(auth.getSnapshot().session).toBeUndefined()
    expect(client.getQueryCache().getAll()).toHaveLength(0)
    auth.dispose()
  })
  it('rejects overlapping authentication writes without cancelling the first one', async () => {
    let complete!: () => void
    const fetch = vi.fn().mockImplementation(async (path) => {
      if (path.endsWith('/auth/csrf')) return Response.json({ token: 'token', headerName: 'X-CSRF-TOKEN' })
      return new Promise<Response>((resolve) => { complete = () => resolve(Response.json(identity())) })
    })
    vi.stubGlobal('fetch', fetch)
    const auth = new SessionController(createQueryClient())
    const login = auth.login('operator@example.test', 'password')
    await vi.waitFor(() => expect(complete).toBeDefined())
    await expect(auth.logout()).rejects.toMatchObject({ code: 'AUTH_IN_PROGRESS' })
    await expect(auth.login('operator@example.test', 'password')).rejects.toMatchObject({ code: 'AUTH_IN_PROGRESS' })
    complete()
    await login
    expect(auth.getSnapshot().status).toBe('authenticated')
    expect(fetch.mock.calls).toHaveLength(2)
    auth.dispose()
  })
  it('bounds read retries and excludes client errors and cancelled work', () => {
    expect(retryRead(0, new Error('offline'))).toBe(true)
    expect(retryRead(2, new Error('offline'))).toBe(false)
    for (const status of [400, 401, 403, 404, 409]) expect(retryRead(0, new ApiError(status, 'error'))).toBe(false)
    expect(retryRead(0, new DOMException('Aborted', 'AbortError'))).toBe(false)
    expect(createQueryClient().getDefaultOptions().mutations?.retry).toBe(false)
  })
  it('executes at most three transient read attempts and one mutation attempt', async () => {
    const client = createQueryClient()
    const read = vi.fn().mockRejectedValue(new Error('offline'))
    await client.fetchQuery({ queryKey: ['retry-test'], queryFn: read, retryDelay: 0 }).catch(() => undefined)
    expect(read).toHaveBeenCalledTimes(3)
    const write = vi.fn().mockRejectedValue(new Error('offline'))
    await client.getMutationCache().build(client, { mutationFn: write }).execute(undefined).catch(() => undefined)
    expect(write).toHaveBeenCalledTimes(1)
    client.clear()
  })
  it('cancels an old pending read on logout and cannot cache its late result in a new session', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(async (path) => {
      if (path.endsWith('/auth/csrf')) return Response.json({ token: 'token', headerName: 'X-CSRF-TOKEN' })
      if (path.endsWith('/auth/logout')) return new Response(null, { status: 204 })
      return Response.json(identity())
    }))
    const client = createQueryClient()
    const auth = new SessionController(client)
    await auth.refresh()
    const oldScope = auth.getSnapshot().scope!
    let finish!: (value: string) => void
    let signal!: AbortSignal
    const pending = client.fetchQuery({ queryKey: sessionKey(oldScope, 'customers'), queryFn: (context) => {
      signal = context.signal
      return new Promise<string>((resolve) => { finish = resolve })
    } }).catch(() => undefined)
    await auth.logout()
    expect(signal.aborted).toBe(true)
    await auth.login('operator@example.test', 'password')
    finish('private old data')
    await pending
    expect(auth.getSnapshot().scope).not.toBe(oldScope)
    expect(client.getQueryData(sessionKey(oldScope, 'customers'))).toBeUndefined()
    auth.dispose()
  })
  it('removes previous company data before publishing a changed identity', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(Response.json(identity())).mockResolvedValueOnce(Response.json(identity('company-b'))))
    const client = createQueryClient()
    const auth = new SessionController(client)
    await auth.refresh()
    const scope = auth.getSnapshot().scope!
    client.setQueryData(sessionKey(scope, 'customers'), ['private'])
    await auth.refresh()
    expect(auth.getSnapshot().session?.company.id).toBe('company-b')
    expect(client.getQueryData(sessionKey(scope, 'customers'))).toBeUndefined()
    auth.dispose()
  })
  it('expires an authenticated session on a scoped unauthorized read', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json(identity())))
    const client = createQueryClient()
    const auth = new SessionController(client)
    await auth.refresh()
    const scope = auth.getSnapshot().scope!
    await client.fetchQuery({ queryKey: sessionKey(scope, 'customers'), queryFn: () => { throw new ApiError(401, 'DEMO_EXPIRED') } }).catch(() => undefined)
    await vi.waitFor(() => expect(auth.getSnapshot().status).toBe('anonymous'))
    expect(auth.getSnapshot().expired).toBe(true)
    expect(client.getQueryCache().getAll()).toHaveLength(0)
    auth.dispose()
  })
  it('expires on a scoped unauthorized mutation and clears protected data', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json(identity())))
    const client = createQueryClient()
    const auth = new SessionController(client)
    await auth.refresh()
    const scope = auth.getSnapshot().scope!
    client.setQueryData(sessionKey(scope, 'customers'), ['private'])
    await client.getMutationCache().build(client, { mutationKey: sessionKey(scope, 'create-customer'),
      mutationFn: async () => { throw new ApiError(401, 'UNAUTHENTICATED') },
    }).execute(undefined).catch(() => undefined)
    await vi.waitFor(() => expect(auth.getSnapshot().status).toBe('anonymous'))
    expect(auth.getSnapshot().expired).toBe(true)
    expect(client.getQueryCache().getAll()).toHaveLength(0)
    auth.dispose()
  })
  it('does not claim sign-out success after a network failure', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(async (path) => {
      if (path.endsWith('/auth/csrf')) return Response.json({ token: 'token', headerName: 'X-CSRF-TOKEN' })
      if (path.endsWith('/auth/logout')) throw new TypeError('offline')
      return Response.json(identity())
    }))
    const client = createQueryClient()
    const auth = new SessionController(client)
    await auth.refresh()
    const scope = auth.getSnapshot().scope!
    client.setQueryData(sessionKey(scope, 'customers'), ['private'])
    await expect(auth.logout()).rejects.toThrow('offline')
    expect(auth.getSnapshot()).toMatchObject({ status: 'error', failedLogout: true })
    expect(auth.getSnapshot().session).toBeUndefined()
    expect(client.getQueryData(sessionKey(scope, 'customers'))).toBeUndefined()
    auth.dispose()
  })
})
