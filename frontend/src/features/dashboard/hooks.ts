import { useQuery } from '@tanstack/react-query'
import { sessionKey } from '../../lib/query-client'
import { getDashboardSummary, type SummaryPeriod } from './api'
import { listPayments } from '../payments/api'

export function useDashboardSummary(scope: string, period: SummaryPeriod) {
  return useQuery({
    queryKey: sessionKey(scope, 'dashboard-summary', period),
    queryFn: ({ signal }) => getDashboardSummary(period, signal),
  })
}

export function useRecentPayments(scope: string, period: SummaryPeriod) {
  return useQuery({
    queryKey: sessionKey(scope, 'dashboard-recent-payments', period),
    queryFn: ({ signal }) => listPayments({
      page: 0, size: 5, q: '', createdFrom: period.from, createdTo: period.to,
    }, signal),
  })
}
