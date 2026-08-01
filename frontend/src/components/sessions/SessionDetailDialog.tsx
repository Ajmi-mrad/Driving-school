import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { StatusBadge } from '@/components/common/StatusBadge'
import { useAuth } from '@/core/auth/AuthContext'
import { isStaff } from '@/core/auth/roles'
import { sessionsApi } from '@/core/api'
import type { Session } from '@/core/types'
import { formatDate, formatTime } from '@/core/format'
import { fromDateTimeInput, toDateInput, toTimeInput } from '@/core/datetime'
import { SESSION_STATUS_TONE } from '@/lib/tones'

export function SessionDetailDialog({
  session,
  onOpenChange,
  onChanged,
  resolveName,
  resolveVehicle,
}: {
  session: Session | null
  onOpenChange: (open: boolean) => void
  onChanged: () => void
  resolveName: (id?: string | null) => string
  resolveVehicle: (id?: string | null) => string
}) {
  const { t } = useTranslation()
  const { user, roles } = useAuth()
  const staff = isStaff(roles)
  const isMonitor = roles.includes('MONITOR')
  const [rescheduling, setRescheduling] = useState(false)
  const [date, setDate] = useState('')
  const [start, setStart] = useState('')
  const [end, setEnd] = useState('')

  const open = !!session
  if (!session) return null

  const own = session.clientId === user?.id
  const canModerate = staff || isMonitor
  const canOwnerAct = staff || own
  const isPending = session.status === 'PENDING'
  const isConfirmed = session.status === 'CONFIRMED'
  const activeStatus = isPending || isConfirmed

  const act = async (fn: () => Promise<unknown>, msg: string) => {
    await fn()
    toast.success(msg)
    onChanged()
    onOpenChange(false)
  }

  const startReschedule = () => {
    setDate(toDateInput(new Date(session.startTime)))
    setStart(toTimeInput(new Date(session.startTime)))
    setEnd(toTimeInput(new Date(session.endTime)))
    setRescheduling(true)
  }

  const submitReschedule = async () => {
    if (!date || start >= end) return
    await act(
      () =>
        sessionsApi.reschedule(
          session.id,
          fromDateTimeInput(date, start).toISOString(),
          fromDateTimeInput(date, end).toISOString(),
        ),
      t('sessions.rescheduledToast'),
    )
    setRescheduling(false)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-3">
            {t(`enums.sessionType.${session.type}`)}
            <StatusBadge tone={SESSION_STATUS_TONE[session.status]}>
              {t(`enums.sessionStatus.${session.status}`)}
            </StatusBadge>
          </DialogTitle>
        </DialogHeader>

        <dl className="grid grid-cols-3 gap-y-3 text-sm">
          <Row label={t('sessions.client')} value={resolveName(session.clientId)} />
          {session.type === 'DRIVING' && (
            <>
              <Row
                label={t('sessions.monitor')}
                value={resolveName(session.monitorId) || t('sessions.unassigned')}
              />
              <Row
                label={t('sessions.vehicle')}
                value={resolveVehicle(session.vehicleId) || t('sessions.autoVehicle')}
              />
            </>
          )}
          <Row label={t('sessions.date')} value={formatDate(session.startTime)} />
          <Row
            label={t('sessions.startTime')}
            value={`${formatTime(session.startTime)} – ${formatTime(session.endTime)}`}
          />
          {session.notes && <Row label={t('sessions.notes')} value={session.notes} />}
        </dl>

        {rescheduling ? (
          <div className="space-y-3 rounded-lg border p-3">
            <div className="space-y-1.5">
              <Label>{t('sessions.date')}</Label>
              <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label>{t('sessions.startTime')}</Label>
                <Input type="time" value={start} onChange={(e) => setStart(e.target.value)} />
              </div>
              <div className="space-y-1.5">
                <Label>{t('sessions.endTime')}</Label>
                <Input type="time" value={end} onChange={(e) => setEnd(e.target.value)} />
              </div>
            </div>
            <div className="flex justify-end gap-2">
              <Button variant="outline" size="sm" onClick={() => setRescheduling(false)}>
                {t('common.cancel')}
              </Button>
              <Button size="sm" onClick={submitReschedule} disabled={start >= end}>
                {t('common.save')}
              </Button>
            </div>
          </div>
        ) : (
          <div className="flex flex-wrap justify-end gap-2">
            {canModerate && isPending && (
              <>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() =>
                    act(() => sessionsApi.refuse(session.id), t('sessions.refusedToast'))
                  }
                >
                  {t('sessions.refuse')}
                </Button>
                <Button
                  size="sm"
                  onClick={() =>
                    act(() => sessionsApi.confirm(session.id), t('sessions.confirmedToast'))
                  }
                >
                  {t('sessions.confirm')}
                </Button>
              </>
            )}
            {canModerate && isConfirmed && (
              <Button
                size="sm"
                onClick={() =>
                  act(() => sessionsApi.complete(session.id), t('sessions.completedToast'))
                }
              >
                {t('sessions.complete')}
              </Button>
            )}
            {canOwnerAct && activeStatus && (
              <>
                <Button variant="outline" size="sm" onClick={startReschedule}>
                  {t('sessions.reschedule')}
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  className="text-destructive"
                  onClick={() =>
                    act(() => sessionsApi.cancel(session.id), t('sessions.cancelledToast'))
                  }
                >
                  {t('sessions.cancel')}
                </Button>
              </>
            )}
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <>
      <dt className="col-span-1 text-muted-foreground">{label}</dt>
      <dd className="col-span-2 font-medium">{value}</dd>
    </>
  )
}
