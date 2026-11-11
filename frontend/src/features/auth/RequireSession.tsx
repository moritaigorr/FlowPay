import { Navigate, Outlet, useLocation } from 'react-router'
import { RouteLoading } from '../../components/shared/route-feedback'
import { Button } from '../../components/ui/button'
import { useAuth } from './auth-context'

export function RequireSession() {
  const auth = useAuth()
  const location = useLocation()
  if (auth.status === 'loading') return <RouteLoading />
  if (auth.status === 'error') return <main className="session-feedback">
    <h1>{auth.failedLogout ? 'Sign out not confirmed' : 'Session unavailable'}</h1>
    <p role="alert">{auth.failedLogout ? 'Your sign-out request could not be confirmed. Please try again to end your session.' : 'We could not confirm your session. Check your connection and try again.'}</p>
    <Button onClick={() => { if (auth.failedLogout) void auth.logout().catch(() => {}); else void auth.refresh() }}>{auth.failedLogout ? 'Retry sign out' : 'Try again'}</Button>
  </main>
  if (auth.status !== 'authenticated') return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  return <Outlet />
}
