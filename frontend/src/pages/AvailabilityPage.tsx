import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Plus, Save, Trash2 } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { DataTable, type Column } from '@/components/common/DataTable'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
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
import { isStaff } from '@/core/auth/roles'
import { useAsync } from '@/core/hooks/useAsync'
import { useRevalidateOnFocus } from '@/hooks/useRevalidateOnFocus'
import { monitorsApi, usersApi, type TimeOffInput } from '@/core/api'
import { DAYS_OF_WEEK, type AvailabilityRule, type DayOfWeek, type TimeOff } from '@/core/types'
import { fromDateTimeInput, toDateInput } from '@/core/datetime'
import { fullName, formatDateTime } from '@/core/format'

/** "09:00:00" | "09:00" -> "09:00" for <input type="time">. */
function hhmm(value: string): string {
  return value.slice(0, 5)
}

/** "09:00" -> "09:00:00" for the API (LocalTime). */
function withSeconds(value: string): string {
  return value.length === 5 ? `${value}:00` : value
}

export function AvailabilityPage() {
  const { t } = useTranslation()
  const { user, roles } = useAuth()
  const staff = isStaff(roles)

  // Staff pick a monitor; a monitor manages only their own availability.
  const { data: monitors } = useAsync(
    () => (staff ? usersApi.list('MONITOR') : Promise.resolve([])),
    [staff],
  )
  const [selectedId, setSelectedId] = useState<string>('')
  const monitorId = staff ? selectedId : (user?.id ?? '')

  // Default the staff selector to the first monitor once loaded.
  useEffect(() => {
    if (staff && !selectedId && monitors && monitors.length > 0) {
      setSelectedId(monitors[0].id)
    }
  }, [staff, selectedId, monitors])

  return (
    <div className="space-y-6">
      <PageHeader title={t('nav.availability')} description={t('availability.subtitle')} />

      {staff && (
        <div className="max-w-sm space-y-1.5">
          <Label>{t('availability.monitor')}</Label>
          <Select value={selectedId} onValueChange={setSelectedId}>
            <SelectTrigger>
              <SelectValue placeholder={t('availability.selectMonitor')} />
            </SelectTrigger>
            <SelectContent>
              {(monitors ?? []).map((m) => (
                <SelectItem key={m.id} value={m.id}>
                  {fullName(m)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      )}

      {monitorId ? (
        <div className="grid gap-6 lg:grid-cols-2">
          <WeeklyHoursCard monitorId={monitorId} />
          <TimeOffCard monitorId={monitorId} />
        </div>
      ) : (
        <p className="text-sm text-muted-foreground">{t('availability.selectMonitorHint')}</p>
      )}
    </div>
  )
}

// --------------------------------------------------------------------------
// Weekly working hours (editable, replace-all)
// --------------------------------------------------------------------------
function WeeklyHoursCard({ monitorId }: { monitorId: string }) {
  const { t } = useTranslation()
  const { data, loading, reload } = useAsync(
    () => monitorsApi.getAvailability(monitorId),
    [monitorId],
  )

  const [rules, setRules] = useState<AvailabilityRule[]>([])
  const [saving, setSaving] = useState(false)

  // Sync the editable copy whenever the loaded availability changes.
  useEffect(() => {
    setRules(data ?? [])
  }, [data])

  const addRow = () =>
    setRules((prev) => [...prev, { dayOfWeek: 'MONDAY', startTime: '09:00', endTime: '12:00' }])

  const updateRow = (index: number, patch: Partial<AvailabilityRule>) =>
    setRules((prev) => prev.map((r, i) => (i === index ? { ...r, ...patch } : r)))

  const removeRow = (index: number) =>
    setRules((prev) => prev.filter((_, i) => i !== index))

  const save = async () => {
    for (const r of rules) {
      if (withSeconds(r.endTime) <= withSeconds(r.startTime)) {
        toast.error(t('availability.invalidRange'))
        return
      }
    }
    setSaving(true)
    try {
      await monitorsApi.replaceAvailability(
        monitorId,
        rules.map((r) => ({
          dayOfWeek: r.dayOfWeek,
          startTime: withSeconds(r.startTime),
          endTime: withSeconds(r.endTime),
        })),
      )
      toast.success(t('availability.savedToast'))
      reload()
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('common.error'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('availability.weeklyHours')}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {loading ? (
          <p className="text-sm text-muted-foreground">{t('common.loading')}</p>
        ) : rules.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t('availability.noHours')}</p>
        ) : (
          rules.map((rule, index) => (
            <div key={index} className="flex items-end gap-2">
              <div className="flex-1 space-y-1.5">
                {index === 0 && <Label className="text-xs">{t('availability.day')}</Label>}
                <Select
                  value={rule.dayOfWeek}
                  onValueChange={(v) => updateRow(index, { dayOfWeek: v as DayOfWeek })}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {DAYS_OF_WEEK.map((d) => (
                      <SelectItem key={d} value={d}>
                        {t(`enums.dayOfWeek.${d}`)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1.5">
                {index === 0 && <Label className="text-xs">{t('availability.from')}</Label>}
                <Input
                  type="time"
                  value={hhmm(rule.startTime)}
                  onChange={(e) => updateRow(index, { startTime: e.target.value })}
                  className="w-28"
                />
              </div>
              <div className="space-y-1.5">
                {index === 0 && <Label className="text-xs">{t('availability.to')}</Label>}
                <Input
                  type="time"
                  value={hhmm(rule.endTime)}
                  onChange={(e) => updateRow(index, { endTime: e.target.value })}
                  className="w-28"
                />
              </div>
              <Button
                size="icon"
                variant="ghost"
                onClick={() => removeRow(index)}
                aria-label={t('common.delete')}
              >
                <Trash2 className="size-4" />
              </Button>
            </div>
          ))
        )}

        <div className="flex justify-between pt-2">
          <Button variant="outline" size="sm" onClick={addRow}>
            <Plus className="size-4" />
            {t('availability.addRow')}
          </Button>
          <Button size="sm" onClick={save} disabled={saving}>
            <Save className="size-4" />
            {t('common.save')}
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}

// --------------------------------------------------------------------------
// Time-off (list + add + delete)
// --------------------------------------------------------------------------
function TimeOffCard({ monitorId }: { monitorId: string }) {
  const { t } = useTranslation()
  const { data, loading, reload, refresh } = useAsync(
    () => monitorsApi.listTimeOff(monitorId),
    [monitorId],
  )
  useRevalidateOnFocus(refresh)

  const [addOpen, setAddOpen] = useState(false)
  const [deleteFor, setDeleteFor] = useState<TimeOff | null>(null)

  const columns: Column<TimeOff>[] = [
    {
      key: 'period',
      header: t('availability.period'),
      cell: (o) => (
        <div>
          <p className="font-medium">{formatDateTime(o.startTime)}</p>
          <p className="text-xs text-muted-foreground">→ {formatDateTime(o.endTime)}</p>
        </div>
      ),
    },
    {
      key: 'reason',
      header: t('availability.reason'),
      cell: (o) => o.reason || '—',
    },
    {
      key: 'actions',
      header: '',
      className: 'text-end',
      cell: (o) => (
        <Button size="icon" variant="ghost" onClick={() => setDeleteFor(o)} aria-label={t('common.delete')}>
          <Trash2 className="size-4" />
        </Button>
      ),
    },
  ]

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between">
        <CardTitle>{t('availability.timeOff')}</CardTitle>
        <Button size="sm" onClick={() => setAddOpen(true)}>
          <Plus className="size-4" />
          {t('availability.addTimeOff')}
        </Button>
      </CardHeader>
      <CardContent>
        <DataTable
          columns={columns}
          rows={data ?? []}
          loading={loading}
          emptyLabel={t('availability.noTimeOff')}
        />
      </CardContent>

      <AddTimeOffDialog
        monitorId={monitorId}
        open={addOpen}
        onOpenChange={setAddOpen}
        onSaved={reload}
      />

      <ConfirmDialog
        open={deleteFor != null}
        onOpenChange={(o) => !o && setDeleteFor(null)}
        title={t('availability.deleteTimeOffTitle')}
        description={t('availability.deleteTimeOffConfirm')}
        confirmLabel={t('common.delete')}
        destructive
        onConfirm={async () => {
          if (!deleteFor) return
          try {
            await monitorsApi.deleteTimeOff(monitorId, deleteFor.id)
            toast.success(t('availability.deletedToast'))
            reload()
          } catch (err) {
            toast.error(err instanceof Error ? err.message : t('common.error'))
          }
        }}
      />
    </Card>
  )
}

function AddTimeOffDialog({
  monitorId,
  open,
  onOpenChange,
  onSaved,
}: {
  monitorId: string
  open: boolean
  onOpenChange: (open: boolean) => void
  onSaved: () => void
}) {
  const { t } = useTranslation()
  const [startDate, setStartDate] = useState(toDateInput(new Date()))
  const [startTime, setStartTime] = useState('09:00')
  const [endDate, setEndDate] = useState(toDateInput(new Date()))
  const [endTime, setEndTime] = useState('18:00')
  const [reason, setReason] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (open) {
      const today = toDateInput(new Date())
      setStartDate(today)
      setStartTime('09:00')
      setEndDate(today)
      setEndTime('18:00')
      setReason('')
    }
  }, [open])

  const submit = async () => {
    const start = fromDateTimeInput(startDate, startTime)
    const end = fromDateTimeInput(endDate, endTime)
    if (end <= start) {
      toast.error(t('availability.invalidRange'))
      return
    }
    setSaving(true)
    const payload: TimeOffInput = {
      startTime: start.toISOString(),
      endTime: end.toISOString(),
      reason: reason.trim() || null,
    }
    try {
      await monitorsApi.createTimeOff(monitorId, payload)
      toast.success(t('availability.timeOffAddedToast'))
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
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t('availability.addTimeOff')}</DialogTitle>
        </DialogHeader>
        <div className="grid gap-4 py-2">
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label>{t('availability.startDate')}</Label>
              <Input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
            </div>
            <div className="space-y-1.5">
              <Label>{t('availability.startTime')}</Label>
              <Input type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label>{t('availability.endDate')}</Label>
              <Input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} />
            </div>
            <div className="space-y-1.5">
              <Label>{t('availability.endTime')}</Label>
              <Input type="time" value={endTime} onChange={(e) => setEndTime(e.target.value)} />
            </div>
          </div>
          <div className="space-y-1.5">
            <Label>{t('availability.reason')}</Label>
            <Input
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder={t('availability.reasonPlaceholder')}
            />
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
