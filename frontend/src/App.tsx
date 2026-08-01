import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { RequireAuth } from '@/components/RequireAuth'
import { RequireRole } from '@/components/RequireRole'
import { LoginPage } from '@/pages/LoginPage'
import { DashboardPage } from '@/pages/DashboardPage'
import { UsersPage } from '@/pages/UsersPage'
import { VehiclesPage } from '@/pages/VehiclesPage'
import { SessionsPage } from '@/pages/SessionsPage'
import { ForfaitsPage } from '@/pages/ForfaitsPage'
import { EnrollmentsPage } from '@/pages/EnrollmentsPage'
import { PaymentsPage } from '@/pages/PaymentsPage'
import { InvoicesPage } from '@/pages/InvoicesPage'
import { MessagesPage } from '@/pages/MessagesPage'
import { NotificationsPage } from '@/pages/NotificationsPage'
import { SettingsPage } from '@/pages/SettingsPage'
import { hasAnyRole, isStaff, permissions } from '@/core/auth/roles'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />

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
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App