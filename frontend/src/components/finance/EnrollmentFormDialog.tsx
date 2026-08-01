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
import { enrollmentsApi, forfaitsApi, usersApi } from '@/core/api'
import { fullName, formatCurrency } from '@/core/format'

export function EnrollmentFormDialog({
  open,
  onOpenChange,
  onSaved,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSaved: () => void
}) {
  const { t } = useTranslation()
  const [clientId, setClientId] = useState('')
  const [forfaitId, setForfaitId] = useState('')
  const [saving, setSaving] = useState(false)

  const { data: clients } = useAsync(
    () => (open ? usersApi.list('CLIENT') : Promise.resolve([])),
    [open],
  )
  const { data: forfaits } = useAsync(
    () => (open ? forfaitsApi.list(true) : Promise.resolve([])),
    [open],
  )

  useEffect(() => {
    if (open) {
      setClientId('')
      setForfaitId('')
    }
  }, [open])

  const submit = async () => {
    if (!clientId || !forfaitId) return
    setSaving(true)
    try {
      await enrollmentsApi.create(clientId, forfaitId)
      toast.success(t('enrollments.createdToast'))
      onSaved()
      onOpenChange(false)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t('enrollments.newTitle')}</DialogTitle>
        </DialogHeader>

        <div className="grid gap-4 py-2">
          <div className="space-y-1.5">
            <Label>{t('enrollments.client')}</Label>
            <Select value={clientId} onValueChange={setClientId}>
              <SelectTrigger>
                <SelectValue placeholder={t('enrollments.selectClient')} />
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
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('common.cancel')}
          </Button>
          <Button onClick={submit} disabled={!clientId || !forfaitId || saving}>
            {t('common.create')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
