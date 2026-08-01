import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
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
import { sessionsApi, usersApi, vehiclesApi, type SessionInput } from '@/core/api'
import { SESSION_TYPES, type SessionType } from '@/core/types'
import { fromDateTimeInput, toDateInput } from '@/core/datetime'
import { fullName } from '@/core/format'

export function SessionFormDialog({
  open,
  onOpenChange,
  onSaved,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSaved: () => void
}) {
  const { t } = useTranslation()
  const { user, roles } = useAuth()
  const staff = isStaff(roles)

  const [type, setType] = useState<SessionType>('DRIVING')
  const [clientId, setClientId] = useState('')
  const [monitorId, setMonitorId] = useState('')
  const [vehicleId, setVehicleId] = useState('AUTO')
  const [date, setDate] = useState(toDateInput(new Date()))
  const [startTime, setStartTime] = useState('09:00')
  const [endTime, setEndTime] = useState('10:00')
  const [notes, setNotes] = useState('')
  const [saving, setSaving] = useState(false)

  const { data: clients } = useAsync(
    () => (staff && open ? usersApi.list('CLIENT') : Promise.resolve([])),
    [open, staff],
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
      setType('DRIVING')
      setClientId(staff ? '' : (user?.id ?? ''))
      setMonitorId('')
      setVehicleId('AUTO')
      setDate(toDateInput(new Date()))
      setStartTime('09:00')
      setEndTime('10:00')
      setNotes('')
    }
  }, [open, staff, user?.id])

  const isDriving = type === 'DRIVING'
  const valid =
    clientId &&
    date &&
    startTime &&
    endTime &&
    startTime < endTime &&
    (!isDriving || monitorId)

  const submit = async () => {
    if (!valid) return
    setSaving(true)
    const payload: SessionInput = {
      type,
      clientId,
      monitorId: isDriving ? monitorId : null,
      vehicleId: isDriving && vehicleId !== 'AUTO' ? vehicleId : null,
      startTime: fromDateTimeInput(date, startTime).toISOString(),
      endTime: fromDateTimeInput(date, endTime).toISOString(),
      notes: notes.trim() || null,
    }
    try {
      await sessionsApi.create(payload)
      toast.success(t('sessions.createdToast'))
      onSaved()
      onOpenChange(false)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90svh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{t('sessions.bookingTitle')}</DialogTitle>
        </DialogHeader>

        <div className="grid gap-4 py-2">
          <div className="space-y-1.5">
            <Label>{t('sessions.type')}</Label>
            <Select value={type} onValueChange={(v) => setType(v as SessionType)}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {SESSION_TYPES.map((ty) => (
                  <SelectItem key={ty} value={ty}>
                    {t(`enums.sessionType.${ty}`)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {staff && (
            <div className="space-y-1.5">
              <Label>{t('sessions.client')}</Label>
              <Select value={clientId} onValueChange={setClientId}>
                <SelectTrigger>
                  <SelectValue placeholder={t('sessions.selectClient')} />
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
          )}

          {isDriving ? (
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label>{t('sessions.monitor')}</Label>
                <Select value={monitorId} onValueChange={setMonitorId}>
                  <SelectTrigger>
                    <SelectValue placeholder={t('sessions.selectMonitor')} />
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
              <div className="space-y-1.5">
                <Label>{t('sessions.vehicle')}</Label>
                <Select value={vehicleId} onValueChange={setVehicleId}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="AUTO">{t('sessions.autoVehicle')}</SelectItem>
                    {(vehicles ?? []).map((v) => (
                      <SelectItem key={v.id} value={v.id}>
                        {v.brand} {v.model} · {v.registrationNumber}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">{t('sessions.codeNoMonitor')}</p>
          )}

          <div className="space-y-1.5">
            <Label>{t('sessions.date')}</Label>
            <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label>{t('sessions.startTime')}</Label>
              <Input
                type="time"
                value={startTime}
                onChange={(e) => setStartTime(e.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label>{t('sessions.endTime')}</Label>
              <Input
                type="time"
                value={endTime}
                onChange={(e) => setEndTime(e.target.value)}
              />
            </div>
          </div>

          <div className="space-y-1.5">
            <Label>{t('sessions.notes')}</Label>
            <Textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={2} />
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
