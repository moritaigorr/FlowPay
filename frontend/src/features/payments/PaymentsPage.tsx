import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { ArrowDownUp, ArrowLeft, ArrowRight, CreditCard, Plus, Search, SlidersHorizontal } from 'lucide-react'
import { Link, useSearchParams } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { Button } from '../../components/ui/button'
import { StatusBadge } from '../../components/shared/status-badge'
import { sessionKey } from '../../lib/query-client'
import { ApiError } from '../../lib/http'
import { useAuth } from '../auth/auth-context'
import { listCustomers } from '../customers/api'
import type { Customer } from '../customers/api'
import { CreatePaymentForm } from './CreatePaymentForm'
import type { PaymentStatus } from './api'
import { usePayments } from './hooks'
import { updateListFilters } from './list-url'
import { formatBrl } from './money'
import '../customers/customer-payment.css'

const statuses: PaymentStatus[] = ['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED']

export function PaymentsPage() {
  const auth = useAuth()
  const scope = auth.scope ?? ''
  const [params, setParams] = useSearchParams()
  const [showForm, setShowForm] = useState(false)
  const [customerFilterInput, setCustomerFilterInput] = useState('')
  const [customerFilterSearch, setCustomerFilterSearch] = useState('')
  const [customerFilterPage, setCustomerFilterPage] = useState(0)
  const [selectedFilterCustomer, setSelectedFilterCustomer] = useState<Customer>()
  const page = readPage(params.get('page'))
  const q = params.get('q') ?? ''
  const status = isStatus(params.get('status')) ? params.get('status') as PaymentStatus : undefined
  const createdFrom = params.get('createdFrom') ?? ''
  const createdTo = params.get('createdTo') ?? ''
  const customerId = params.get('customerId') ?? ''
  const size = 20
  const invalidRange = !!createdFrom && !!createdTo && createdFrom >= createdTo

  const customersQuery = useQuery({
    queryKey: sessionKey(scope, 'customer-options'),
    queryFn: ({ signal }) => listCustomers({ page: 0, size: 100, q: '' }, signal),
    enabled: showForm,
  })
  const customers = customersQuery.data?.items ?? []
  const customerFilterOptions = useQuery({
    queryKey: sessionKey(scope, 'customer-filter-options', { q: customerFilterSearch, page: customerFilterPage, size: 20 }),
    queryFn: ({ signal }) => listCustomers({ page: customerFilterPage, size: 20, q: customerFilterSearch }, signal),
  })
  const filterCustomers = customerFilterOptions.data?.items ?? []
  const currentFilterCustomer = customerId
    ? selectedFilterCustomer?.id === customerId
      ? selectedFilterCustomer
      : customers.find((customer) => customer.id === customerId)
        ?? filterCustomers.find((customer) => customer.id === customerId)
        ?? { id: customerId, name: `Customer ${customerId.slice(0, 8)}`, email: '', createdAt: '', updatedAt: '' }
    : undefined
  const visibleFilterCustomers = currentFilterCustomer && !filterCustomers.some((customer) => customer.id === currentFilterCustomer.id)
    ? [currentFilterCustomer, ...filterCustomers]
    : filterCustomers
  const filters = useMemo(() => ({
    page, size, q, status, customerId: customerId || undefined,
    createdFrom: createdFrom ? `${createdFrom}T00:00:00Z` : undefined,
    createdTo: createdTo ? `${createdTo}T00:00:00Z` : undefined,
  }), [page, size, q, status, customerId, createdFrom, createdTo])
  const payments = usePayments(scope, filters, !invalidRange)

  function updateFilter(changes: Record<string, string>) {
    setParams(updateListFilters(params, changes))
  }
  function applySearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    updateFilter({ q: String(data.get('q') ?? '').trim() })
  }
  function changePage(value: number) {
    const next = new URLSearchParams(params)
    next.set('page', String(Math.max(0, value)))
    setParams(next)
  }
  function submitCustomerFilterSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setCustomerFilterSearch(customerFilterInput.trim())
    setCustomerFilterPage(0)
  }

  return <section className="business-page">
    <div className="page-heading business-heading">
      <div><h1 tabIndex={-1}>Payments</h1><p>Find simulated payments and create a new transaction.</p></div>
      <Button onClick={() => setShowForm((visible) => !visible)} aria-expanded={showForm} aria-controls="payment-create-panel"><Plus aria-hidden="true" />{showForm ? 'Close form' : 'Create payment'}</Button>
    </div>
    {showForm && <section id="payment-create-panel" className="create-panel" aria-labelledby="payment-create-title">
      <div className="panel-title"><div className="empty-icon"><CreditCard aria-hidden="true" /></div><div><h2 id="payment-create-title">Create a simulated payment</h2><p>It will be saved as Pending. No real money is moved.</p></div></div>
      {customersQuery.isPending && <p className="list-message" role="status">Loading customers…</p>}
      {customersQuery.isError && <div className="list-message" role="alert">Customers could not be loaded. <Button variant="secondary" onClick={() => void customersQuery.refetch()}>Try again</Button></div>}
      {customersQuery.isSuccess && customers.length === 0 && <div className="list-empty compact-empty"><p>Add a customer before creating a payment.</p><Link className="text-link" to="/customers">Go to customers</Link></div>}
      {customersQuery.isSuccess && customersQuery.data.totalElements > 0 && <CreatePaymentForm scope={scope} />}
    </section>}

    <section className="data-panel" aria-labelledby="payment-list-title">
      <div className="data-panel-head"><div><h2 id="payment-list-title">Payment activity</h2><p>{payments.data ? `${payments.data.totalElements} result${payments.data.totalElements === 1 ? '' : 's'}` : 'Search and filter persisted payments'}</p></div>
        <form key={q} className="search-form" role="search" onSubmit={applySearch}><label className="visually-hidden" htmlFor="payment-search">Search payments</label><Search aria-hidden="true" /><input id="payment-search" name="q" defaultValue={q} placeholder="Payment ID or customer" /><Button variant="secondary" type="submit">Search</Button></form>
      </div>
      <div className="payment-filter-panel">
        <div className="payment-filter-heading"><SlidersHorizontal aria-hidden="true" /><span>Filter payments</span>{(q || status || customerId || createdFrom || createdTo) && <Button variant="ghost" onClick={() => { setSelectedFilterCustomer(undefined); setCustomerFilterInput(''); setCustomerFilterSearch(''); setParams(new URLSearchParams()) }}>Clear all</Button>}</div>
        <div className="payment-filters">
        <label>Status<select aria-label="Filter by status" value={status ?? ''} onChange={(event) => updateFilter({ status: event.target.value })}><option value="">All statuses</option>{statuses.map((item) => <option key={item} value={item}>{statusLabel(item)}</option>)}</select></label>
        <form className="customer-filter-search" role="search" onSubmit={submitCustomerFilterSearch}>
          <label htmlFor="customer-filter-search">Search customers</label>
          <input id="customer-filter-search" type="search" value={customerFilterInput} onChange={(event) => setCustomerFilterInput(event.target.value)} placeholder="Name or email" aria-describedby="customer-filter-search-help" />
          <p className="field-help" id="customer-filter-search-help">Find a customer to use as a filter.</p>
          <Button type="submit" variant="secondary" aria-label="Apply customer search"><Search aria-hidden="true" /></Button>
        </form>
        <label>Customer<select aria-label="Filter by customer" value={customerId} disabled={customerFilterOptions.isPending || customerFilterOptions.isError} onChange={(event) => {
          const value = event.target.value
          const selected = visibleFilterCustomers.find((customer) => customer.id === value)
          setSelectedFilterCustomer(selected)
          updateFilter({ customerId: value })
        }}><option value="">All customers</option>{visibleFilterCustomers.map((customer) => <option key={customer.id} value={customer.id}>{customer.name}</option>)}</select></label>
        {customerFilterOptions.isError && <p className="field-error" role="alert">Customer filters could not be loaded. Try again.</p>}
        {customerFilterOptions.isSuccess && customerFilterOptions.data.totalPages > 1 && <div className="pagination customer-filter-pages"><span>Customer results · Page {customerFilterPage + 1} of {customerFilterOptions.data.totalPages}</span><div>
          <Button type="button" variant="secondary" aria-label="Previous customer results" disabled={customerFilterPage === 0} onClick={() => setCustomerFilterPage((page) => page - 1)}>Previous</Button>
          <Button type="button" variant="secondary" aria-label="Next customer results" disabled={customerFilterPage + 1 >= customerFilterOptions.data.totalPages} onClick={() => setCustomerFilterPage((page) => page + 1)}>Next</Button>
        </div></div>}
        {customerFilterOptions.isSuccess && customerFilterOptions.data.totalElements === 0 && <p className="list-message" role="status">No customers match this search.</p>}
        <label>Created from (UTC)<input type="date" aria-label="Created from (UTC, inclusive)" value={createdFrom} onChange={(event) => updateFilter({ createdFrom: event.target.value })} /></label>
        <label>Before (UTC)<input type="date" aria-label="Created before (UTC, exclusive)" value={createdTo} onChange={(event) => updateFilter({ createdTo: event.target.value })} /></label>
        </div>
      </div>
      {invalidRange && <p className="form-error range-error" role="alert">The start date must be before the exclusive end date.</p>}
      {payments.isPending && <p className="list-message" role="status">Loading payments…</p>}
      {payments.isError && <div className="list-message" role="alert"><strong>Payments could not be loaded.</strong><p>{messageFor(payments.error)}</p><Button variant="secondary" onClick={() => void payments.refetch()}>Try again</Button></div>}
      {payments.isSuccess && payments.data.items.length === 0 && <div className="list-empty"><div className="empty-icon"><ArrowDownUp aria-hidden="true" /></div><h3>{q || status || customerId || createdFrom || createdTo ? 'No payments match these filters' : 'No payments yet'}</h3><p>{q || status || customerId || createdFrom || createdTo ? 'Change a filter or start a new search.' : 'Create a simulated payment to see it here.'}</p></div>}
      {payments.isSuccess && payments.data.items.length > 0 && <>
        <div className="table-scroll"><table className="business-table payment-table"><thead><tr><th scope="col">Payment</th><th scope="col">Customer</th><th scope="col">Amount</th><th scope="col">Status</th><th scope="col">Created</th></tr></thead><tbody>{payments.data.items.map((payment) => {
          const customerName = customers.find((customer) => customer.id === payment.customerId)?.name
          return <tr key={payment.id}><th scope="row"><Link className="record-id payment-detail-link" to={`/payments/${payment.id}`} aria-label={`View payment ${payment.id}`}>{payment.id}</Link>{payment.description && <small className="customer-name">{payment.description}</small>}</th><td title={payment.customerId}>{customerName ?? `Customer ${payment.customerId.slice(0, 8)}`}</td><td className="amount-cell">{formatBrl(payment.amount)}</td><td><StatusBadge status={payment.status} /></td><td>{formatDate(payment.createdAt)}</td></tr>
        })}</tbody></table></div>
        <div className="pagination"><span>Page {payments.data.page + 1} of {Math.max(payments.data.totalPages, 1)} · {payments.data.totalElements} total</span><div><Button variant="secondary" disabled={page === 0} onClick={() => changePage(page - 1)} aria-label="Previous page"><ArrowLeft aria-hidden="true" />Previous</Button><Button variant="secondary" disabled={payments.data.totalPages === 0 || page + 1 >= payments.data.totalPages} onClick={() => changePage(page + 1)} aria-label="Next page">Next<ArrowRight aria-hidden="true" /></Button></div></div>
      </>}
    </section>
  </section>
}

function readPage(value: string | null) { const page = Number(value); return Number.isSafeInteger(page) && page >= 0 && page <= 2_147_483_647 ? page : 0 }
function isStatus(value: string | null): value is PaymentStatus { return statuses.includes(value as PaymentStatus) }
function statusLabel(status: PaymentStatus) { return status[0] + status.slice(1).toLowerCase() }
function formatDate(value: string) { return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) }
function messageFor(error: Error) { return error instanceof ApiError && error.status === 401 ? 'Your session ended. Sign in again; this page will not submit anything automatically.' : error.message || 'Check your connection and try again.' }
