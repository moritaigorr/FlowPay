import { QueryClient } from '@tanstack/react-query'
import { describe, expect, it, vi } from 'vitest'
import { sessionKey } from '../../lib/query-client'
import { invalidatePaymentViews } from './hooks'

describe('payment mutation invalidation', () => {
  it('refreshes list, summary, and recent payments for only the current session', async () => {
    const scope = 'company:operator-session'
    const client = new QueryClient()
    const invalidate = vi.spyOn(client, 'invalidateQueries').mockResolvedValue(undefined)

    await invalidatePaymentViews(client, scope)

    expect(invalidate).toHaveBeenCalledWith({ queryKey: sessionKey(scope, 'payments') })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: sessionKey(scope, 'dashboard-summary') })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: sessionKey(scope, 'dashboard-recent-payments') })
    expect(invalidate).toHaveBeenCalledTimes(3)
  })
})
