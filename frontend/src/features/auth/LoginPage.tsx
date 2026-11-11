import { useEffect, useRef, useState } from 'react'
import { ArrowRight, Info, LockKeyhole } from 'lucide-react'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { Button } from '../../components/ui/button'
import { ApiError } from '../../lib/http'
import { useAuth } from './auth-context'
import { RouteLoading } from '../../components/shared/route-feedback'

export function LoginPage() {
  const auth = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [pending, setPending] = useState(false)
  const [demoPending, setDemoPending] = useState(false)
  const [error, setError] = useState<string>()
  const [requestId, setRequestId] = useState<string>()
  const submitting = useRef(false)
  const errorRef = useRef<HTMLDivElement>(null)
  useEffect(() => { document.title = 'FlowPay · Sign in' }, [])
  useEffect(() => { if (error) errorRef.current?.focus() }, [error])
  const routeState = location.state as { from?: unknown; freshDemoWorkspace?: unknown } | null
  const freshDemoWorkspace = routeState?.freshDemoWorkspace === true
  const requested = routeState?.from
  const destination = typeof requested === 'string' && /^\/(?!\/|login(?:[/?#]|$))/.test(requested) ? requested : '/payments'
  if (auth.status === 'authenticated' && !freshDemoWorkspace) return <Navigate to={destination} replace />
  if (auth.status === 'loading') return <RouteLoading />

  async function startFreshDemoWorkspace() {
    if (submitting.current || auth.status !== 'authenticated') return
    submitting.current = true
    setDemoPending(true)
    setError(undefined)
    setRequestId(undefined)
    let enteredFreshWorkspace = false
    try {
      // The backend reuses a valid active demo, so this handoff ends it before requesting another.
      await auth.logout()
      await auth.enterDemo()
      enteredFreshWorkspace = true
    } catch (failure) {
      if (!(failure instanceof Error && failure.name === 'AbortError')) {
        setError(freshWorkspaceFailureMessage(failure))
        setRequestId(failure instanceof ApiError ? failure.requestId : undefined)
      }
    } finally {
      submitting.current = false
      setDemoPending(false)
    }
    if (enteredFreshWorkspace) navigate(destination, { replace: true })
  }

  return <div className="login-layout">
    <header className="login-header"><a className="brand" href="/login" aria-label="FlowPay sign in"><img src="/flowpay-mark.svg" alt="" width="35" height="35" /><span>FlowPay</span></a>
      <span className="environment-label">Personal project</span></header>
    <main className="login-main">
      <section className="login-card" aria-labelledby="login-title">
        <div className="empty-icon"><LockKeyhole aria-hidden="true" /></div>
        <h1 id="login-title">Welcome back</h1>
        <p className="login-intro">Sign in to your simulation workspace.</p>
        {auth.expired ? <p className="session-notice" role="status">Your session has expired. Sign in again to continue.</p> : null}
        {auth.status === 'error' ? <p className="form-error" role="alert">We could not confirm your session. You can try signing in again.</p> : null}
        {freshDemoWorkspace && auth.status === 'authenticated' ? <div className="login-access fresh-demo-access">
          <h2>Open a fresh demo workspace</h2>
          <p>This ends the current demo session and requests a new isolated workspace with its own quota. It will not reuse the exhausted workspace.</p>
          {error ? <div className="form-error" id="login-error" role="alert" tabIndex={-1} ref={errorRef}><p>{error}</p>{requestId ? <small>Request ID: {requestId}</small> : null}</div> : null}
          <Button type="button" variant="secondary" disabled={demoPending} aria-busy={demoPending} onClick={() => void startFreshDemoWorkspace()}>{demoPending ? 'Preparing a fresh workspace…' : 'Start fresh demo workspace'}{demoPending ? null : <ArrowRight aria-hidden="true" />}</Button>
        </div> : <>
        <form aria-label="Sign in" onSubmit={async (event) => {
          event.preventDefault()
          if (submitting.current) return
          submitting.current = true
          const form = event.currentTarget
          const fields = new FormData(form)
          const email = String(fields.get('email') ?? '').trim()
          const password = String(fields.get('password') ?? '')
          setPending(true); setError(undefined); setRequestId(undefined)
          try { await auth.login(email, password); form.reset() } catch (failure) {
            if (failure instanceof Error && failure.name === 'AbortError') return
            setError(failure instanceof ApiError && failure.code === 'INVALID_CREDENTIALS'
              ? 'The email or password is incorrect.'
              : failure instanceof ApiError && failure.status === 429 ? 'Too many sign-in attempts. Please wait and try again.'
              : 'We could not sign you in. Please check your connection and try again.')
            setRequestId(failure instanceof ApiError ? failure.requestId : undefined)
            const passwordInput = form.elements.namedItem('password') as HTMLInputElement
            passwordInput.value = ''
          } finally { submitting.current = false; setPending(false) }
        }}>
          <div className="form-field"><label htmlFor="email">Email</label><input id="email" name="email" type="email" autoComplete="username" required maxLength={254} disabled={pending || demoPending} aria-describedby={error ? 'login-error' : undefined} /></div>
          <div className="form-field"><label htmlFor="password">Password</label><input id="password" name="password" type="password" autoComplete="current-password" required disabled={pending || demoPending} aria-describedby={error ? 'login-error' : undefined} /></div>
          {error ? <div className="form-error" id="login-error" role="alert" tabIndex={-1} ref={errorRef}><p>{error}</p>{requestId ? <small>Request ID: {requestId}</small> : null}</div> : null}
          <Button type="submit" disabled={pending || demoPending} aria-busy={pending}>{pending ? 'Signing in…' : 'Sign in'}{pending ? null : <ArrowRight aria-hidden="true" />}</Button>
        </form>
        <div className="login-access"><p>Use a provisioned operator account, or explore with isolated synthetic data.</p>
          <Button type="button" variant="secondary" disabled={pending || demoPending} aria-busy={demoPending} onClick={async () => {
            setDemoPending(true); setError(undefined); setRequestId(undefined)
            try { await auth.enterDemo() } catch (failure) {
              if (failure instanceof Error && failure.name === 'AbortError') return
              setError(failure instanceof ApiError && failure.status === 429 ? 'Too many demo attempts. Please wait and try again.'
                : failure instanceof ApiError && failure.code === 'DEMO_CAPACITY_REACHED' ? 'Demo workspaces are temporarily full. Please try again shortly.'
                : 'We could not start a demo workspace. Please try again.')
              setRequestId(failure instanceof ApiError ? failure.requestId : undefined)
              errorRef.current?.focus()
            } finally { setDemoPending(false) }
          }}>{demoPending ? 'Preparing demo…' : 'Explore demo workspace'}{demoPending ? null : <ArrowRight aria-hidden="true" />}</Button>
        </div>
        </>}
      </section>
      <p className="login-simulation"><Info aria-hidden="true" /><span>Payment simulation. No real money is processed.</span></p>
    </main>
  </div>
}

function freshWorkspaceFailureMessage(failure: unknown) {
  if (failure instanceof ApiError && failure.status === 429) return 'Too many demo attempts. Please wait and try again.'
  if (failure instanceof ApiError && failure.status === 503 && failure.code === 'DEMO_CAPACITY_REACHED') return 'Demo workspaces are temporarily full. Please try again shortly.'
  if (failure instanceof ApiError && failure.code === 'AUTH_IN_PROGRESS') return 'Another sign-in or sign-out action is still running. Please wait and try again.'
  return 'We could not start a fresh demo workspace. Please try again.'
}
