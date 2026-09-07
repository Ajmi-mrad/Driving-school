import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { AlertTriangle, Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/core/auth/AuthContext'

/** Handles the Keycloak redirect: exchanges the auth code, then goes home. */
export function AuthCallbackPage() {
  const { t } = useTranslation()
  const { completeSignin } = useAuth()
  const navigate = useNavigate()
  const ran = useRef(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    // Guard against StrictMode's double-invoke: the auth code is single-use.
    if (ran.current) return
    ran.current = true
    completeSignin()
      .then(() => navigate('/', { replace: true }))
      .catch((e: unknown) =>
        setError(e instanceof Error ? e.message : t('auth.signInError')),
      )
  }, [completeSignin, navigate, t])

  if (error) {
    return (
      <div className="flex min-h-svh flex-col items-center justify-center gap-4 p-4 text-center">
        <AlertTriangle className="size-8 text-destructive" />
        <p className="text-sm text-muted-foreground">{error}</p>
        <Button onClick={() => navigate('/login', { replace: true })}>
          {t('auth.signIn')}
        </Button>
      </div>
    )
  }

  return <LoadingScreen />
}

function LoadingScreen() {
  return (
    <div className="flex min-h-svh items-center justify-center">
      <Loader2 className="size-6 animate-spin text-muted-foreground" />
    </div>
  )
}