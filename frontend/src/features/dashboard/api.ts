import { apiRequest } from '../../lib/http'

export interface DashboardSummary {
  from: string
  to: string
  currency: 'BRL'
  approvedVolume: string
  pendingCount: number
  pendingAmount: string
  approvedCount: number
  rejectedCount: number
  totalPayments: number
  approvalRate: string | null
}

export interface SummaryPeriod { from: string; to: string }

export function getDashboardSummary(period: SummaryPeriod, signal?: AbortSignal) {
  const params = new URLSearchParams({ from: period.from, to: period.to })
  return apiRequest<DashboardSummary>(`/dashboard/summary?${params}`, { signal })
}
