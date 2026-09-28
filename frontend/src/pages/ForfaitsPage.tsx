import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Car, GraduationCap, Pencil, Plus, Trash2 } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { StatusBadge } from '@/components/common/StatusBadge'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { ForfaitFormDialog } from '@/components/finance/ForfaitFormDialog'
import { useAuth } from '@/core/auth/AuthContext'
import { isStaff } from '@/core/auth/roles'
import { useAsync } from '@/core/hooks/useAsync'
import { useRevalidateOnFocus } from '@/hooks/useRevalidateOnFocus'
import { forfaitsApi } from '@/core/api'
import { ApiError } from '@/core/api/client'
import type { Forfait } from '@/core/types'
import { formatCurrency } from '@/core/format'

export function ForfaitsPage() {
  const { t } = useTranslation()
  const { roles } = useAuth()
  const canWrite = isStaff(roles)
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<Forfait | null>(null)
  const [toDelete, setToDelete] = useState<Forfait | null>(null)

  const { data, loading, reload, refresh } = useAsync(() => forfaitsApi.list(!canWrite), [canWrite])
  useRevalidateOnFocus(refresh)

  const remove = async (f: Forfait) => {
    try {
      await forfaitsApi.remove(f.id)
      toast.success(t('forfaits.deletedToast'))
      reload()
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : t('common.error'))
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('nav.forfaits')}
        description={t('forfaits.subtitle')}
        actions={
          canWrite && (
            <Button
              onClick={() => {
                setEditing(null)
                setFormOpen(true)
              }}
            >
              <Plus className="size-4" />
              {t('forfaits.new')}
            </Button>
          )
        }
      />

      {loading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-52 rounded-xl" />
          ))}
        </div>
      ) : (data ?? []).length === 0 ? (
        <p className="py-16 text-center text-muted-foreground">{t('forfaits.empty')}</p>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {(data ?? []).map((f) => (
            <Card key={f.id} className="flex flex-col">
              <CardHeader className="flex-row items-start justify-between gap-2 space-y-0">
                <div>
                  <h3 className="font-semibold">{f.name}</h3>
                  {!f.active && (
                    <StatusBadge tone="neutral">{t('common.inactive')}</StatusBadge>
                  )}
                </div>
                {canWrite && (
                  <div className="flex items-center gap-1">
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => {
                        setEditing(f)
                        setFormOpen(true)
                      }}
                    >
                      <Pencil className="size-4" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="text-destructive"
                      onClick={() => setToDelete(f)}
                    >
                      <Trash2 className="size-4" />
                    </Button>
                  </div>
                )}
              </CardHeader>
              <CardContent className="flex flex-1 flex-col gap-4">
                <p className="flex-1 text-sm text-muted-foreground">{f.description}</p>
                <div className="flex flex-wrap gap-2 text-sm">
                  {f.drivingHours > 0 && (
                    <span className="inline-flex items-center gap-1.5 rounded-md bg-muted px-2 py-1">
                      <Car className="size-3.5" />
                      {t('forfaits.hoursShort', { count: f.drivingHours })}
                    </span>
                  )}
                  {f.codeSessions > 0 && (
                    <span className="inline-flex items-center gap-1.5 rounded-md bg-muted px-2 py-1">
                      <GraduationCap className="size-3.5" />
                      {t('forfaits.sessionsShort', { count: f.codeSessions })}
                    </span>
                  )}
                </div>
                <p className="text-2xl font-semibold text-primary">
                  {formatCurrency(f.price)}
                </p>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      <ForfaitFormDialog
        open={formOpen}
        onOpenChange={setFormOpen}
        forfait={editing}
        onSaved={reload}
      />

      <ConfirmDialog
        open={!!toDelete}
        onOpenChange={(o) => !o && setToDelete(null)}
        title={t('forfaits.deleteTitle')}
        description={t('forfaits.deleteConfirm')}
        confirmLabel={t('common.delete')}
        destructive
        onConfirm={() => toDelete && remove(toDelete)}
      />
    </div>
  )
}
