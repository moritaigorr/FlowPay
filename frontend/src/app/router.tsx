import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes, useParams } from 'react-router'
import { NotFoundPage, RouteLoading } from '../components/shared/route-feedback'
import { WorkspaceShell } from './shell'
import { LoginPage } from '../features/auth/LoginPage'
import { RequireSession } from '../features/auth/RequireSession'

const CustomersPage = lazy(() => import('../features/customers/CustomersPage').then((module) => ({ default: module.CustomersPage })))
const PaymentsPage = lazy(() => import('../features/payments/PaymentsPage').then((module) => ({ default: module.PaymentsPage })))
const PaymentDetailsPage = lazy(() => import('../features/payments/PaymentDetailsPage').then((module) => ({ default: module.PaymentDetailsPage })))
const OverviewPage = lazy(() => import('../features/dashboard/OverviewPage').then((module) => ({ default: module.OverviewPage })))
const GuidedDemoPage = lazy(() => import('../features/demo/GuidedDemoPage').then((module) => ({ default: module.GuidedDemoPage })))

export function AppRoutes() {
  return <Routes>
    <Route path="login" element={<LoginPage />} />
    <Route element={<RequireSession />}>
      <Route element={<WorkspaceShell />}>
        <Route index element={<Navigate to="/payments" replace />} />
        <Route path="overview" element={<Suspense fallback={<RouteLoading />}><OverviewPage /></Suspense>} />
        <Route path="demo" element={<Suspense fallback={<RouteLoading />}><GuidedDemoPage /></Suspense>} />
        <Route path="payments" element={<Suspense fallback={<RouteLoading />}><PaymentsPage /></Suspense>} />
        <Route path="payments/:paymentId" element={<Suspense fallback={<RouteLoading />}><PaymentDetailsRoute /></Suspense>} />
        <Route path="customers" element={<Suspense fallback={<RouteLoading />}><CustomersPage /></Suspense>} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Route>
  </Routes>
}

function PaymentDetailsRoute() {
  const { paymentId } = useParams()
  if (!paymentId || !/^[\da-f]{8}-(?:[\da-f]{4}-){3}[\da-f]{12}$/i.test(paymentId)) return <NotFoundPage />
  return <PaymentDetailsPage />
}
