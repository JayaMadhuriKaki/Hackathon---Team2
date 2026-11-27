import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import AppLayout, { RequireAuth, RoleGate } from './components/AppLayout.jsx'
import { AuthProvider } from './context/AuthContext.jsx'
import { ToastProvider } from './context/ToastContext.jsx'
import ApprovalsQueuePage from './pages/ApprovalsQueuePage.jsx'
import ApproverReviewPage from './pages/ApproverReviewPage.jsx'
import AuditTrailPage from './pages/AuditTrailPage.jsx'
import ClaimDetailPage from './pages/ClaimDetailPage.jsx'
import ClaimEditorPage from './pages/ClaimEditorPage.jsx'
import DashboardPage from './pages/DashboardPage.jsx'
import DelegationsPage from './pages/DelegationsPage.jsx'
import LoginPage from './pages/LoginPage.jsx'
import MyClaimsPage from './pages/MyClaimsPage.jsx'
import NotFoundPage from './pages/NotFoundPage.jsx'
import PayoutBatchDetailPage from './pages/PayoutBatchDetailPage.jsx'
import PayoutBatchesPage from './pages/PayoutBatchesPage.jsx'
import PolicyRulesPage from './pages/PolicyRulesPage.jsx'
import ReviewQueuePage from './pages/ReviewQueuePage.jsx'

/** Route map (frontend/SPEC.md §2). Gates are UX only; the backend enforces every permission. */
export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route index element={<Navigate to="/claims" replace />} />
        <Route path="claims" element={<MyClaimsPage />} />
        <Route path="claims/new" element={<ClaimEditorPage />} />
        <Route path="claims/:claimId" element={<ClaimDetailPage />} />
        <Route path="claims/:claimId/edit" element={<ClaimEditorPage />} />
        <Route
          path="approvals"
          element={
            <RoleGate capabilities={['isApprover']}>
              <ApprovalsQueuePage />
            </RoleGate>
          }
        />
        <Route
          path="approvals/:claimId"
          element={
            <RoleGate capabilities={['isApprover']}>
              <ApproverReviewPage mode="approvals" />
            </RoleGate>
          }
        />
        <Route
          path="review-queue"
          element={
            <RoleGate roles={['FINANCE_AUDITOR']}>
              <ReviewQueuePage />
            </RoleGate>
          }
        />
        <Route
          path="review-queue/:claimId"
          element={
            <RoleGate roles={['FINANCE_AUDITOR']}>
              <ApproverReviewPage mode="review" />
            </RoleGate>
          }
        />
        <Route
          path="delegations"
          element={
            <RoleGate capabilities={['canDelegate']}>
              <DelegationsPage />
            </RoleGate>
          }
        />
        <Route
          path="payout-batches"
          element={
            <RoleGate capabilities={['isAdmin']}>
              <PayoutBatchesPage />
            </RoleGate>
          }
        />
        <Route
          path="payout-batches/:batchId"
          element={
            <RoleGate capabilities={['isAdmin']}>
              <PayoutBatchDetailPage />
            </RoleGate>
          }
        />
        <Route
          path="dashboard"
          element={
            <RoleGate roles={['FINANCE_AUDITOR']} capabilities={['isAdmin']}>
              <DashboardPage />
            </RoleGate>
          }
        />
        <Route
          path="audit-trail"
          element={
            <RoleGate capabilities={['isAdmin']}>
              <AuditTrailPage />
            </RoleGate>
          }
        />
        <Route
          path="policy-rules"
          element={
            <RoleGate capabilities={['isAdmin']}>
              <PolicyRulesPage />
            </RoleGate>
          }
        />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}

export default function App() {
  const navigate = useNavigate()
  const location = useLocation()
  // 401 anywhere: clear the session and go to /login?next=<path> (SPEC §3).
  const onSessionEnded = () => {
    const next = encodeURIComponent(`${location.pathname}${location.search}`)
    navigate(`/login?expired=1&next=${next}`, { replace: true })
  }
  return (
    <ToastProvider>
      <AuthProvider onSessionEnded={onSessionEnded}>
        <AppRoutes />
      </AuthProvider>
    </ToastProvider>
  )
}
