import { describe, expect, it } from 'vitest'
import { formatBrl, parseAmount } from './money'

describe('payment amount strings', () => {
  it('accepts and preserves the exact NUMERIC(19,2) maximum', () => {
    const amount = parseAmount('99999999999999999.99')
    expect(amount).toBe('99999999999999999.99')
    expect(formatBrl(amount)).toContain('999.999.999.999.999,99')
  })

  it.each(['0', '0.00', '-1.00', '+1.00', '01.00', '1.001', '1e3', ' 1.00', '100000000000000000.00'])('rejects invalid wire value %s', (value) => {
    expect(() => parseAmount(value)).toThrow()
  })

  it('normalizes valid whole and one-place decimal input without floating point', () => {
    expect(parseAmount('1')).toBe('1.00')
    expect(parseAmount('1.2')).toBe('1.20')
  })
})
