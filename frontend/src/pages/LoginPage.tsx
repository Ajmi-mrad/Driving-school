import { Navigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Car, CalendarDays, CreditCard, LogIn, ShieldCheck } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { LanguageSwitcher } from '@/components/layout/LanguageSwitcher'
import { ModeToggle } from '@/components/layout/ModeToggle'
import { useAuth } from '@/core/auth/AuthContext'

export function LoginPage() {
  const { t } = useTranslation()
  const { status, signIn } = useAuth()

  if (status === 'authenticated') return <Navigate to="/" replace />

  const highlights = [
    { icon: CalendarDays, text: t('login.highlightSessions') },
    { icon: CreditCard, text: t('login.highlightPayments') },
    { icon: Car, text: t('login.highlightFleet') },
  ]

  return (
    <div className="grid min-h-svh lg:grid-cols-2">
      {/* Brand panel */}
      <div className="relative hidden flex-col justify-between overflow-hidden bg-primary p-10 text-primary-foreground lg:flex">
        <div
          className="pointer-events-none absolute inset-0 opacity-20"
          style={{
            backgroundImage:
              'radial-gradient(circle at 20% 20%, white 0, transparent 40%), radial-gradient(circle at 80% 60%, white 0, transparent 35%)',
          }}
        />
        <div className="relative flex items-center gap-2 text-lg font-semibold">
          <div className="flex size-9 items-center justify-center rounded-lg bg-primary-foreground/15">
            <Car className="size-5" />
          </div>
          {t('common.appName')}
        </div>

        <div className="relative space-y-6">
          <h2 className="max-w-md text-3xl font-bold leading-tight">{t('login.tagline')}</h2>
          <ul className="space-y-3">
            {highlights.map(({ icon: Icon, text }) => (
              <li key={text} className="flex items-center gap-3 text-primary-foreground/90">
                <span className="flex size-8 items-center justify-center rounded-full bg-primary-foreground/15">
                  <Icon className="size-4" />
                </span>
                {text}
              </li>
            ))}
          </ul>
        </div>

        <div className="relative flex items-center gap-2 text-sm text-primary-foreground/70">
          <ShieldCheck className="size-4" />
          {t('common.appName')} · {t('common.appSubtitle')}
        </div>
      </div>

      {/* Sign-in panel */}
      <div className="relative flex items-center justify-center p-6 sm:p-10">
        <div className="absolute end-4 top-4 flex items-center gap-1">
          <LanguageSwitcher />
          <ModeToggle />
        </div>

        <div className="w-full max-w-sm">
          <div className="mb-8 flex flex-col items-center gap-2 text-center lg:hidden">
            <div className="flex size-12 items-center justify-center rounded-xl bg-primary text-primary-foreground">
              <Car className="size-6" />
            </div>
            <h1 className="text-xl font-semibold">{t('common.appName')}</h1>
          </div>

          <div className="space-y-2 text-center">
            <h1 className="text-2xl font-bold tracking-tight">{t('login.welcome')}</h1>
            <p className="text-sm text-muted-foreground">{t('auth.connectYourSpace')}</p>
          </div>

          <Button className="mt-8 w-full gap-2" size="lg" onClick={signIn}>
            <LogIn className="size-4" />
            {t('auth.signIn')}
          </Button>
        </div>
      </div>
    </div>
  )
}
