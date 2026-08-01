import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '@/core/auth/AuthContext'
import type { Role } from '@/core/types'

/** Nested-route guard: renders children only if the role predicate passes. */
export function RequireRole({ allow }: { allow: (roles: Role[]) => boolean }) {
  const { roles } = useAuth()
  return allow(roles) ? <Outlet /> : <Navigate to="/" replace />
}
