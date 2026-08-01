import { useTranslation } from 'react-i18next'
import { Bell } from 'lucide-react'
import { toast } from 'sonner'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { StatusBadge } from '@/components/common/StatusBadge'
import { paymentsApi } from '@/core/api'
import type { Enrollment } from '@/core/types'
import { formatCurrency, formatDate } from '@/core/format'
import { ENROLLMENT_STATUS_TONE } from '@/lib/tones'

export function EnrollmentDetailDialog({
  enrollment,
  onOpenChange,
  resolveName,
  resolveForfait,
  canRemind,
}: {
  enrollment: Enrollment | null
  onOpenChange: (open: boolean) => void
  resolveName: (id?: string | null) => string
  resolveForfait: (id: string) => string
  canRemind?: boolean
}) {
  const { t } = useTranslation()
  if (!enrollment) return null

  const remind = async () => {
    await paymentsApi.remind(enrollment.id)
    toast.success(t('payments.remindSent'))
  }

  const paidPct =
    enrollment.totalPrice > 0
      ? Math.round((enrollment.amountPaid / enrollment.totalPrice) * 100)
      : 100

  return (
    <Dialog open={!!enrollment} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-3">
            {resolveName(enrollment.clientId)}
            <StatusBadge tone={ENROLLMENT_STATUS_TONE[enrollment.status]}>
              {t(`enums.enrollmentStatus.${enrollment.status}`)}
            </StatusBadge>
          </DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          <p className="text-sm">
            <span className="text-muted-foreground">{t('enrollments.forfait')}: </span>
            <span className="font-medium">{resolveForfait(enrollment.forfaitId)}</span>
          </p>

          {/* Payment progress */}
          <div className="space-y-1.5">
            <div className="flex justify-between text-sm">
              <span className="text-muted-foreground">{t('enrollments.paid')}</span>
              <span className="font-medium">
                {formatCurrency(enrollment.amountPaid)} / {formatCurrency(enrollment.totalPrice)}
              </span>
            </div>
            <div className="h-2 overflow-hidden rounded-full bg-muted">
              <div className="h-full bg-primary" style={{ width: `${paidPct}%` }} />
            </div>
            {enrollment.outstanding > 0 && (
              <p className="text-sm text-destructive">
                {t('enrollments.outstanding')}: {formatCurrency(enrollment.outstanding)}
              </p>
            )}
          </div>

          <div className="grid grid-cols-2 gap-3">
            <Stat label={t('enrollments.remainingHours')} value={enrollment.remainingDrivingHours} />
            <Stat label={t('enrollments.remainingCode')} value={enrollment.remainingCodeSessions} />
          </div>

          <p className="text-sm text-muted-foreground">
            {t('enrollments.enrolledAt')} {formatDate(enrollment.enrolledAt)}
          </p>

          {canRemind && enrollment.outstanding > 0 && (
            <div className="flex justify-end">
              <Button variant="outline" size="sm" onClick={remind}>
                <Bell className="size-4" />
                {t('payments.remind')}
              </Button>
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-lg border p-3">
      <p className="text-2xl font-semibold">{value}</p>
      <p className="text-xs text-muted-foreground">{label}</p>
    </div>
  )
}
