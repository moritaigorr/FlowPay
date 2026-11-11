import { z } from 'zod'
import { parseAmount } from './money'

const codePoints = (value: string) => Array.from(value).length

const normalizedEmailSchema = z.string().transform((value) => value.trim().toLowerCase()).pipe(z.email('Enter a valid email address.'))
  .refine((value) => codePoints(value) <= 254, 'Email must be at most 254 characters.')

export const customerSchema = z.object({
  name: z.string().transform((value) => value.trim()).refine((value) => codePoints(value) > 0, 'Name is required.')
    .refine((value) => codePoints(value) <= 120, 'Name must be at most 120 characters.'),
  email: normalizedEmailSchema,
})

const amountInputSchema = z.string().refine((value) => {
  try { parseAmount(value); return true } catch { return false }
}, 'Enter a positive amount with up to two decimal places, up to R$ 99,999,999,999,999,999.99.').transform(parseAmount)

export const paymentFormSchema = z.object({
  customerId: z.uuid('Select a customer.'),
  amount: amountInputSchema,
  description: z.string().transform((value) => value.trim()).refine((value) => codePoints(value) <= 500, 'Description must be at most 500 characters.'),
  simulationScenario: z.enum(['APPROVE', 'DECLINE']),
})

export type CustomerFormValues = z.input<typeof customerSchema>
export type CustomerFormData = z.output<typeof customerSchema>
export type PaymentFormValues = z.input<typeof paymentFormSchema>
export type PaymentFormData = z.output<typeof paymentFormSchema>
