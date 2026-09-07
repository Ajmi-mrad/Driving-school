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
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useAsync } from '@/core/hooks/useAsync'
import { enrollmentsApi, forfaitsApi } from '@/core/api'
import { ApiError } from '@/core/api/client'
import { ENROLLMENT_STATUSES, type Enrollment, type EnrollmentStatus } from '@/core/types'
import { formatCurrency } from '@/core/format'

/** Edit an existing enrollment: change its package (forfait) and/or status. Staff only. */
export function EnrollmentEditDialog({
  enrollment,
  onOpenChange,
  onSaved,
}: {
  enrollment: Enrollment | null
  onOpenChange: (open: boolean) => void
  onSaved: () => void
}) {
  const { t } = useTranslation()
  const open = !!enrollment
  const [forfaitId, setForfaitId] = useState('')
  const [status, setStatus] = useState<EnrollmentStatus>('ACTIVE')
  const [saving, setSaving] = useState(false)

  const { data: forfaits } = useAsync(
    () => (open ? forfaitsApi.list() : Promise.resolve([])),
    [open],
  )

  useEffect(() => {
    if (enrollment) {
      setForfaitId(enrollment.forfaitId)
      setStatus(enrollment.status)
    }
  }, [enrollment])

  const submit = async () => {
    if (!enrollment || !forfaitId) return
    setSaving(true)
    try {
      await enrollmentsApi.update(enrollment.id, { forfaitId, status })
      toast.success(t('enrollments.updatedToast'))
      onSaved()
      onOpenChange(false)
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : t('common.error'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t('enrollments.editTitle')}</DialogTitle>
        </DialogHeader>

        <div className="grid gap-4 py-2">
          <div className="space-y-1.5">
            <Label>{t('enrollments.forfait')}</Label>
            <Select value={forfaitId} onValueChange={setForfaitId}>
              <SelectTrigger>
                <SelectValue placeholder={t('enrollments.selectForfait')} />
              </SelectTrigger>
              <SelectContent>
                {(forfaits ?? []).map((f) => (
                  <SelectItem key={f.id} value={f.id}>
                    {f.name} — {formatCurrency(f.price)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-1.5">
            <Label>{t('enrollments.status')}</Label>
            <Select value={status} onValueChange={(v) => setStatus(v as EnrollmentStatus)}>
              <SelectTrigger>
                <SelectValue placeholder={t('enrollments.selectStatus')} />
              </SelectTrigger>
              <SelectContent>
                {ENROLLMENT_STATUSES.map((s) => (
                  <SelectItem key={s} value={s}>
                    {t(`enums.enrollmentStatus.${s}`)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('common.cancel')}
          </Button>
          <Button onClick={submit} disabled={!forfaitId || saving}>
            {t('common.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
