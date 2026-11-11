import { zodResolver } from '@hookform/resolvers/zod'
import { ArrowRight, ShieldCheck } from 'lucide-react'
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { sessionKey } from '../../lib/query-client'
import { useForm, useWatch } from 'react-hook-form'
import type { Customer } from '../customers/api'
import { listCustomers } from '../customers/api'
import { Button } from '../../components/ui/button'
import { useCreatePayment } from './hooks'
import { applyServerFieldErrors } from './server-errors'
import { beginAttempt, editAttempt, samePayload } from './payment-attempt'
import type { PaymentAttempt, PaymentPayload } from './payment-attempt'
import { paymentFormSchema } from './schemas'
import type { PaymentFormData, PaymentFormValues } from './schemas'
import { formatBrl } from './money'

const customerPageSize = 20

export function CreatePaymentForm({ scope }: { scope: string }) {
  const mutation = useCreatePayment(scope)
  const [attempt, setAttempt] = useState<PaymentAttempt>()
  const [uncertain, setUncertain] = useState(false)
  const [success, setSuccess] = useState<string>()
  const [customerSearch, setCustomerSearch] = useState('')
  const [customerPage, setCustomerPage] = useState(0)
  const [selectedCustomer, setSelectedCustomer] = useState<Customer>()
  const customerOptions = useQuery({
    queryKey: sessionKey(scope, 'customer-options', { q: customerSearch, page: customerPage, size: customerPageSize }),
    queryFn: ({ signal }) => listCustomers({ page: customerPage, size: customerPageSize, q: customerSearch }, signal),
  })
  const customers = customerOptions.data?.items ?? []
  const form = useForm<PaymentFormValues, unknown, PaymentFormData>({
    resolver: zodResolver(paymentFormSchema),
    defaultValues: { customerId: '', amount: '', description: '', simulationScenario: 'APPROVE' },
  })
  const serverError = form.formState.errors.root?.server?.message
  const customerId = useWatch({ control: form.control, name: 'customerId' })
  const visibleCustomers = selectedCustomer && !customers.some((customer) => customer.id === selectedCustomer.id)
    ? [selectedCustomer, ...customers]
    : customers

  async function submit(data: PaymentFormData) {
    const payload: PaymentPayload = {
      customerId: data.customerId,
      amount: data.amount,
      currency: 'BRL',
      description: data.description || null,
      simulationScenario: data.simulationScenario,
    }
    const selectedAttempt = attempt
      ? samePayload(attempt.payload, payload) ? attempt : editAttempt(attempt, payload)
      : beginAttempt(payload)
    setAttempt(selectedAttempt)
    setSuccess(undefined)
    setUncertain(false)
    form.clearErrors('root')
    try {
      const payment = await mutation.mutateAsync({ payload: selectedAttempt.payload, key: selectedAttempt.key })
      setAttempt(undefined)
      form.reset()
      setSuccess(`Payment ${payment.id.slice(0, 8)} created as Pending for ${formatBrl(payment.amount)}.`)
    } catch (error) {
      const isUncertain = !(error instanceof Error && 'status' in error && typeof error.status === 'number' && error.status < 500)
        || (error instanceof Error && 'status' in error && typeof error.status === 'number' && error.status >= 500)
      setUncertain(isUncertain)
      form.setError('root.server', { type: 'server', message: applyServerFieldErrors(error, form.setError) })
    }
  }

  async function retrySameAttempt() {
    if (!attempt) return
    setUncertain(false)
    form.clearErrors('root')
    try {
      const payment = await mutation.mutateAsync({ payload: attempt.payload, key: attempt.key })
      setAttempt(undefined)
      form.reset()
      setSuccess(`Payment ${payment.id.slice(0, 8)} created as Pending for ${formatBrl(payment.amount)}.`)
    } catch (error) {
      const isUncertain = !(error instanceof Error && 'status' in error && typeof error.status === 'number' && error.status < 500)
        || (error instanceof Error && 'status' in error && typeof error.status === 'number' && error.status >= 500)
      setUncertain(isUncertain)
      form.setError('root.server', { type: 'server', message: applyServerFieldErrors(error, form.setError) })
    }
  }

  return <form className="flow-form" noValidate onSubmit={form.handleSubmit(submit)}>
    <div className="form-field">
      <label htmlFor="payment-customer-search">Find a customer</label>
      <input id="payment-customer-search" type="search" autoComplete="off" value={customerSearch} disabled={mutation.isPending} onChange={(event) => { setCustomerSearch(event.target.value); setCustomerPage(0) }} placeholder="Search name or email" aria-describedby="payment-customer-search-help" />
      <p className="field-help" id="payment-customer-search-help">Searches saved customers on the server. Each page is limited to {customerPageSize} matches.</p>
      {customerOptions.isPending && <p className="list-message" role="status">Searching customers…</p>}
      {customerOptions.isError && <p className="field-error" role="alert">Customers could not be loaded. Try another search or retry.</p>}
      {customerOptions.isSuccess && customerOptions.data.totalElements === 0 && <p className="list-message" role="status">No customers match this search.</p>}
      <label htmlFor="payment-customer">Customer</label>
      <select id="payment-customer" disabled={mutation.isPending || customerOptions.isPending || customerOptions.isError} aria-invalid={!!form.formState.errors.customerId} aria-describedby={form.formState.errors.customerId ? 'payment-customer-error' : undefined} {...form.register('customerId', {
        onChange: (event) => setSelectedCustomer(visibleCustomers.find((item) => item.id === event.target.value)),
      })}>
        <option value="">Choose a customer</option>
        {visibleCustomers.map((customer) => <option key={customer.id} value={customer.id}>{customer.name} · {customer.email}</option>)}
      </select>
      {form.formState.errors.customerId && <p className="field-error" id="payment-customer-error">{form.formState.errors.customerId.message}</p>}
      {customerOptions.isSuccess && customerOptions.data.totalPages > 1 && <div className="customer-result-pages"><span>Page {customerPage + 1} of {customerOptions.data.totalPages}</span><div>
        <Button type="button" variant="secondary" disabled={customerPage === 0 || mutation.isPending} onClick={() => setCustomerPage((page) => page - 1)}>Previous matches</Button>
        <Button type="button" variant="secondary" disabled={customerPage + 1 >= customerOptions.data.totalPages || mutation.isPending} onClick={() => setCustomerPage((page) => page + 1)}>More matches</Button>
      </div></div>}
    </div>
    <div className="form-field">
      <label htmlFor="payment-amount">Amount (BRL)</label>
      <input id="payment-amount" type="text" inputMode="decimal" autoComplete="off" placeholder="0.00" disabled={mutation.isPending} {...form.register('amount')} aria-invalid={!!form.formState.errors.amount} aria-describedby={form.formState.errors.amount ? 'payment-amount-error' : 'payment-amount-help'} />
      {form.formState.errors.amount ? <p className="field-error" id="payment-amount-error">{form.formState.errors.amount.message}</p> : <p className="field-help" id="payment-amount-help">Use a decimal point and up to two decimal places.</p>}
    </div>
    <div className="form-field">
      <label htmlFor="payment-description">Description <span className="optional-label">Optional</span></label>
      <input id="payment-description" type="text" disabled={mutation.isPending} {...form.register('description')} />
    </div>
    <fieldset className="scenario-field">
      <legend>Simulation outcome</legend>
      <label><input type="radio" value="APPROVE" disabled={mutation.isPending} {...form.register('simulationScenario')} />Simulate approval</label>
      <label><input type="radio" value="DECLINE" disabled={mutation.isPending} {...form.register('simulationScenario')} />Simulate decline</label>
      <p>The payment starts as Pending. This scenario determines its outcome when you process it.</p>
    </fieldset>
    {serverError && <p className="form-error" role="alert">{serverError}</p>}
    {success && <p className="form-success" role="status">{success}</p>}
    <Button type="submit" disabled={mutation.isPending || !customerId}>{mutation.isPending ? 'Creating payment…' : <>Create payment<ArrowRight aria-hidden="true" /></>}</Button>
    {uncertain && attempt && <Button type="button" variant="secondary" disabled={mutation.isPending} onClick={() => void retrySameAttempt()}>Retry same attempt</Button>}
    <p className="inline-simulation"><ShieldCheck aria-hidden="true" />Simulation only · no real money is moved.</p>
  </form>
}
