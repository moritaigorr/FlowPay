export interface PaymentPayload {
  customerId: string
  amount: string
  currency: 'BRL'
  description: string | null
  simulationScenario: 'APPROVE' | 'DECLINE'
}

export interface PaymentAttempt {
  readonly key: string
  readonly payload: Readonly<PaymentPayload>
}

export function beginAttempt(payload: PaymentPayload, createKey: () => string = () => crypto.randomUUID()): PaymentAttempt {
  return Object.freeze({ key: createKey(), payload: Object.freeze({ ...payload }) })
}

export function retryAttempt(attempt: PaymentAttempt): PaymentAttempt {
  return attempt
}

export function editAttempt(_attempt: PaymentAttempt, payload: PaymentPayload, createKey: () => string = () => crypto.randomUUID()): PaymentAttempt {
  return beginAttempt(payload, createKey)
}

export function samePayload(left: PaymentPayload, right: PaymentPayload): boolean {
  return left.customerId === right.customerId && left.amount === right.amount && left.currency === right.currency
    && left.description === right.description && left.simulationScenario === right.simulationScenario
}
