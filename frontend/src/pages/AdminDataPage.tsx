import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Download, Loader2, Sprout, Trash2 } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { TypedConfirmDialog } from '@/components/common/TypedConfirmDialog'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { adminApi, SEED_ACCOUNTS, SEED_PASSWORD } from '@/core/api'
import { saveBlob } from '@/lib/download'

const RESET_PHRASE = 'RESET'

export function AdminDataPage() {
  const { t } = useTranslation()
  const [seeding, setSeeding] = useState(false)
  const [resetting, setResetting] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)

  const seed = async () => {
    setSeeding(true)
    try {
      const summary = await adminApi.seedAll()
      toast.success(t('adminData.seededToast', { created: summary.created, skipped: summary.skipped }))
    } catch (e) {
      toast.error(t('adminData.errorToast', { message: (e as Error).message }))
    } finally {
      setSeeding(false)
    }
  }

  const reset = async () => {
    setResetting(true)
    try {
      await adminApi.resetAll()
      setConfirmOpen(false)
      toast.success(t('adminData.resetDoneToast'))
    } catch (e) {
      toast.error(t('adminData.errorToast', { message: (e as Error).message }))
    } finally {
      setResetting(false)
    }
  }

  const exportAll = async () => {
    setExporting(true)
    try {
      const bundle = await adminApi.exportAll()
      const stamp = new Date().toISOString().slice(0, 10)
      saveBlob(
        new Blob([JSON.stringify(bundle, null, 2)], { type: 'application/json' }),
        `driving-school-export-${stamp}.json`,
      )
      toast.success(t('adminData.exportDoneToast'))
    } catch (e) {
      toast.error(t('adminData.errorToast', { message: (e as Error).message }))
    } finally {
      setExporting(false)
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader title={t('adminData.title')} description={t('adminData.subtitle')} />

      {/* Seed demo data */}
      <Card className="max-w-2xl">
        <CardHeader>
          <div className="flex items-center gap-2">
            <Sprout className="size-5 text-primary" />
            <h2 className="font-semibold">{t('adminData.seedTitle')}</h2>
          </div>
          <p className="text-sm text-muted-foreground">{t('adminData.seedDesc')}</p>
        </CardHeader>
        <CardContent className="space-y-4">
          <Button onClick={seed} disabled={seeding}>
            {seeding ? <Loader2 className="size-4 animate-spin" /> : <Sprout className="size-4" />}
            {t('adminData.seedButton')}
          </Button>

          <div className="rounded-lg border bg-muted/40 p-3 text-sm">
            <p className="mb-2 font-medium">{t('adminData.seedCredentialsTitle')}</p>
            <p className="mb-2 text-muted-foreground">
              {t('adminData.seedPassword')}: <code className="font-mono">{SEED_PASSWORD}</code>
            </p>
            <ul className="grid grid-cols-2 gap-x-4 gap-y-1 font-mono text-xs">
              {SEED_ACCOUNTS.map((a) => (
                <li key={a.username} className="flex justify-between gap-2">
                  <span>{a.username}</span>
                  <span className="text-muted-foreground">{a.role}</span>
                </li>
              ))}
            </ul>
          </div>
        </CardContent>
      </Card>

      {/* Export */}
      <Card className="max-w-2xl">
        <CardHeader>
          <div className="flex items-center gap-2">
            <Download className="size-5 text-primary" />
            <h2 className="font-semibold">{t('adminData.exportTitle')}</h2>
          </div>
          <p className="text-sm text-muted-foreground">{t('adminData.exportDesc')}</p>
        </CardHeader>
        <CardContent>
          <Button variant="outline" onClick={exportAll} disabled={exporting}>
            {exporting ? <Loader2 className="size-4 animate-spin" /> : <Download className="size-4" />}
            {t('adminData.exportButton')}
          </Button>
        </CardContent>
      </Card>

      {/* Reset (destructive) */}
      <Card className="max-w-2xl border-destructive/40">
        <CardHeader>
          <div className="flex items-center gap-2">
            <Trash2 className="size-5 text-destructive" />
            <h2 className="font-semibold">{t('adminData.resetTitle')}</h2>
          </div>
          <p className="text-sm text-muted-foreground">{t('adminData.resetDesc')}</p>
        </CardHeader>
        <CardContent>
          <Button variant="destructive" onClick={() => setConfirmOpen(true)} disabled={resetting}>
            <Trash2 className="size-4" />
            {t('adminData.resetButton')}
          </Button>
        </CardContent>
      </Card>

      <TypedConfirmDialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title={t('adminData.resetConfirmTitle')}
        description={t('adminData.resetConfirmDesc')}
        phrase={RESET_PHRASE}
        phraseLabel={t('adminData.resetPhraseLabel', { phrase: RESET_PHRASE })}
        confirmLabel={t('adminData.resetButton')}
        loading={resetting}
        onConfirm={reset}
      />
    </div>
  )
}
