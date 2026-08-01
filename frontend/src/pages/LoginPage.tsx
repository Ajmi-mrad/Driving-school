import { useState } from 'react'
import { Navigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Car, Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Separator } from '@/components/ui/separator'
import { LanguageSwitcher } from '@/components/layout/LanguageSwitcher'
import { useAuth } from '@/core/auth/AuthContext'
import { ROLES } from '@/core/types'

export function LoginPage() {
  const { t } = useTranslation()
  const { status, loginAs } = useAuth()
  const [submitting, setSubmitting] = useState(false)

  if (status === 'authenticated') return <Navigate to="/" replace />

  // Mock: the form is illustrative; auth is triggered via the role buttons.
  // A real impl redirects to Keycloak (PKCE) here.
  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    setTimeout(() => loginAs('OWNER'), 400)
  }

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

        <form onSubmit={onSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="username">{t('auth.identifier')}</Label>
            <Input id="username" defaultValue="directeur" />
          </div>
          <div className="space-y-2">
            <Label htmlFor="password">{t('auth.password')}</Label>
            <Input id="password" type="password" placeholder="••••••••" defaultValue="demo" />
          </div>
          <Button type="submit" className="w-full" disabled={submitting}>
            {submitting && <Loader2 className="size-4 animate-spin" />}
            {t('auth.signIn')}
          </Button>
        </form>

        <div className="my-6 flex items-center gap-3">
          <Separator className="flex-1" />
          <span className="text-xs text-muted-foreground">{t('auth.demoChooseRole')}</span>
          <Separator className="flex-1" />
        </div>

        <div className="grid grid-cols-2 gap-2">
          {ROLES.map((role) => (
            <Button
              key={role}
              variant="outline"
              size="sm"
              onClick={() => loginAs(role)}
            >
              {t(`enums.role.${role}`)}
            </Button>
          ))}
        </div>
      </div>
    </div>
  )
}