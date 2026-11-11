import { useEffect, useRef } from 'react'
import { CreditCard, House, UsersRound } from 'lucide-react'
import { useNavigationType } from 'react-router'

const pages = {
  overview: {
    title: 'Overview',
    subtitle: 'A clear view of your simulated payment activity.',
    heading: 'Your overview is taking shape.',
    description: 'Payment summaries will appear here once the dashboard is connected. No financial data is available in this preview.',
    availability: 'Dashboard summaries are not available yet.',
    icon: House,
  },
  payments: {
    title: 'Payments',
    subtitle: 'Create and track simulated payments.',
    heading: 'A workspace for every payment.',
    description: 'Creation, tracking and payment history will be available once this workspace is connected.',
    availability: 'Payment processing is not available yet.',
    icon: CreditCard,
  },
  customers: {
    title: 'Customers',
    subtitle: 'The people behind your simulated payments.',
    heading: 'Your customers, in one place.',
    description: 'Customer creation and your customer list will be available once this workspace is connected.',
    availability: 'Customer management is not available yet.',
    icon: UsersRound,
  },
} as const

export default function PlannedPage({ page }: { page: keyof typeof pages }) {
  const { title, subtitle, heading, description, availability, icon: Icon } = pages[page]
  const headingRef = useRef<HTMLHeadingElement>(null)
  const navigationType = useNavigationType()
  useEffect(() => {
    if (navigationType !== 'POP') headingRef.current?.focus()
  }, [page, navigationType])
  return <>
    <header className="page-heading"><h1 ref={headingRef} tabIndex={-1}>{title}</h1><p>{subtitle}</p></header>
    <section className="unavailable-panel" aria-labelledby="availability-heading">
      <div className="empty-icon"><Icon aria-hidden="true" /></div>
      <h2 id="availability-heading">{heading}</h2>
      <p>{description}</p>
      <div className="availability"><span aria-hidden="true" />{availability}</div>
    </section>
    <p className="foundation-note">FlowPay is a personal project. This preview shows the workspace design; all payment operations are simulated.</p>
  </>
}
