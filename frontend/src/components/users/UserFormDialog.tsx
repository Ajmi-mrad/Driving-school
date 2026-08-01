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
import { Switch } from '@/components/ui/switch'
import { cn } from '@/lib/utils'
import { usersApi, type UserInput } from '@/core/api'
import { ROLES, type Role, type User } from '@/core/types'

interface FormState {
  firstName: string
  lastName: string
  username: string
  email: string
  phones: string
  roles: Role[]
  permitNumber: string
  notificationsEnabled: boolean
}

const EMPTY: FormState = {
  firstName: '',
  lastName: '',
  username: '',
  email: '',
  phones: '',
  roles: ['CLIENT'],
  permitNumber: '',
  notificationsEnabled: true,
}

function fromUser(u: User): FormState {
  return {
    firstName: u.firstName,
    lastName: u.lastName,
    username: u.username,
    email: u.email,
    phones: u.phones.join(', '),
    roles: u.roles,
    permitNumber: u.permitNumber ?? '',
    notificationsEnabled: u.notificationsEnabled,
  }
}

export function UserFormDialog({
  open,
  onOpenChange,
  user,
  onSaved,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  user: User | null
  onSaved: () => void
}) {
  const { t } = useTranslation()
  const [form, setForm] = useState<FormState>(EMPTY)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (open) setForm(user ? fromUser(user) : EMPTY)
  }, [open, user])

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setForm((f) => ({ ...f, [key]: value }))

  const toggleRole = (role: Role) =>
    setForm((f) => ({
      ...f,
      roles: f.roles.includes(role)
        ? f.roles.filter((r) => r !== role)
        : [...f.roles, role],
    }))

  const valid =
    form.firstName.trim() &&
    form.lastName.trim() &&
    form.username.trim() &&
    form.email.trim() &&
    form.roles.length > 0

  const submit = async () => {
    if (!valid) return
    setSaving(true)
    const payload: UserInput = {
      firstName: form.firstName.trim(),
      lastName: form.lastName.trim(),
      username: form.username.trim(),
      email: form.email.trim(),
      phones: form.phones
        .split(',')
        .map((p) => p.trim())
        .filter(Boolean),
      roles: form.roles,
      permitNumber: form.permitNumber.trim() || null,
      notificationsEnabled: form.notificationsEnabled,
    }
    try {
      if (user) {
        await usersApi.update(user.id, payload)
        toast.success(t('users.updatedToast'))
      } else {
        await usersApi.create(payload)
        toast.success(t('users.createdToast'))
      }
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
          <DialogTitle>
            {user ? t('users.editTitle') : t('users.newTitle')}
          </DialogTitle>
        </DialogHeader>

        <div className="grid gap-4 py-2">
          <div className="grid grid-cols-2 gap-3">
            <Field label={t('users.firstName')}>
              <Input
                value={form.firstName}
                onChange={(e) => set('firstName', e.target.value)}
              />
            </Field>
            <Field label={t('users.lastName')}>
              <Input
                value={form.lastName}
                onChange={(e) => set('lastName', e.target.value)}
              />
            </Field>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <Field label={t('users.username')}>
              <Input
                value={form.username}
                onChange={(e) => set('username', e.target.value)}
              />
            </Field>
            <Field label={t('users.email')}>
              <Input
                type="email"
                value={form.email}
                onChange={(e) => set('email', e.target.value)}
              />
            </Field>
          </div>

          <Field label={t('users.phones')} hint={t('users.phonesHint')}>
            <Input
              value={form.phones}
              onChange={(e) => set('phones', e.target.value)}
            />
          </Field>

          <Field label={t('users.roles')}>
            <div className="flex flex-wrap gap-2">
              {ROLES.map((role) => {
                const active = form.roles.includes(role)
                return (
                  <button
                    key={role}
                    type="button"
                    onClick={() => toggleRole(role)}
                    className={cn(
                      'rounded-full border px-3 py-1 text-sm transition-colors',
                      active
                        ? 'border-primary bg-primary text-primary-foreground'
                        : 'border-input hover:bg-accent',
                    )}
                  >
                    {t(`enums.role.${role}`)}
                  </button>
                )
              })}
            </div>
          </Field>

          <Field label={`${t('users.permitNumber')} (${t('common.optional')})`}>
            <Input
              value={form.permitNumber}
              onChange={(e) => set('permitNumber', e.target.value)}
            />
          </Field>

          <div className="flex items-center justify-between rounded-lg border p-3">
            <Label htmlFor="notif">{t('users.notifications')}</Label>
            <Switch
              id="notif"
              checked={form.notificationsEnabled}
              onCheckedChange={(v) => set('notificationsEnabled', v)}
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

function Field({
  label,
  hint,
  children,
}: {
  label: string
  hint?: string
  children: React.ReactNode
}) {
  return (
    <div className="space-y-1.5">
      <Label>{label}</Label>
      {children}
      {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
    </div>
  )
}
