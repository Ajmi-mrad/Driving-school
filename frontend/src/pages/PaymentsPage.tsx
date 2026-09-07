import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Pencil, Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { PageHeader } from '@/components/common/PageHeader'
import { DataTable, type Column } from '@/components/common/DataTable'
import { StatusBadge } from '@/components/common/StatusBadge'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { Button } from '@/components/ui/button'
import { PaymentFormDialog } from '@/components/finance/PaymentFormDialog'
import { useAuth } from '@/core/auth/AuthContext'
import { isStaff } from '@/core/auth/roles'
import { useAsync } from '@/core/hooks/useAsync'
import { useRevalidateOnFocus } from '@/hooks/useRevalidateOnFocus'
import { paymentsApi, usersApi } from '@/core/api'
import type { Payment } from '@/core/types'
import { fullName, formatCurrency, formatDate } from '@/core/format'

export function PaymentsPage() {
  const { t } = useTranslation()
  const { user, roles } = useAuth()
  const staff = isStaff(roles)
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<Payment | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Payment | 'bulk' | null>(null)
  const [selected, setSelected] = useState<Set<string>>(new Set())

  const { data, loading, reload, refresh } = useAsync(
    () => paymentsApi.list(staff ? undefined : user?.id),
    [staff, user?.id],
  )
  useRevalidateOnFocus(refresh)

  const performDelete = async () => {
    const ids = deleteTarget === 'bulk' ? [...selected] : deleteTarget ? [deleteTarget.id] : []
    const results = await Promise.allSettled(ids.map((id) => paymentsApi.remove(id)))
    const ok = results.filter((r) => r.status === 'fulfilled').length
    const failed = ids.length - ok
    if (failed === 0) {
      toast.success(ids.length === 1 ? t('payments.deletedToast') : t('payments.deletedManyToast', { count: ok }))
    } else {
      toast.error(t('payments.deletePartialToast', { ok, failed }))
    }
    if (deleteTarget === 'bulk') setSelected(new Set())
    setDeleteTarget(null)
    reload()
  }
  const { data: users } = useAsync(
    () => (staff ? usersApi.list('CLIENT') : Promise.resolve([])),
    [staff],
  )

  const nameOf = useMemo(() => {
    const map = new Map((users ?? []).map((u) => [u.id, fullName(u)]))
    if (user) map.set(user.id, fullName(user))
    return (id: string) => map.get(id) ?? '—'
  }, [users, user])

  const columns: Column<Payment>[] = [
    {
      key: 'date',
      header: t('payments.date'),
      cell: (p) => formatDate(p.paidAt),
    },
    ...(staff
      ? [
          {
            key: 'client',
            header: t('payments.client'),
            cell: (p: Payment) => nameOf(p.clientId),
          },
        ]
      : []),
    {
      key: 'amount',
      header: t('payments.amount'),
      cell: (p) => <span className="font-medium">{formatCurrency(p.amount)}</span>,
    },
    {
      key: 'method',
      header: t('payments.method'),
      cell: (p) => (
        <StatusBadge tone="info">{t(`enums.paymentMethod.${p.method}`)}</StatusBadge>
      ),
    },
    {
      key: 'reference',
      header: t('payments.reference'),
      cell: (p) => p.reference || '—',
    },
    ...(staff
      ? [
          {
            key: 'actions',
            header: '',
            className: 'w-24 text-end',
            cell: (p: Payment) => (
              <div className="flex justify-end gap-0.5">
                <Button
                  variant="ghost"
                  size="icon"
                  title={t('common.edit')}
                  aria-label={t('common.edit')}
                  onClick={(ev) => {
                    ev.stopPropagation()
                    setEditing(p)
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
                    setDeleteTarget(p)
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
        title={t('nav.payments')}
        description={t('payments.subtitle')}
        actions={
          staff && (
            <Button onClick={() => setFormOpen(true)}>
              <Plus className="size-4" />
              {t('payments.new')}
            </Button>
          )
        }
      />

      {staff && selected.size > 0 && (
        <div className="flex flex-wrap items-center gap-2 rounded-xl border bg-muted/40 p-3">
          <span className="text-sm font-medium">
            {t('payments.selected', { count: selected.size })}
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
        emptyLabel={t('payments.empty')}
        selectable={staff}
        selectedIds={selected}
        onSelectionChange={setSelected}
      />

      <PaymentFormDialog open={formOpen} onOpenChange={setFormOpen} onSaved={reload} />

      <PaymentFormDialog
        open={!!editing}
        onOpenChange={(o) => !o && setEditing(null)}
        onSaved={reload}
        payment={editing}
      />

      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(o) => !o && setDeleteTarget(null)}
        title={t('payments.deleteTitle')}
        description={
          deleteTarget === 'bulk'
            ? t('payments.deleteManyConfirm', { count: selected.size })
            : t('payments.deleteConfirm')
        }
        confirmLabel={t('common.delete')}
        destructive
        onConfirm={performDelete}
      />
    </div>
  )
}
