import { useEffect, useMemo, useState } from 'react'
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useAsync } from '@/core/hooks/useAsync'
import { enrollmentsApi, paymentsApi, usersApi, type PaymentInput } from '@/core/api'
import { PAYMENT_METHODS, type PaymentMethod } from '@/core/types'
import { fullName, formatCurrency } from '@/core/format'

export function PaymentFormDialog({
  open,
  onOpenChange,
  onSaved,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSaved: () => void
}) {
  const { t } = useTranslation()
  const [enrollmentId, setEnrollmentId] = useState('')
  const [amount, setAmount] = useState('')
  const [method, setMethod] = useState<PaymentMethod>('CARD')
  const [reference, setReference] = useState('')
  const [saving, setSaving] = useState(false)

  const { data: enrollments } = useAsync(
    () => (open ? enrollmentsApi.list() : Promise.resolve([])),
    [open],
  )
  const { data: clients } = useAsync(
    () => (open ? usersApi.list('CLIENT') : Promise.resolve([])),
    [open],
  )

  const nameOf = useMemo(() => {
    const map = new Map((clients ?? []).map((c) => [c.id, fullName(c)]))
    return (id: string) => map.get(id) ?? '—'
  }, [clients])

  useEffect(() => {
    if (open) {
      setEnrollmentId('')
      setAmount('')
      setMethod('CARD')
      setReference('')
    }
  }, [open])

  const onPickEnrollment = (id: string) => {
    setEnrollmentId(id)
    const e = (enrollments ?? []).find((x) => x.id === id)
    if (e) setAmount(String(e.outstanding))
  }

  const submit = async () => {
    if (!enrollmentId || Number(amount) <= 0) return
    setSaving(true)
    const payload: PaymentInput = {
      enrollmentId,
      amount: Number(amount),
      method,
      reference: reference.trim() || null,
    }
    try {
      await paymentsApi.create(payload)
      toast.success(t('payments.createdToast'))
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
          <DialogTitle>{t('payments.newTitle')}</DialogTitle>
        </DialogHeader>

        <div className="grid gap-4 py-2">
          <div className="space-y-1.5">
            <Label>{t('payments.enrollment')}</Label>
            <Select value={enrollmentId} onValueChange={onPickEnrollment}>
              <SelectTrigger>
                <SelectValue placeholder={t('payments.selectEnrollment')} />
              </SelectTrigger>
              <SelectContent>
                {(enrollments ?? []).map((e) => (
                  <SelectItem key={e.id} value={e.id}>
                    {nameOf(e.clientId)} — {formatCurrency(e.outstanding)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label>{t('payments.amount')}</Label>
              <Input
                type="number"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label>{t('payments.method')}</Label>
              <Select value={method} onValueChange={(v) => setMethod(v as PaymentMethod)}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {PAYMENT_METHODS.map((m) => (
                    <SelectItem key={m} value={m}>
                      {t(`enums.paymentMethod.${m}`)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          <div className="space-y-1.5">
            <Label>{t('payments.reference')}</Label>
            <Input value={reference} onChange={(e) => setReference(e.target.value)} />
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('common.cancel')}
          </Button>
          <Button onClick={submit} disabled={!enrollmentId || Number(amount) <= 0 || saving}>
            {t('common.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
