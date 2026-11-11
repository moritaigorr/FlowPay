import { ArrowLeft, FileQuestion } from 'lucide-react'
import { Link } from 'react-router'
import { Button } from '../ui/button'

export function RouteLoading() {
  return <div className="route-loading" role="status">Loading workspace…</div>
}

export function NotFoundPage() {
  return <>
    <header className="page-heading"><h1 tabIndex={-1}>Page not found</h1><p>This page does not exist in the workspace.</p></header>
    <section className="unavailable-panel" aria-label="Page recovery">
      <div className="empty-icon"><FileQuestion aria-hidden="true" /></div>
      <h2>Let’s get you back on track.</h2>
      <p>Use the workspace navigation or return to Payments.</p>
      <Button asChild><Link to="/payments"><ArrowLeft aria-hidden="true" />Back to payments</Link></Button>
    </section>
  </>
}
