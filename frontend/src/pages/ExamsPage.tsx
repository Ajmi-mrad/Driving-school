import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Plus } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { DataTable, type Column } from '@/components/common/DataTable'
import { StatusBadge } from '@/components/common/StatusBadge'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useAuth } from '@/core/auth/AuthContext'
import { hasAnyRole, isStaff } from '@/core/auth/roles'
import { useAsync } from '@/core/hooks/useAsync'
import { useRevalidateOnFocus } from '@/hooks/useRevalidateOnFocus'
import { examsApi, usersApi, vehiclesApi, type ExamInput } from '@/core/api'
import {
  EXAM_OUTCOMES,
  EXAM_STATUSES,
  EXAM_TYPES,
  type Exam,
  type ExamOutcome,
  type ExamStatus,
  type ExamType,
} from '@/core/types'
import { fromDateTimeInput, toDateInput, toTimeInput } from '@/core/datetime'
import { fullName, formatDate, formatTime } from '@/core/format'
import { EXAM_STATUS_TONE } from '@/lib/tones'

export function ExamsPage() {
  const { t } = useTranslation()
  const { user, roles } = useAuth()
  const staff = isStaff(roles)
  const canRecord = hasAnyRole(roles, 'OWNER', 'SECRETARY', 'MONITOR')

  const [status, setStatus] = useState<ExamStatus | 'ALL'>('ALL')
  const [type, setType] = useState<ExamType | 'ALL'>('ALL')
  const [scheduleOpen, setScheduleOpen] = useState(false)
  const [resultFor, setResultFor] = useState<Exam | null>(null)
  const [rescheduleFor, setRescheduleFor] = useState<Exam | null>(null)
  const [cancelFor, setCancelFor] = useState<Exam | null>(null)

  const filter = useMemo(() => {
    if (staff) return {}
    if (roles.includes('MONITOR')) return { monitorId: user?.id }
    return { clientId: user?.id }
  }, [staff, roles, user?.id])

  const { data, loading, reload, refresh } = useAsync(
    () => examsApi.list(filter),
    [filter.monitorId, filter.clientId, staff],
  )
  useRevalidateOnFocus(refresh)
  // Name resolution needs the staff-only /users list, so only staff fetch it.
  const { data: users } = useAsync(
    () => (staff ? usersApi.list() : Promise.resolve([])),
    [staff],
  )

  const nameOf = useMemo(() => {
    const map = new Map((users ?? []).map((u) => [u.id, fullName(u)]))
    return (id?: string | null) => (id ? (map.get(id) ?? '') : '')
  }, [users])

  const filtered = useMemo(
    () =>
      (data ?? []).filter(
        (e) =>
          (status === 'ALL' || e.status === status) &&
          (type === 'ALL' || e.type === type),
      ),
    [data, status, type],
  )

  const columns: Column<Exam>[] = [
    {
      key: 'when',
      header: t('exams.date'),
      cell: (e) => (
        <div>
          <p className="font-medium">{formatDate(e.scheduledAt)}</p>
          <p className="text-xs text-muted-foreground">{formatTime(e.scheduledAt)}</p>
        </div>
      ),
    },
    {
      key: 'type',
      header: t('exams.type'),
      cell: (e) => t(`enums.examType.${e.type}`),
    },
    {
      key: 'client',
      header: t('exams.client'),
      cell: (e) => nameOf(e.clientId) || '—',
    },
    {
      key: 'attempt',
      header: t('exams.attempt'),
      cell: (e) => `#${e.attemptNumber}`,
    },
    {
      key: 'location',
      header: t('exams.location'),
      cell: (e) => e.location || '—',
    },
    {
      key: 'status',
      header: t('common.status'),
      cell: (e) => (
        <StatusBadge tone={EXAM_STATUS_TONE[e.status]}>
          {t(`enums.examStatus.${e.status}`)}
        </StatusBadge>
      ),
    },
    {
      key: 'actions',
      header: '',
      className: 'text-end',
      cell: (e) =>
        e.status === 'SCHEDULED' ? (
          <div className="flex justify-end gap-2">
            {canRecord && (
              <Button size="sm" variant="outline" onClick={() => setResultFor(e)}>
                {t('exams.recordResult')}
              </Button>
            )}
            {staff && (
              <Button size="sm" variant="outline" onClick={() => setRescheduleFor(e)}>
                {t('exams.reschedule')}
              </Button>
            )}
            {staff && (
              <Button size="sm" variant="ghost" onClick={() => setCancelFor(e)}>
                {t('common.cancel')}
              </Button>
            )}
          </div>
        ) : (
          e.resultNote || '—'
        ),
    },
  ]

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('nav.exams')}
        description={t('exams.subtitle')}
        actions={
          staff && (
            <Button onClick={() => setScheduleOpen(true)}>
              <Plus className="size-4" />
              {t('exams.new')}
            </Button>
          )
        }
      />

      <div className="flex gap-2">
        <Select value={status} onValueChange={(v) => setStatus(v as ExamStatus | 'ALL')}>
          <SelectTrigger className="w-44">
            <SelectValue placeholder={t('exams.filterStatus')} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">{t('common.all')}</SelectItem>
            {EXAM_STATUSES.map((s) => (
              <SelectItem key={s} value={s}>
                {t(`enums.examStatus.${s}`)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={type} onValueChange={(v) => setType(v as ExamType | 'ALL')}>
          <SelectTrigger className="w-36">
            <SelectValue placeholder={t('exams.filterType')} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">{t('common.all')}</SelectItem>
            {EXAM_TYPES.map((ty) => (
              <SelectItem key={ty} value={ty}>
                {t(`enums.examType.${ty}`)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <DataTable
        columns={columns}
        rows={filtered}
        loading={loading}
        emptyLabel={t('exams.empty')}
      />

      <ScheduleExamDialog open={scheduleOpen} onOpenChange={setScheduleOpen} onSaved={reload} />

      <RecordResultDialog
        exam={resultFor}
        onOpenChange={(o) => !o && setResultFor(null)}
        onSaved={reload}
      />

      <RescheduleExamDialog
        exam={rescheduleFor}
        onOpenChange={(o) => !o && setRescheduleFor(null)}
        onSaved={reload}
      />

      <ConfirmDialog
        open={cancelFor != null}
        onOpenChange={(o) => !o && setCancelFor(null)}
        title={t('exams.cancelTitle')}
        description={t('exams.cancelConfirm')}
        confirmLabel={t('exams.cancelAction')}
        destructive
        onConfirm={async () => {
          if (!cancelFor) return
          try {
            await examsApi.cancel(cancelFor.id)
            toast.success(t('exams.cancelledToast'))
            reload()
          } catch (err) {
            toast.error(err instanceof Error ? err.message : t('common.error'))
          }
        }}
      />
    </div>
  )
}

// --------------------------------------------------------------------------
// Schedule dialog (staff only)
// --------------------------------------------------------------------------
function ScheduleExamDialog({
  open,
  onOpenChange,
  onSaved,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSaved: () => void
}) {
  const { t } = useTranslation()

  const [type, setType] = useState<ExamType>('CODE')
  const [clientId, setClientId] = useState('')
  const [monitorId, setMonitorId] = useState('NONE')
  const [vehicleId, setVehicleId] = useState('NONE')
  const [date, setDate] = useState(toDateInput(new Date()))
  const [time, setTime] = useState('09:00')
  const [location, setLocation] = useState('')
  const [saving, setSaving] = useState(false)

  const { data: clients } = useAsync(
    () => (open ? usersApi.list('CLIENT') : Promise.resolve([])),
    [open],
  )
  const { data: monitors } = useAsync(
    () => (open ? usersApi.list('MONITOR') : Promise.resolve([])),
    [open],
  )
  const { data: vehicles } = useAsync(
    () => (open ? vehiclesApi.list({ status: 'AVAILABLE' }) : Promise.resolve([])),
    [open],
  )

  useEffect(() => {
    if (open) {
      setType('CODE')
      setClientId('')
      setMonitorId('NONE')
      setVehicleId('NONE')
      setDate(toDateInput(new Date()))
      setTime('09:00')
      setLocation('')
    }
  }, [open])

  const isDriving = type === 'DRIVING'
  const valid = clientId && date && time

  const submit = async () => {
    if (!valid) return
    setSaving(true)
    const payload: ExamInput = {
      type,
      clientId,
      monitorId: monitorId !== 'NONE' ? monitorId : null,
      vehicleId: isDriving && vehicleId !== 'NONE' ? vehicleId : null,
      scheduledAt: fromDateTimeInput(date, time).toISOString(),
      location: location.trim() || null,
    }
    try {
      await examsApi.create(payload)
      toast.success(t('exams.createdToast'))
      onSaved()
      onOpenChange(false)
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('common.error'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90svh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{t('exams.scheduleTitle')}</DialogTitle>
        </DialogHeader>

        <div className="grid gap-4 py-2">
          <div className="space-y-1.5">
            <Label>{t('exams.type')}</Label>
            <Select value={type} onValueChange={(v) => setType(v as ExamType)}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {EXAM_TYPES.map((ty) => (
                  <SelectItem key={ty} value={ty}>
                    {t(`enums.examType.${ty}`)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-1.5">
            <Label>{t('exams.client')}</Label>
            <Select value={clientId} onValueChange={setClientId}>
              <SelectTrigger>
                <SelectValue placeholder={t('exams.selectClient')} />
              </SelectTrigger>
              <SelectContent>
                {(clients ?? []).map((c) => (
                  <SelectItem key={c.id} value={c.id}>
                    {fullName(c)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label>{t('exams.monitor')}</Label>
              <Select value={monitorId} onValueChange={setMonitorId}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="NONE">{t('exams.noMonitor')}</SelectItem>
                  {(monitors ?? []).map((m) => (
                    <SelectItem key={m.id} value={m.id}>
                      {fullName(m)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            {isDriving && (
              <div className="space-y-1.5">
                <Label>{t('exams.vehicle')}</Label>
                <Select value={vehicleId} onValueChange={setVehicleId}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="NONE">{t('exams.noVehicle')}</SelectItem>
                    {(vehicles ?? []).map((v) => (
                      <SelectItem key={v.id} value={v.id}>
                        {v.brand} {v.model} · {v.registrationNumber}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            )}
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label>{t('exams.date')}</Label>
              <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
            </div>
            <div className="space-y-1.5">
              <Label>{t('exams.time')}</Label>
              <Input type="time" value={time} onChange={(e) => setTime(e.target.value)} />
            </div>
          </div>

          <div className="space-y-1.5">
            <Label>{t('exams.location')}</Label>
            <Input
              value={location}
              onChange={(e) => setLocation(e.target.value)}
              placeholder={t('exams.locationPlaceholder')}
            />
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('common.cancel')}
          </Button>
          <Button onClick={submit} disabled={!valid || saving}>
            {t('common.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

// --------------------------------------------------------------------------
// Record-result dialog (staff / monitor)
// --------------------------------------------------------------------------
function RecordResultDialog({
  exam,
  onOpenChange,
  onSaved,
}: {
  exam: Exam | null
  onOpenChange: (open: boolean) => void
  onSaved: () => void
}) {
  const { t } = useTranslation()
  const [outcome, setOutcome] = useState<ExamOutcome>('PASSED')
  const [note, setNote] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (exam) {
      setOutcome('PASSED')
      setNote('')
    }
  }, [exam])

  const submit = async () => {
    if (!exam) return
    setSaving(true)
    try {
      await examsApi.recordResult(exam.id, outcome, note.trim() || undefined)
      toast.success(t('exams.resultToast'))
      onSaved()
      onOpenChange(false)
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('common.error'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={exam != null} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t('exams.resultTitle')}</DialogTitle>
        </DialogHeader>
        <div className="grid gap-4 py-2">
          <div className="space-y-1.5">
            <Label>{t('exams.outcome')}</Label>
            <Select value={outcome} onValueChange={(v) => setOutcome(v as ExamOutcome)}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {EXAM_OUTCOMES.map((o) => (
                  <SelectItem key={o} value={o}>
                    {t(`enums.examStatus.${o}`)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-1.5">
            <Label>{t('exams.resultNote')}</Label>
            <Textarea value={note} onChange={(e) => setNote(e.target.value)} rows={2} />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('common.cancel')}
          </Button>
          <Button onClick={submit} disabled={saving}>
            {t('common.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

// --------------------------------------------------------------------------
// Reschedule dialog (staff)
// --------------------------------------------------------------------------
function RescheduleExamDialog({
  exam,
  onOpenChange,
  onSaved,
}: {
  exam: Exam | null
  onOpenChange: (open: boolean) => void
  onSaved: () => void
}) {
  const { t } = useTranslation()
  const [date, setDate] = useState(toDateInput(new Date()))
  const [time, setTime] = useState('09:00')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (exam) {
      const d = new Date(exam.scheduledAt)
      setDate(toDateInput(d))
      setTime(toTimeInput(d))
    }
  }, [exam])

  const submit = async () => {
    if (!exam || !date || !time) return
    setSaving(true)
    try {
      await examsApi.reschedule(exam.id, fromDateTimeInput(date, time).toISOString())
      toast.success(t('exams.rescheduledToast'))
      onSaved()
      onOpenChange(false)
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('common.error'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={exam != null} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t('exams.rescheduleTitle')}</DialogTitle>
        </DialogHeader>
        <div className="grid grid-cols-2 gap-3 py-2">
          <div className="space-y-1.5">
            <Label>{t('exams.date')}</Label>
            <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
          </div>
          <div className="space-y-1.5">
            <Label>{t('exams.time')}</Label>
            <Input type="time" value={time} onChange={(e) => setTime(e.target.value)} />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('common.cancel')}
          </Button>
          <Button onClick={submit} disabled={saving}>
            {t('common.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
