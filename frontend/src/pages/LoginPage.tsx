import { Navigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Car, LogIn } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { LanguageSwitcher } from '@/components/layout/LanguageSwitcher'
import { useAuth } from '@/core/auth/AuthContext'

export function LoginPage() {
  const { t } = useTranslation()
  const { status, signIn } = useAuth()

  if (status === 'authenticated') return <Navigate to="/" replace />

  return (
    <div className="relative flex min-h-svh items-center justify-center bg-muted/40 p-4">
      <div className="absolute end-4 top-4">
        <LanguageSwitcher />
      </div>
      <div className="w-full max-w-sm rounded-xl border bg-card p-8 shadow-sm">
        <div className="mb-6 flex flex-col items-center gap-2 text-center">
          <div className="flex size-12 items-center justify-center rounded-xl bg-primary text-primary-foreground">
            <Car className="size-6" />
          </div>
          <h1 className="text-xl font-semibold">{t('common.appName')}</h1>
          <p className="text-sm text-muted-foreground">
            {t('auth.connectYourSpace')}
          </p>
        </div>

        <Button className="w-full gap-2" onClick={signIn}>
          <LogIn className="size-4" />
          {t('auth.signIn')}
        </Button>
      </div>
    </div>
  )
}