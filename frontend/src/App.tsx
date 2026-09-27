import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { RequireAuth } from '@/components/RequireAuth'
import { RequireRole } from '@/components/RequireRole'
import { LoginPage } from '@/pages/LoginPage'
import { AuthCallbackPage } from '@/pages/AuthCallbackPage'
import { DashboardPage } from '@/pages/DashboardPage'
import { UsersPage } from '@/pages/UsersPage'
import { VehiclesPage } from '@/pages/VehiclesPage'
import { SessionsPage } from '@/pages/SessionsPage'
import { AvailabilityPage } from '@/pages/AvailabilityPage'
import { ExamsPage } from '@/pages/ExamsPage'
import { ForfaitsPage } from '@/pages/ForfaitsPage'
import { EnrollmentsPage } from '@/pages/EnrollmentsPage'
import { PaymentsPage } from '@/pages/PaymentsPage'
import { InvoicesPage } from '@/pages/InvoicesPage'
import { MessagesPage } from '@/pages/MessagesPage'
import { NotificationsPage } from '@/pages/NotificationsPage'
import { SettingsPage } from '@/pages/SettingsPage'
import { AuditPage } from '@/pages/AuditPage'
import { AdminDataPage } from '@/pages/AdminDataPage'
import { OpsPage } from '@/pages/OpsPage'
import { hasAnyRole, isOwner, isStaff, permissions } from '@/core/auth/roles'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/auth/callback" element={<AuthCallbackPage />} />

        <Route element={<RequireAuth />}>
          <Route index element={<DashboardPage />} />

          <Route element={<RequireRole allow={isStaff} />}>
            <Route path="/users" element={<UsersPage />} />
          </Route>
          <Route
            element={
              <RequireRole
                allow={(r) => hasAnyRole(r, 'OWNER', 'SECRETARY', 'MONITOR')}
              />
            }
          >
            <Route path="/vehicles" element={<VehiclesPage />} />
          </Route>

          <Route path="/sessions" element={<SessionsPage />} />
          <Route
            element={
              <RequireRole allow={(r) => hasAnyRole(r, 'OWNER', 'SECRETARY', 'MONITOR')} />
            }
          >
            <Route path="/availability" element={<AvailabilityPage />} />
          </Route>
          <Route path="/exams" element={<ExamsPage />} />
          <Route path="/forfaits" element={<ForfaitsPage />} />

          <Route
            element={
              <RequireRole allow={(r) => hasAnyRole(r, 'OWNER', 'SECRETARY', 'CLIENT')} />
            }
          >
            <Route path="/enrollments" element={<EnrollmentsPage />} />
            <Route path="/payments" element={<PaymentsPage />} />
            <Route path="/invoices" element={<InvoicesPage />} />
          </Route>
          <Route element={<RequireRole allow={permissions.chat} />}>
            <Route path="/messages" element={<MessagesPage />} />
          </Route>
          <Route path="/notifications" element={<NotificationsPage />} />
          <Route element={<RequireRole allow={isStaff} />}>
            <Route path="/settings" element={<SettingsPage />} />
          </Route>
          <Route element={<RequireRole allow={isOwner} />}>
            <Route path="/audit" element={<AuditPage />} />
            <Route path="/admin/data" element={<AdminDataPage />} />
            <Route path="/ops" element={<OpsPage />} />
          </Route>
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App