import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import type { CustomerFormData, CustomerFormValues } from '../payments/schemas'
import { customerSchema } from '../payments/schemas'
import { useCreateCustomer } from './hooks'
import { applyServerFieldErrors } from '../payments/server-errors'
import { Button } from '../../components/ui/button'

export function CustomerForm({ scope, onCreated }: { scope: string; onCreated?: () => void }) {
  const mutation = useCreateCustomer(scope)
  const form = useForm<CustomerFormValues, unknown, CustomerFormData>({
    resolver: zodResolver(customerSchema),
    defaultValues: { name: '', email: '' },
  })
  const serverError = form.formState.errors.root?.server?.message

  async function submit(data: CustomerFormData) {
    form.clearErrors('root')
    try {
      await mutation.mutateAsync(data)
      form.reset()
      onCreated?.()
      form.setError('root.success', { type: 'success', message: `${data.name} is ready to use in a payment.` })
    } catch (error) {
      form.setError('root.server', { type: 'server', message: applyServerFieldErrors(error, form.setError) })
    }
  }

  return <form className="flow-form" noValidate onSubmit={form.handleSubmit(submit)}>
    <div className="form-field">
      <label htmlFor="customer-name">Customer name</label>
      <input id="customer-name" autoComplete="organization" {...form.register('name')} aria-invalid={!!form.formState.errors.name} aria-describedby={form.formState.errors.name ? 'customer-name-error' : undefined} />
      {form.formState.errors.name && <p className="field-error" id="customer-name-error">{form.formState.errors.name.message}</p>}
    </div>
    <div className="form-field">
      <label htmlFor="customer-email">Email address</label>
      <input id="customer-email" type="email" inputMode="email" autoComplete="email" {...form.register('email')} aria-invalid={!!form.formState.errors.email} aria-describedby={form.formState.errors.email ? 'customer-email-error' : undefined} />
      {form.formState.errors.email && <p className="field-error" id="customer-email-error">{form.formState.errors.email.message}</p>}
    </div>
    {serverError && <p className="form-error" role="alert">{serverError}</p>}
    {form.formState.errors.root?.success?.message && <p className="form-success" role="status">{form.formState.errors.root.success.message}</p>}
    <Button type="submit" disabled={mutation.isPending}>{mutation.isPending ? 'Saving customer…' : 'Save customer'}</Button>
  </form>
}
