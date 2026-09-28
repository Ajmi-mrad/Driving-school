import {
  CalendarDays,
  Car,
  Clock,
  GraduationCap,
  TriangleAlert,
  Wallet,
} from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { PageHeader } from '@/components/common/PageHeader'
import { StatCard } from '@/components/common/StatCard'
import { SessionsAreaChart, FleetStatusChart } from '@/components/dashboard/DashboardCharts'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuth } from '@/core/auth/AuthContext'
import { useAsync } from '@/core/hooks/useAsync'
import { enrollmentsApi, sessionsApi, usersApi, vehiclesApi } from '@/core/api'
import { isStaff } from '@/core/auth/roles'
import { formatCurrency, formatShortDateTime } from '@/core/format'
import { isToday } from '@/core/datetime'
import type { Enrollment, Session, Vehicle } from '@/core/types'

function StaffDashboard() {
  const { t } = useTranslation()
  const { data, loading } = useAsync(async () => {
    const [clients, sessions, vehicles, enrollments] = await Promise.all([
      usersApi.list('CLIENT'),
      sessionsApi.list(),
      vehiclesApi.list(),
      enrollmentsApi.list(),
    ])
    return { clients, sessions, vehicles, enrollments }
  })

  if (loading || !data) return <StatGridSkeleton />

  const activeStudents = data.clients.filter((c) => c.active).length
  const sessionsToday = data.sessions.filter(
    (s: Session) => isToday(new Date(s.startTime)) && s.status !== 'CANCELLED',
  ).length
  const available = data.vehicles.filter((v: Vehicle) => v.status === 'AVAILABLE').length
  const outstanding = data.enrollments.reduce(
    (sum: number, e: Enrollment) => sum + e.outstanding,
    0,
  )

  return (
    <div className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label={t('dashboard.activeStudents')} value={activeStudents} icon={GraduationCap} />
        <StatCard label={t('dashboard.sessionsToday')} value={sessionsToday} icon={CalendarDays} />
        <StatCard
          label={t('dashboard.availableVehicles')}
          value={`${available}/${data.vehicles.length}`}
          icon={Car}
          tone="success"
        />
        <StatCard
          label={t('dashboard.unpaid')}
          value={formatCurrency(outstanding)}
          icon={Wallet}
          tone={outstanding > 0 ? 'danger' : 'success'}
        />
      </div>
      <div className="grid gap-4 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <SessionsAreaChart sessions={data.sessions} />
        </div>
        <FleetStatusChart vehicles={data.vehicles} />
      </div>
    </div>
  )
}

function ClientDashboard({ clientId }: { clientId: string }) {
  const { t } = useTranslation()
  const { data, loading } = useAsync(async () => {
    const [sessions, enrollments] = await Promise.all([
      sessionsApi.list({ clientId }),
      enrollmentsApi.list(clientId),
    ])
    return { sessions, enrollments }
  }, [clientId])

  if (loading || !data) return <StatGridSkeleton />

  const upcoming = data.sessions
    .filter((s) => new Date(s.startTime) > new Date() && s.status !== 'CANCELLED')
    .sort((a, b) => a.startTime.localeCompare(b.startTime))[0]
  const hours = data.enrollments.reduce((s, e) => s + e.remainingDrivingHours, 0)
  const code = data.enrollments.reduce((s, e) => s + e.remainingCodeSessions, 0)
  const outstanding = data.enrollments.reduce((s, e) => s + e.outstanding, 0)

  return (
    <div className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard
          label={t('dashboard.nextSession')}
          value={upcoming ? formatShortDateTime(upcoming.startTime) : '—'}
          icon={Clock}
        />
        <StatCard
          label={t('dashboard.remainingDrivingHours')}
          value={hours}
          icon={Car}
          tone="success"
        />
        <StatCard label={t('dashboard.remainingCodeSessions')} value={code} icon={CalendarDays} />
        <StatCard
          label={t('dashboard.balanceDue')}
          value={formatCurrency(outstanding)}
          icon={outstanding > 0 ? TriangleAlert : Wallet}
          tone={outstanding > 0 ? 'danger' : 'success'}
        />
      </div>
      <SessionsAreaChart sessions={data.sessions} />
    </div>
  )
}

function MonitorDashboard({ monitorId }: { monitorId: string }) {
  const { t } = useTranslation()
  const { data, loading } = useAsync(
    () => sessionsApi.list({ monitorId }),
    [monitorId],
  )
  if (loading || !data) return <StatGridSkeleton />

  const today = data.filter(
    (s) => isToday(new Date(s.startTime)) && s.status !== 'CANCELLED',
  ).length
  const pending = data.filter((s) => s.status === 'PENDING').length

  return (
    <div className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label={t('dashboard.sessionsToday')} value={today} icon={CalendarDays} />
        <StatCard
          label={t('dashboard.toConfirm')}
          value={pending}
          icon={Clock}
          tone={pending > 0 ? 'warning' : 'success'}
        />
      </div>
      <SessionsAreaChart sessions={data} />
    </div>
  )
}

function StatGridSkeleton() {
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      {Array.from({ length: 4 }).map((_, i) => (
        <Skeleton key={i} className="h-24 rounded-xl" />
      ))}
    </div>
  )
}

export function DashboardPage() {
  const { t } = useTranslation()
  const { user, roles } = useAuth()
  if (!user) return null

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('dashboard.greeting', { name: user.firstName })}
        description={t('dashboard.overview')}
      />
      {isStaff(roles) ? (
        <StaffDashboard />
      ) : roles.includes('MONITOR') ? (
        <MonitorDashboard monitorId={user.id} />
      ) : (
        <ClientDashboard clientId={user.id} />
      )}
    </div>
  )
}