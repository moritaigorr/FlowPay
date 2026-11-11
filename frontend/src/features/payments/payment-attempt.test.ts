import { describe, expect, it } from 'vitest'
import { beginAttempt, editAttempt, retryAttempt } from './payment-attempt'

const payload = { customerId: 'customer-a', amount: '10.00', currency: 'BRL' as const, description: null, simulationScenario: 'APPROVE' as const }

describe('idempotent payment attempt snapshots', () => {
  it('retries the exact immutable key and payload after an uncertain outcome', () => {
    const first = beginAttempt(payload, () => 'key-a')
    const retry = retryAttempt(first)
    expect(retry).toBe(first)
    expect(retry).toEqual({ key: 'key-a', payload })
    expect(Object.isFrozen(first)).toBe(true)
    expect(Object.isFrozen(first.payload)).toBe(true)
  })

  it('starts a fresh logical attempt when the user edits the payload', () => {
    const first = beginAttempt(payload, () => 'key-a')
    const edited = editAttempt(first, { ...payload, amount: '11.00' }, () => 'key-b')
    expect(edited.key).toBe('key-b')
    expect(edited.payload.amount).toBe('11.00')
    expect(first.key).toBe('key-a')
    expect(first.payload.amount).toBe('10.00')
  })
})
