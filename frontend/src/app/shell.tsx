import { useEffect, useRef } from 'react'
import { CreditCard, House, Info, Layers2, UsersRound } from 'lucide-react'
import { Link, matchPath, Outlet, useLocation } from 'react-router'
import { useAuth } from '../features/auth/auth-context'
import { Button } from '../components/ui/button'

const navigation = [
  { to: '/overview', label: 'Overview', icon: House },
  { to: '/payments', label: 'Payments', icon: CreditCard },
  { to: '/customers', label: 'Customers', icon: UsersRound },
]

export function WorkspaceShell() {
  const auth = useAuth()
  const { pathname } = useLocation()
  const mainRef = useRef<HTMLElement>(null)
  const previousPath = useRef(pathname)
  const currentRoute = navigation.find((item) =>
    matchPath({ path: item.to, end: true, caseSensitive: false }, pathname),
  ) ?? (isPaymentDetailsPath(pathname)
    ? navigation.find((item) => item.to === '/payments')
    : undefined)
  const isPaymentDetails = isPaymentDetailsPath(pathname)
  const currentPage = isPaymentDetails ? 'Payment details' : isGuidedDemoPath(pathname) ? 'Guided demo' : currentRoute?.label ?? 'Page not found'

  useEffect(() => {
    document.title = `FlowPay · ${currentPage}`
    if (previousPath.current !== pathname) {
      previousPath.current = pathname
      const main = mainRef.current
      if (!main) return
      const focusHeading = () => {
        const heading = main.querySelector('h1')
        if (!heading) return false
        heading.focus()
        return true
      }
      if (focusHeading()) return
      const observer = new MutationObserver(() => {
        if (focusHeading()) observer.disconnect()
      })
      observer.observe(main, { childList: true, subtree: true })
      return () => observer.disconnect()
    }
  }, [pathname, currentPage])

  return <>
    <a className="skip-link" href="#main-content" onClick={(event) => {
      event.preventDefault()
      mainRef.current?.focus()
    }}>Skip to content</a>
    <div className="workspace">
      <aside className="sidebar" aria-label="Workspace sidebar">
        <div className="brand"><img src="/flowpay-mark.svg" alt="" width="35" height="35" /><span>FlowPay</span></div>
        <div className="workspace-label"><span className="workspace-tile"><Layers2 aria-hidden="true" /></span><span>{auth.session?.company.name}</span></div>
        <nav className="navigation" aria-label="Main navigation">
          {navigation.map(({ to, label, icon: Icon }) => <Link key={to} to={to} aria-current={currentRoute?.to === to ? 'page' : undefined}><Icon aria-hidden="true" /><span>{label}</span></Link>)}
        </nav>
        <div className="sidebar-footer"><p><span className="environment-label">Personal project</span><br />Payment processing, simulated.</p></div>
      </aside>
      <div className="main-column">
        <header className="topbar">
          <div className="breadcrumb" aria-label="Breadcrumb"><span>Workspace</span><span aria-hidden="true">/</span><strong>{currentPage}</strong></div>
          <p className="simulation-notice"><Info aria-hidden="true" /><span>Simulation · No real money is processed</span></p>
          <Button variant="ghost" onClick={() => { void auth.logout().catch(() => { /* The session guard displays the failure. */ }) }}>Sign out</Button>
        </header>
        <main id="main-content" ref={mainRef} tabIndex={-1}><Outlet /></main>
      </div>
    </div>
  </>
}

function isPaymentDetailsPath(pathname: string) {
  const match = matchPath({ path: '/payments/:paymentId', end: true, caseSensitive: false }, pathname)
  return !!match?.params.paymentId && /^[\da-f]{8}-(?:[\da-f]{4}-){3}[\da-f]{12}$/i.test(match.params.paymentId)
}

function isGuidedDemoPath(pathname: string) {
  return !!matchPath({ path: '/demo', end: true, caseSensitive: false }, pathname)
}
