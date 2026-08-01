import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { PageHeader } from '@/components/common/PageHeader'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuth } from '@/core/auth/AuthContext'
import { isOwner } from '@/core/auth/roles'
import { useAsync } from '@/core/hooks/useAsync'
import { settingsApi } from '@/core/api'

export function SettingsPage() {
  const { t } = useTranslation()
  const { roles } = useAuth()
  const canEdit = isOwner(roles)

  const { data, loading } = useAsync(() => settingsApi.get(), [])
  const [autoValidation, setAutoValidation] = useState(false)
  const [notice, setNotice] = useState('24')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (data) {
      setAutoValidation(data.autoValidationEnabled)
      setNotice(String(data.cancellationNoticeHours))
    }
  }, [data])

  const save = async () => {
    setSaving(true)
    try {
      await settingsApi.update({
        autoValidationEnabled: autoValidation,
        cancellationNoticeHours: Number(notice) || 0,
      })
      toast.success(t('settings.savedToast'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader title={t('nav.settings')} description={t('settings.subtitle')} />

      {loading ? (
        <Skeleton className="h-64 max-w-2xl rounded-xl" />
      ) : (
        <Card className="max-w-2xl">
          <CardHeader>
            <h2 className="font-semibold">{t('settings.booking')}</h2>
            {!canEdit && (
              <p className="text-sm text-muted-foreground">{t('settings.ownerOnly')}</p>
            )}
          </CardHeader>
          <CardContent className="space-y-6">
            <div className="flex items-center justify-between gap-4">
              <div>
                <Label htmlFor="auto">{t('settings.autoValidation')}</Label>
                <p className="text-sm text-muted-foreground">
                  {t('settings.autoValidationHint')}
                </p>
              </div>
              <Switch
                id="auto"
                checked={autoValidation}
                onCheckedChange={setAutoValidation}
                disabled={!canEdit}
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="notice">{t('settings.cancellationNotice')}</Label>
              <Input
                id="notice"
                type="number"
                className="max-w-40"
                value={notice}
                onChange={(e) => setNotice(e.target.value)}
                disabled={!canEdit}
              />
              <p className="text-sm text-muted-foreground">
                {t('settings.cancellationNoticeHint')}
              </p>
            </div>

            {canEdit && (
              <div className="flex justify-end">
                <Button onClick={save} disabled={saving}>
                  {t('common.save')}
                </Button>
              </div>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  )
}
