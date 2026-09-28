import { Navigate } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import { useAuth } from '@/core/auth/AuthContext'
import { AppLayout } from './layout/AppLayout'

/** Route guard: renders the app shell for authenticated users, else /login. */
export function RequireAuth() {
  const { status } = useAuth()

  if (status === 'loading') {
    return (
      <div className="flex min-h-svh items-center justify-center">
        <Loader2 className="size-6 animate-spin text-muted-foreground" />
      </div>
    )
  }

  if (status === 'unauthenticated') {
    return <Navigate to="/login" replace />
  }

  return <AppLayout />
}