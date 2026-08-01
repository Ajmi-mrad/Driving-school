import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ChevronLeft, ChevronRight, Plus } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { DataTable, type Column } from '@/components/common/DataTable'
import { StatusBadge } from '@/components/common/StatusBadge'
import { Button } from '@/components/ui/button'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { WeekCalendar } from '@/components/sessions/WeekCalendar'
import { SessionFormDialog } from '@/components/sessions/SessionFormDialog'
import { SessionDetailDialog } from '@/components/sessions/SessionDetailDialog'
import { useAuth } from '@/core/auth/AuthContext'
import { isStaff, permissions } from '@/core/auth/roles'
import { useAsync } from '@/core/hooks/useAsync'
import { sessionsApi, usersApi, vehiclesApi } from '@/core/api'
import {
  SESSION_STATUSES,
  SESSION_TYPES,
  type Session,
  type SessionStatus,
  type SessionType,
} from '@/core/types'
import { addDays, startOfWeek, weekDays } from '@/core/datetime'
import { fullName, formatDate, formatMonthYear, formatTime } from '@/core/format'
import { SESSION_STATUS_TONE } from '@/lib/tones'

export function SessionsPage() {
  const { t } = useTranslation()
  const { user, roles } = useAuth()
  const staff = isStaff(roles)
  const canBook = permissions.bookSessions(roles)

  const [week, setWeek] = useState(() => startOfWeek(new Date()))
  const [status, setStatus] = useState<SessionStatus | 'ALL'>('ALL')
  const [type, setType] = useState<SessionType | 'ALL'>('ALL')
  const [formOpen, setFormOpen] = useState(false)
  const [selected, setSelected] = useState<Session | null>(null)

  const filter = useMemo(() => {
    if (staff) return {}
    if (roles.includes('MONITOR')) return { monitorId: user?.id }
    return { clientId: user?.id }
  }, [staff, roles, user?.id])

  const { data, loading, reload } = useAsync(
    () => sessionsApi.list(filter),
    [filter.monitorId, filter.clientId, staff],
  )
  const { data: users } = useAsync(() => usersApi.list(), [])
  const { data: vehicles } = useAsync(() => vehiclesApi.list(), [])

  const nameOf = useMemo(() => {
    const map = new Map((users ?? []).map((u) => [u.id, fullName(u)]))
    return (id?: string | null) => (id ? (map.get(id) ?? '') : '')
  }, [users])

  const vehicleOf = useMemo(() => {
    const map = new Map(
      (vehicles ?? []).map((v) => [v.id, `${v.brand} ${v.model}`]),
    )
    return (id?: string | null) => (id ? (map.get(id) ?? '') : '')
  }, [vehicles])

  const filtered = useMemo(
    () =>
      (data ?? []).filter(
        (s) =>
          (status === 'ALL' || s.status === status) &&
          (type === 'ALL' || s.type === type),
      ),
    [data, status, type],
  )

  const label = (s: Session) =>
    s.type === 'DRIVING'
      ? `${nameOf(s.clientId)}${nameOf(s.monitorId) ? ` · ${nameOf(s.monitorId)}` : ''}`
      : nameOf(s.clientId)

  const days = weekDays(week)
  const weekLabel = formatMonthYear(week)

  const columns: Column<Session>[] = [
    {
      key: 'when',
      header: t('sessions.date'),
      cell: (s) => (
        <div>
          <p className="font-medium">{formatDate(s.startTime)}</p>
          <p className="text-xs text-muted-foreground">
            {formatTime(s.startTime)} – {formatTime(s.endTime)}
          </p>
        </div>
      ),
    },
    {
      key: 'type',
      header: t('sessions.type'),
      cell: (s) => t(`enums.sessionType.${s.type}`),
    },
    {
      key: 'client',
      header: t('sessions.client'),
      cell: (s) => nameOf(s.clientId),
    },
    {
      key: 'monitor',
      header: t('sessions.monitor'),
      cell: (s) => nameOf(s.monitorId) || '—',
    },
    {
      key: 'status',
      header: t('common.status'),
      cell: (s) => (
        <StatusBadge tone={SESSION_STATUS_TONE[s.status]}>
          {t(`enums.sessionStatus.${s.status}`)}
        </StatusBadge>
      ),
    },
  ]

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('nav.sessions')}
        description={t('sessions.subtitle')}
        actions={
          canBook && (
            <Button onClick={() => setFormOpen(true)}>
              <Plus className="size-4" />
              {t('sessions.new')}
            </Button>
          )
        }
      />

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex gap-2">
          <Select value={status} onValueChange={(v) => setStatus(v as SessionStatus | 'ALL')}>
            <SelectTrigger className="w-40">
              <SelectValue placeholder={t('sessions.filterStatus')} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">{t('common.all')}</SelectItem>
              {SESSION_STATUSES.map((s) => (
                <SelectItem key={s} value={s}>
                  {t(`enums.sessionStatus.${s}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select value={type} onValueChange={(v) => setType(v as SessionType | 'ALL')}>
            <SelectTrigger className="w-36">
              <SelectValue placeholder={t('sessions.filterType')} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">{t('common.all')}</SelectItem>
              {SESSION_TYPES.map((ty) => (
                <SelectItem key={ty} value={ty}>
                  {t(`enums.sessionType.${ty}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      <Tabs defaultValue="calendar">
        <TabsList>
          <TabsTrigger value="calendar">{t('sessions.viewCalendar')}</TabsTrigger>
          <TabsTrigger value="list">{t('sessions.viewList')}</TabsTrigger>
        </TabsList>

        <TabsContent value="calendar" className="space-y-3">
          <div className="flex items-center gap-2">
            <Button variant="outline" size="icon" onClick={() => setWeek(addDays(week, -7))}>
              <ChevronLeft className="size-4 rtl:rotate-180" />
            </Button>
            <Button variant="outline" size="icon" onClick={() => setWeek(addDays(week, 7))}>
              <ChevronRight className="size-4 rtl:rotate-180" />
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setWeek(startOfWeek(new Date()))}
            >
              {t('sessions.today')}
            </Button>
            <span className="ms-2 text-sm font-medium capitalize">{weekLabel}</span>
          </div>
          <WeekCalendar
            days={days}
            sessions={filtered}
            onSelect={setSelected}
            label={label}
          />
        </TabsContent>

        <TabsContent value="list">
          <DataTable
            columns={columns}
            rows={filtered}
            loading={loading}
            emptyLabel={t('sessions.empty')}
            onRowClick={setSelected}
          />
        </TabsContent>
      </Tabs>

      <SessionFormDialog open={formOpen} onOpenChange={setFormOpen} onSaved={reload} />

      <SessionDetailDialog
        session={selected}
        onOpenChange={(o) => !o && setSelected(null)}
        onChanged={reload}
        resolveName={nameOf}
        resolveVehicle={vehicleOf}
      />
    </div>
  )
}
