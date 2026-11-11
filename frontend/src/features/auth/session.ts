import type { QueryClient } from '@tanstack/react-query'
import { apiRequest, ApiError, resetCsrf } from '../../lib/http'

export interface Session {
  user: { id: string; email: string; createdAt: string }
  company: { id: string; name: string; createdAt: string; demo: boolean; expiresAt: string | null }
}
export interface AuthState {
  status: 'loading' | 'authenticated' | 'anonymous' | 'error'
  session?: Session
  scope?: string
  expired: boolean
  error?: Error
  failedLogout?: boolean
}

export class SessionController {
  private state: AuthState = { status: 'loading', expired: false }
  private listeners = new Set<() => void>()
  private generation = 0
  private operation = new AbortController()
  private authTransition = false
  private unsubscribe?: () => void

  constructor(readonly client: QueryClient) {}
  private watch() {
    if (this.unsubscribe) return
    const queries = this.client.getQueryCache().subscribe((event) => {
      if (event.type !== 'updated' || event.action.type !== 'error') return
      const error = event.query.state.error
      if (this.state.scope && event.query.queryKey[0] === 'session' && event.query.queryKey[1] === this.state.scope && error instanceof ApiError && error.status === 401) {
        void this.expire()
      }
    })
    const mutations = this.client.getMutationCache().subscribe((event) => {
      if (event.type !== 'updated' || event.action.type !== 'error') return
      const error = event.mutation.state.error
      const key = event.mutation.options.mutationKey
      if (this.state.scope && key?.[0] === 'session' && key[1] === this.state.scope && error instanceof ApiError && error.status === 401) void this.expire()
    })
    this.unsubscribe = () => { queries(); mutations() }
  }
  getSnapshot = () => this.state
  subscribe = (listener: () => void) => { this.listeners.add(listener); return () => { this.listeners.delete(listener) } }
  private publish(state: AuthState) { this.state = state; this.listeners.forEach((listener) => listener()) }
  private async clear() {
    this.generation++
    this.operation.abort()
    this.operation = new AbortController()
    resetCsrf()
    const cancelled = this.client.cancelQueries()
    this.client.clear()
    await cancelled
  }
  private async expire() {
    this.publish({ status: 'loading', expired: true })
    await this.clear()
    this.publish({ status: 'anonymous', expired: true })
  }
  async refresh() {
    // A focus recheck must not compete with login/logout for lifecycle ownership.
    // Their server responses establish the session; the next focus can revalidate it.
    if (this.authTransition) return
    this.watch()
    const generation = this.generation
    try {
      const session = await this.client.fetchQuery({ queryKey: ['identity', generation], staleTime: 0,
        queryFn: async ({ signal }) => requireSession(await apiRequest<Session>('/auth/me', { signal })) })
      if (generation !== this.generation) return
      if (this.state.session && (this.state.session.company.id !== session.company.id || this.state.session.user.id !== session.user.id)) {
        this.publish({ status: 'loading', expired: false })
        await this.clear()
      }
      this.publish({ status: 'authenticated', session, scope: `${this.generation}:${session.company.id}:${session.user.id}`, expired: false })
    } catch (error) {
      if (generation !== this.generation) return
      if (error instanceof ApiError && error.status === 401) {
        const expired = !!this.state.session || error.code === 'DEMO_EXPIRED'
        await this.clear()
        this.publish({ status: 'anonymous', expired })
      } else if (error instanceof Error && error.name !== 'AbortError') {
        // A failed identity recheck must not leave a stale authenticated workspace visible.
        await this.clear()
        this.publish({ status: 'error', error, expired: false })
      }
    }
  }
  async login(email: string, password: string) {
    this.beginAuthTransition()
    try {
      this.watch()
      await this.clear()
      const generation = this.generation
      const session = requireSession(await apiRequest<Session>('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }), signal: this.operation.signal }))
      if (generation !== this.generation) return
      this.publish({ status: 'authenticated', session, scope: `${generation}:${session.company.id}:${session.user.id}`, expired: false })
    } finally { this.authTransition = false }
  }
  async enterDemo() {
    this.beginAuthTransition()
    try {
      this.watch()
      await this.clear()
      const generation = this.generation
      const session = requireSession(await apiRequest<Session>('/demo/sessions', { method: 'POST', signal: this.operation.signal }))
      if (generation !== this.generation) return
      this.publish({ status: 'authenticated', session, scope: `${generation}:${session.company.id}:${session.user.id}`, expired: false })
    } finally { this.authTransition = false }
  }
  async logout() {
    this.beginAuthTransition()
    // Hide the workspace and cancel its reads before the logout write starts.
    this.publish({ status: 'loading', expired: false })
    await this.clear()
    const generation = this.generation
    try {
      await apiRequest<void>('/auth/logout', { method: 'POST', signal: this.operation.signal })
      if (generation !== this.generation) return
      this.publish({ status: 'anonymous', expired: false })
    } catch (error) {
      if (generation !== this.generation) return
      if (error instanceof ApiError && error.status === 401) this.publish({ status: 'anonymous', expired: true })
      else {
        this.publish({ status: 'error', expired: false, failedLogout: true, error: error instanceof Error ? error : new Error('Sign out could not be confirmed.') })
        throw error
      }
    } finally { this.authTransition = false }
  }
  private beginAuthTransition() {
    if (this.authTransition) throw new ApiError(409, 'AUTH_IN_PROGRESS', 'A sign-in or sign-out request is already in progress.')
    this.authTransition = true
  }
  dispose() {
    this.unsubscribe?.(); this.unsubscribe = undefined
    this.operation.abort()
    this.generation++
    void this.client.cancelQueries()
    this.client.clear()
  }
}

function requireSession(session: Session): Session {
  if (!session?.user?.id || !session.user.email || !session.company?.id || !session.company.name) {
    throw new ApiError(502, 'INVALID_RESPONSE', 'The server returned an invalid session.')
  }
  return session
}
