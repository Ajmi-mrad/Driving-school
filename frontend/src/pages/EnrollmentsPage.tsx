import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Pencil, Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { PageHeader } from '@/components/common/PageHeader'
import { DataTable, type Column } from '@/components/common/DataTable'
import { StatusBadge } from '@/components/common/StatusBadge'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { Button } from '@/components/ui/button'
import { EnrollmentFormDialog } from '@/components/finance/EnrollmentFormDialog'
import { EnrollmentEditDialog } from '@/components/finance/EnrollmentEditDialog'
import { EnrollmentDetailDialog } from '@/components/finance/EnrollmentDetailDialog'
import { useAuth } from '@/core/auth/AuthContext'
import { isStaff } from '@/core/auth/roles'
import { useAsync } from '@/core/hooks/useAsync'
import { useRevalidateOnFocus } from '@/hooks/useRevalidateOnFocus'
import { enrollmentsApi, forfaitsApi, usersApi } from '@/core/api'
import type { Enrollment } from '@/core/types'
import { fullName, formatCurrency } from '@/core/format'
import { ENROLLMENT_STATUS_TONE } from '@/lib/tones'

export function EnrollmentsPage() {
  const { t } = useTranslation()
  const { user, roles } = useAuth()
  const staff = isStaff(roles)
  const [formOpen, setFormOpen] = useState(false)
  const [selected, setSelected] = useState<Enrollment | null>(null)
  const [editing, setEditing] = useState<Enrollment | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Enrollment | 'bulk' | null>(null)
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())

  const { data, loading, reload, refresh } = useAsync(
    () => enrollmentsApi.list(staff ? undefined : user?.id),
    [staff, user?.id],
  )
  useRevalidateOnFocus(refresh)

  const performDelete = async () => {
    const ids = deleteTarget === 'bulk' ? [...selectedIds] : deleteTarget ? [deleteTarget.id] : []
    const results = await Promise.allSettled(ids.map((id) => enrollmentsApi.remove(id)))
    const ok = results.filter((r) => r.status === 'fulfilled').length
    const failed = ids.length - ok
    if (failed === 0) {
      toast.success(
        ids.length === 1 ? t('enrollments.deletedToast') : t('enrollments.deletedManyToast', { count: ok }),
      )
    } else {
      toast.error(t('enrollments.deletePartialToast', { ok, failed }))
    }
    if (deleteTarget === 'bulk') setSelectedIds(new Set())
    setDeleteTarget(null)
    reload()
  }
  const { data: users } = useAsync(() => (staff ? usersApi.list('CLIENT') : Promise.resolve([])), [staff])
  const { data: forfaits } = useAsync(() => forfaitsApi.list(), [])

  const nameOf = useMemo(() => {
    const map = new Map((users ?? []).map((u) => [u.id, fullName(u)]))
    if (user) map.set(user.id, fullName(user))
    return (id?: string | null) => (id ? (map.get(id) ?? '—') : '—')
  }, [users, user])

  const forfaitOf = useMemo(() => {
    const map = new Map((forfaits ?? []).map((f) => [f.id, f.name]))
    return (id: string) => map.get(id) ?? '—'
  }, [forfaits])

  const columns: Column<Enrollment>[] = [
    ...(staff
      ? [
          {
            key: 'client',
            header: t('enrollments.client'),
            cell: (e: Enrollment) => nameOf(e.clientId),
          },
        ]
      : []),
    {
      key: 'forfait',
      header: t('enrollments.forfait'),
      cell: (e) => forfaitOf(e.forfaitId),
    },
    {
      key: 'total',
      header: t('enrollments.total'),
      cell: (e) => formatCurrency(e.totalPrice),
    },
    {
      key: 'outstanding',
      header: t('enrollments.outstanding'),
      cell: (e) =>
        e.outstanding > 0 ? (
          <span className="font-medium text-destructive">
            {formatCurrency(e.outstanding)}
          </span>
        ) : (
          <span className="text-muted-foreground">{formatCurrency(0)}</span>
        ),
    },
    {
      key: 'status',
      header: t('common.status'),
      cell: (e) => (
        <StatusBadge tone={ENROLLMENT_STATUS_TONE[e.status]}>
          {t(`enums.enrollmentStatus.${e.status}`)}
        </StatusBadge>
      ),
    },
    ...(staff
      ? [
          {
            key: 'actions',
            header: '',
            className: 'w-24 text-end',
            cell: (e: Enrollment) => (
              <div className="flex justify-end gap-0.5">
                <Button
                  variant="ghost"
                  size="icon"
                  title={t('common.edit')}
                  aria-label={t('common.edit')}
                  onClick={(ev) => {
                    ev.stopPropagation()
                    setEditing(e)
                  }}
                >
                  <Pencil className="size-4" />
                </Button>
                <Button
                  variant="ghost"
                  size="icon"
                  className="text-destructive"
                  title={t('common.delete')}
                  aria-label={t('common.delete')}
                  onClick={(ev) => {
                    ev.stopPropagation()
                    setDeleteTarget(e)
                  }}
                >
                  <Trash2 className="size-4" />
                </Button>
              </div>
            ),
          },
        ]
      : []),
  ]

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('nav.enrollments')}
        description={t('enrollments.subtitle')}
        actions={
          staff && (
            <Button onClick={() => setFormOpen(true)}>
              <Plus className="size-4" />
              {t('enrollments.new')}
            </Button>
          )
        }
      />

      {staff && selectedIds.size > 0 && (
        <div className="flex flex-wrap items-center gap-2 rounded-xl border bg-muted/40 p-3">
          <span className="text-sm font-medium">
            {t('enrollments.selected', { count: selectedIds.size })}
          </span>
          <Button
            variant="outline"
            size="sm"
            className="ms-auto text-destructive"
            onClick={() => setDeleteTarget('bulk')}
          >
            <Trash2 className="size-4" />
            {t('common.delete')}
          </Button>
        </div>
      )}

      <DataTable
        columns={columns}
        rows={data ?? []}
        loading={loading}
        emptyLabel={t('enrollments.empty')}
        onRowClick={setSelected}
        selectable={staff}
        selectedIds={selectedIds}
        onSelectionChange={setSelectedIds}
      />

      <EnrollmentFormDialog open={formOpen} onOpenChange={setFormOpen} onSaved={reload} />

      <EnrollmentEditDialog
        enrollment={editing}
        onOpenChange={(o) => !o && setEditing(null)}
        onSaved={reload}
      />

      <EnrollmentDetailDialog
        enrollment={selected}
        onOpenChange={(o) => !o && setSelected(null)}
        resolveName={nameOf}
        resolveForfait={forfaitOf}
        canRemind={staff}
      />

      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(o) => !o && setDeleteTarget(null)}
        title={t('enrollments.deleteTitle')}
        description={
          deleteTarget === 'bulk'
            ? t('enrollments.deleteManyConfirm', { count: selectedIds.size })
            : t('enrollments.deleteConfirm')
        }
        confirmLabel={t('common.delete')}
        destructive
        onConfirm={performDelete}
      />
    </div>
  )
}
