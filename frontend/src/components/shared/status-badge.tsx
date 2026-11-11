const statusLabels = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
} as const

export type PaymentStatus = keyof typeof statusLabels

export function StatusBadge({ status }: { status: PaymentStatus }) {
  return <span className="status-badge" data-status={status}><span aria-hidden="true" />{statusLabels[status]}</span>
}
