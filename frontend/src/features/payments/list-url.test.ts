import { describe, expect, it } from 'vitest'
import { updateListFilters } from './list-url'

describe('server-list URL filters', () => {
  it('resets the page when a filter changes while preserving unrelated filters', () => {
    const current = new URLSearchParams('page=4&size=20&q=Acme&status=PENDING')
    const next = updateListFilters(current, { status: 'APPROVED' })
    expect(next.get('page')).toBe('0')
    expect(next.get('size')).toBe('20')
    expect(next.get('q')).toBe('Acme')
    expect(next.get('status')).toBe('APPROVED')
  })

  it('removes empty filter values from the URL and resets pagination', () => {
    const next = updateListFilters(new URLSearchParams('page=2&status=REJECTED'), { status: '' })
    expect(next.get('page')).toBe('0')
    expect(next.has('status')).toBe(false)
  })
})
