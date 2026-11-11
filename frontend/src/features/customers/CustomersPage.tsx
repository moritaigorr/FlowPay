import { useState } from 'react'
import type { FormEvent } from 'react'
import { ArrowLeft, ArrowRight, Plus, Search, UsersRound } from 'lucide-react'
import { useSearchParams } from 'react-router'
import { Button } from '../../components/ui/button'
import { useAuth } from '../auth/auth-context'
import { useCustomers } from './hooks'
import { CustomerForm } from './CustomerForm'
import { updateListFilters } from '../payments/list-url'
import './customer-payment.css'

export function CustomersPage() {
  const auth = useAuth()
  const scope = auth.scope ?? ''
  const [params, setParams] = useSearchParams()
  const [showForm, setShowForm] = useState(false)
  const page = readPage(params.get('page'))
  const size = 20
  const q = params.get('q') ?? ''
  const result = useCustomers(scope, { page, size, q })

  function changePage(value: number) {
    const next = new URLSearchParams(params)
    next.set('page', String(Math.max(0, value)))
    setParams(next)
  }
  function applySearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    setParams(updateListFilters(params, { q: String(data.get('q') ?? '').trim() }))
  }

  return <section className="business-page">
    <div className="page-heading business-heading">
      <div><h1 tabIndex={-1}>Customers</h1><p>People and businesses you can use in simulated payments.</p></div>
      <Button onClick={() => setShowForm((visible) => !visible)} aria-expanded={showForm} aria-controls="customer-create-panel"><Plus aria-hidden="true" />{showForm ? 'Close form' : 'Add customer'}</Button>
    </div>
    {showForm && <section id="customer-create-panel" className="create-panel" aria-labelledby="customer-create-title">
      <div className="panel-title"><div className="empty-icon"><UsersRound aria-hidden="true" /></div><div><h2 id="customer-create-title">Add a customer</h2><p>Customer details stay inside this company workspace.</p></div></div>
      <CustomerForm scope={scope} />
    </section>}
    <section className="data-panel" aria-labelledby="customer-list-title">
      <div className="data-panel-head"><div><h2 id="customer-list-title">Customer directory</h2><p>{result.data ? `${result.data.totalElements} customer${result.data.totalElements === 1 ? '' : 's'}` : 'Search your saved customers'}</p></div>
        <form key={q} className="search-form" role="search" onSubmit={applySearch}><label className="visually-hidden" htmlFor="customer-search">Search customers</label><Search aria-hidden="true" /><input id="customer-search" name="q" defaultValue={q} placeholder="Name or email" /><Button variant="secondary" type="submit">Search</Button></form>
      </div>
      {result.isPending && <p className="list-message" role="status">Loading customers…</p>}
      {result.isError && <div className="list-message" role="alert"><strong>Customers could not be loaded.</strong><p>{messageFor(result.error)}</p><Button variant="secondary" onClick={() => void result.refetch()}>Try again</Button></div>}
      {result.isSuccess && result.data.items.length === 0 && <div className="list-empty"><div className="empty-icon"><UsersRound aria-hidden="true" /></div><h3>{q ? 'No customers match this search' : 'No customers yet'}</h3><p>{q ? 'Try a different name or email.' : 'Add a customer first, then select them when creating a payment.'}</p>{!q && <Button variant="secondary" onClick={() => setShowForm(true)}><Plus aria-hidden="true" />Add your first customer</Button>}</div>}
      {result.isSuccess && result.data.items.length > 0 && <>
        <div className="table-scroll"><table className="business-table"><thead><tr><th scope="col">Customer</th><th scope="col">Email</th><th scope="col">Added</th></tr></thead><tbody>{result.data.items.map((customer) => <tr key={customer.id}><th scope="row"><span className="customer-name">{customer.name}</span><small className="record-id">{customer.id}</small></th><td>{customer.email}</td><td>{formatDate(customer.createdAt)}</td></tr>)}</tbody></table></div>
        <Pagination page={result.data.page} totalPages={result.data.totalPages} totalElements={result.data.totalElements} onPage={changePage} />
      </>}
    </section>
  </section>
}

function Pagination({ page, totalPages, totalElements, onPage }: { page: number; totalPages: number; totalElements: number; onPage: (page: number) => void }) {
  return <div className="pagination"><span>Page {page + 1} of {Math.max(totalPages, 1)} · {totalElements} total</span><div><Button variant="secondary" disabled={page === 0} onClick={() => onPage(page - 1)} aria-label="Previous page"><ArrowLeft aria-hidden="true" />Previous</Button><Button variant="secondary" disabled={totalPages === 0 || page + 1 >= totalPages} onClick={() => onPage(page + 1)} aria-label="Next page">Next<ArrowRight aria-hidden="true" /></Button></div></div>
}

function readPage(value: string | null) { const page = Number(value); return Number.isSafeInteger(page) && page >= 0 && page <= 2_147_483_647 ? page : 0 }
function formatDate(value: string) { return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(value)) }
function messageFor(error: Error) { return error.message || 'Check your connection and try again.' }
