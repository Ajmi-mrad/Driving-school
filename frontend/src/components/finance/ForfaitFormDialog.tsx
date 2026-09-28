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
import { Switch } from '@/components/ui/switch'
import { forfaitsApi, type ForfaitInput } from '@/core/api'
import type { Forfait } from '@/core/types'

interface FormState {
  name: string
  description: string
  drivingHours: string
  codeSessions: string
  price: string
  active: boolean
}

const EMPTY: FormState = {
  name: '',
  description: '',
  drivingHours: '20',
  codeSessions: '30',
  price: '0',
  active: true,
}

export function ForfaitFormDialog({
  open,
  onOpenChange,
  forfait,
  onSaved,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  forfait: Forfait | null
  onSaved: () => void
}) {
  const { t } = useTranslation()
  const [form, setForm] = useState<FormState>(EMPTY)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (open) {
      setForm(
        forfait
          ? {
              name: forfait.name,
              description: forfait.description,
              drivingHours: String(forfait.drivingHours),
              codeSessions: String(forfait.codeSessions),
              price: String(forfait.price),
              active: forfait.active,
            }
          : EMPTY,
      )
    }
  }, [open, forfait])

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setForm((f) => ({ ...f, [key]: value }))

  const submit = async () => {
    if (!form.name.trim()) return
    setSaving(true)
    const payload: ForfaitInput = {
      name: form.name.trim(),
      description: form.description.trim(),
      drivingHours: Number(form.drivingHours) || 0,
      codeSessions: Number(form.codeSessions) || 0,
      price: Number(form.price) || 0,
      active: form.active,
    }
    try {
      if (forfait) {
        await forfaitsApi.update(forfait.id, payload)
        toast.success(t('forfaits.updatedToast'))
      } else {
        await forfaitsApi.create(payload)
        toast.success(t('forfaits.createdToast'))
      }
      onSaved()
      onOpenChange(false)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>
            {forfait ? t('forfaits.editTitle') : t('forfaits.newTitle')}
          </DialogTitle>
        </DialogHeader>

        <div className="grid gap-4 py-2">
          <div className="space-y-1.5">
            <Label>{t('forfaits.name')}</Label>
            <Input value={form.name} onChange={(e) => set('name', e.target.value)} />
          </div>
          <div className="space-y-1.5">
            <Label>{t('forfaits.description')}</Label>
            <Textarea
              value={form.description}
              onChange={(e) => set('description', e.target.value)}
              rows={2}
            />
          </div>
          <div className="grid grid-cols-3 gap-3">
            <div className="space-y-1.5">
              <Label>{t('forfaits.drivingHours')}</Label>
              <Input
                type="number"
                value={form.drivingHours}
                onChange={(e) => set('drivingHours', e.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label>{t('forfaits.codeSessions')}</Label>
              <Input
                type="number"
                value={form.codeSessions}
                onChange={(e) => set('codeSessions', e.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label>{t('forfaits.price')}</Label>
              <Input
                type="number"
                value={form.price}
                onChange={(e) => set('price', e.target.value)}
              />
            </div>
          </div>
          <div className="flex items-center justify-between rounded-lg border p-3">
            <Label htmlFor="forfait-active">{t('forfaits.active')}</Label>
            <Switch
              id="forfait-active"
              checked={form.active}
              onCheckedChange={(v) => set('active', v)}
            />
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('common.cancel')}
          </Button>
          <Button onClick={submit} disabled={!form.name.trim() || saving}>
            {t('common.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
