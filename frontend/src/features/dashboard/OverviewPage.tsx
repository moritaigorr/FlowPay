import { useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { ArrowDownLeft, ArrowRight, ArrowUpRight, CalendarDays, CreditCard, RotateCw } from 'lucide-react'
import { Link } from 'react-router'
import { Button } from '../../components/ui/button'
import { StatusBadge } from '../../components/shared/status-badge'
import { ApiError } from '../../lib/http'
import { useAuth } from '../auth/auth-context'
import { formatBrl } from '../payments/money'
import { formatAggregateBrl } from './format'
import { useDashboardSummary, useRecentPayments } from './hooks'
import './overview.css'

const periodOptions = [7, 30, 90] as const

export function OverviewPage() {
  const auth = useAuth()
  const [periodDays, setPeriodDays] = useState<number>(30)
  const period = useMemo(() => makePeriod(periodDays), [periodDays])
  const summary = useDashboardSummary(auth.scope ?? '', period)
  const recentPayments = useRecentPayments(auth.scope ?? '', period)

  return <section className="overview-page">
    <header className="page-heading overview-heading">
      <div>
        <p className="overview-eyebrow"><CalendarDays aria-hidden="true" /> Company activity</p>
        <h1 tabIndex={-1}>Overview</h1>
        <p>Payment activity and current outcomes for this workspace.</p>
      </div>
      <div className="overview-heading-actions">
        <Link to="/demo" className="button button-secondary overview-guide-link">Run guided demo<ArrowRight aria-hidden="true" /></Link>
        <label className="overview-period">
          <span>Reporting period</span>
          <select value={periodDays} onChange={(event) => setPeriodDays(Number(event.target.value))}>
            {periodOptions.map((days) => <option key={days} value={days}>Last {days} days</option>)}
          </select>
        </label>
      </div>
    </header>

    <p className="overview-definition">Metrics group payments by creation date in UTC and use their current status. They can change when a pending payment is processed. Payment times below use your local timezone.</p>

    <section className="overview-metrics" aria-label="Payment metrics" aria-busy={summary.isPending}>
      {summary.isPending && <MetricLoading />}
      {summary.isError && <div className="overview-error" role="alert">
        <div><h2>Metrics could not be loaded</h2><p>{errorMessage(summary.error)}</p></div>
        {isExpired(summary.error) ? <Link className="overview-recovery" to="/login">Sign in again</Link>
          : <Button variant="secondary" onClick={() => void summary.refetch()}><RotateCw aria-hidden="true" />Retry metrics</Button>}
      </div>}
      {summary.isSuccess && <>
        <MetricCard label="Approved volume" value={formatAggregateBrl(summary.data.approvedVolume)} detail="Approved payments in this period" icon={<ArrowUpRight aria-hidden="true" />} tone="approved" />
        <MetricCard label="Pending payments" value={summary.data.pendingCount.toLocaleString('en-US')} detail={`${formatAggregateBrl(summary.data.pendingAmount)} awaiting an outcome`} icon={<CreditCard aria-hidden="true" />} tone="pending" />
        <MetricCard label="Approval rate" value={formatRate(summary.data.approvalRate)} detail={`${summary.data.approvedCount.toLocaleString('en-US')} approved · ${summary.data.rejectedCount.toLocaleString('en-US')} declined`} icon={<ArrowDownLeft aria-hidden="true" />} tone="rate" />
        <MetricCard label="Total payments" value={summary.data.totalPayments.toLocaleString('en-US')} detail="All statuses in this period" icon={<CalendarDays aria-hidden="true" />} tone="total" />
      </>}
    </section>

    <section className="overview-recent" aria-labelledby="recent-title" aria-busy={recentPayments.isPending}>
      <div className="overview-section-heading">
        <div><h2 id="recent-title">Recent payments</h2><p>Latest records created during the selected period.</p></div>
        <Link to="/payments" className="overview-recovery">View all payments</Link>
      </div>
      {recentPayments.isPending && <p className="overview-status" role="status">Loading recent payments…</p>}
      {recentPayments.isError && <div className="overview-error overview-recent-error" role="alert">
        <p>{isExpired(recentPayments.error) ? 'Your session ended. Sign in again; no action was submitted.' : 'Recent payments could not be loaded.'}</p>
        {isExpired(recentPayments.error) ? <Link className="overview-recovery" to="/login">Sign in again</Link>
          : <Button variant="secondary" onClick={() => void recentPayments.refetch()}>Retry recent payments</Button>}
      </div>}
      {recentPayments.isSuccess && recentPayments.data.items.length === 0 && <div className="overview-empty">
        <CreditCard aria-hidden="true" /><h3>No payments in this period yet</h3><p>Create a simulated payment to see it here.</p>
        <Link to="/payments" className="overview-recovery">Go to payments</Link>
      </div>}
      {recentPayments.isSuccess && recentPayments.data.items.length > 0 && <div className="overview-table-scroll">
        <table className="overview-table"><thead><tr><th scope="col">Payment</th><th scope="col">Description</th><th scope="col">Amount</th><th scope="col">Status</th><th scope="col">Created</th></tr></thead>
          <tbody>{recentPayments.data.items.map((payment) => <tr key={payment.id}>
            <th scope="row"><span className="overview-id">{payment.id}</span></th>
            <td>{payment.description || '—'}</td>
            <td className="overview-amount">{formatBrl(payment.amount)}</td>
            <td><StatusBadge status={payment.status} /></td>
            <td>{formatDate(payment.createdAt)}</td>
          </tr>)}</tbody>
        </table>
      </div>}
    </section>
  </section>
}

function MetricCard({ label, value, detail, icon, tone }: { label: string; value: string; detail: string; icon: ReactNode; tone: string }) {
  return <article className="overview-card" data-tone={tone}>
    <div className="overview-card-top"><span>{label}</span><span className="overview-icon">{icon}</span></div>
    <p className="overview-value">{value}</p>
    <p className="overview-detail">{detail}</p>
  </article>
}

function MetricLoading() {
  return <div className="overview-loading" role="status" aria-label="Loading dashboard metrics">
    <span>Loading dashboard metrics…</span>
    {periodOptions.map((period) => <span className="overview-skeleton" key={period} aria-hidden="true" />)}
  </div>
}

function makePeriod(days: number, now = new Date()) {
  const to = now.toISOString()
  const from = new Date(now.getTime() - days * 24 * 60 * 60 * 1000).toISOString()
  return { from, to }
}

function formatRate(value: string | null) {
  if (value === null) return 'No data'
  return new Intl.NumberFormat('en-US', { style: 'percent', minimumFractionDigits: 1, maximumFractionDigits: 2 }).format(Number(value))
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function isExpired(error: Error) { return error instanceof ApiError && error.status === 401 }
function errorMessage(error: Error) { return isExpired(error) ? 'Your session ended. Sign in again; no action was submitted.' : error.message || 'Check your connection and try again.' }
