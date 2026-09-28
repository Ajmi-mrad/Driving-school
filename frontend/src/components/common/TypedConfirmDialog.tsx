import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/**
 * Confirmation dialog for irreversible actions: the destructive button stays
 * disabled until the user types an exact confirmation phrase (e.g. "RESET").
 * A stricter variant of {@link ConfirmDialog} used for destructive data ops.
 */
export function TypedConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  phrase,
  phraseLabel,
  confirmLabel,
  loading,
  onConfirm,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description?: string
  phrase: string
  phraseLabel?: string
  confirmLabel?: string
  loading?: boolean
  onConfirm: () => void
}) {
  const { t } = useTranslation()
  const [value, setValue] = useState('')

  // Reset the typed value whenever the dialog opens/closes.
  useEffect(() => {
    if (!open) setValue('')
  }, [open])

  const matches = value.trim() === phrase

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          {description && <DialogDescription>{description}</DialogDescription>}
        </DialogHeader>
        <div className="space-y-1.5">
          {phraseLabel && <Label htmlFor="confirm-phrase">{phraseLabel}</Label>}
          <Input
            id="confirm-phrase"
            value={value}
            onChange={(e) => setValue(e.target.value)}
            placeholder={phrase}
            autoComplete="off"
          />
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('common.cancel')}
          </Button>
          <Button variant="destructive" disabled={!matches || loading} onClick={onConfirm}>
            {confirmLabel ?? t('common.confirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
