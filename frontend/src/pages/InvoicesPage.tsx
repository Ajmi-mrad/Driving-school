import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Download, Mail, Trash2, FileDown } from 'lucide-react'
import { toast } from 'sonner'
import { PageHeader } from '@/components/common/PageHeader'
import { DataTable, type Column } from '@/components/common/DataTable'
import { StatusBadge } from '@/components/common/StatusBadge'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/core/auth/AuthContext'
import { isStaff } from '@/core/auth/roles'
import { useAsync } from '@/core/hooks/useAsync'
import { useRevalidateOnFocus } from '@/hooks/useRevalidateOnFocus'
import { invoicesApi, usersApi } from '@/core/api'
import { ApiError } from '@/core/api/client'
import { saveBlob } from '@/lib/download'
import type { Invoice } from '@/core/types'
import { fullName, formatCurrency, formatDate } from '@/core/format'

export function InvoicesPage() {
  const { t } = useTranslation()
  const { user, roles } = useAuth()
  const staff = isStaff(roles)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [deleteTarget, setDeleteTarget] = useState<Invoice | 'bulk' | null>(null)
  const [busy, setBusy] = useState(false)

  const { data, loading, reload, refresh } = useAsync(
    () => invoicesApi.list(staff ? undefined : user?.id),
    [staff, user?.id],
  )
  useRevalidateOnFocus(refresh)
  const { data: users } = useAsync(
    () => (staff ? usersApi.list('CLIENT') : Promise.resolve([])),
    [staff],
  )

  const nameOf = useMemo(() => {
    const map = new Map((users ?? []).map((u) => [u.id, fullName(u)]))
    if (user) map.set(user.id, fullName(user))
    return (id: string) => map.get(id) ?? '—'
  }, [users, user])

  const rows = data ?? []
  const selectedIds = useMemo(() => [...selected], [selected])

  const withBusy = async (fn: () => Promise<void>) => {
    setBusy(true)
    try {
      await fn()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : t('common.error'))
    } finally {
      setBusy(false)
    }
  }

  const downloadOne = (inv: Invoice) =>
    withBusy(async () => {
      const blob = await invoicesApi.pdf(inv.id)
      saveBlob(blob, `${inv.number}.pdf`)
    })

  const downloadZip = () =>
    withBusy(async () => {
      const blob = await invoicesApi.zip(selectedIds)
      saveBlob(blob, 'documents.zip')
    })

  const sendEmail = (ids: string[]) =>
    withBusy(async () => {
      const res = await invoicesApi.email(ids)
      toast.success(t('invoices.emailSentToast', { sent: res.sent, skipped: res.skipped }))
    })

  const performDelete = () =>
    withBusy(async () => {
      const ids = deleteTarget === 'bulk' ? selectedIds : deleteTarget ? [deleteTarget.id] : []
      await invoicesApi.removeMany(ids)
      toast.success(t('invoices.deletedToast', { count: ids.length }))
      if (deleteTarget === 'bulk') setSelected(new Set())
      setDeleteTarget(null)
      reload()
    })

  const deleteCount = deleteTarget === 'bulk' ? selected.size : 1

  const columns: Column<Invoice>[] = [
    { key: 'date', header: t('invoices.date'), cell: (i) => formatDate(i.issuedAt) },
    {
      key: 'number',
      header: t('invoices.number'),
      cell: (i) => <span className="font-medium">{i.number}</span>,
    },
    {
      key: 'type',
      header: t('invoices.type'),
      cell: (i) => (
        <StatusBadge tone={i.type === 'INVOICE' ? 'info' : 'success'}>
          {t(`enums.invoiceType.${i.type}`)}
        </StatusBadge>
      ),
    },
    ...(staff
      ? [
          {
            key: 'client',
            header: t('invoices.client'),
            cell: (i: Invoice) => nameOf(i.clientId),
          },
        ]
      : []),
    {
      key: 'amount',
      header: t('invoices.amount'),
      cell: (i) => formatCurrency(i.amount),
    },
    {
      key: 'actions',
      header: '',
      className: 'w-32 text-end',
      cell: (i) => (
        <div className="flex justify-end gap-0.5">
          <Button
            variant="ghost"
            size="icon"
            disabled={busy}
            title={t('invoices.download')}
            aria-label={t('invoices.download')}
            onClick={(e) => {
              e.stopPropagation()
              downloadOne(i)
            }}
          >
            <FileDown className="size-4" />
          </Button>
          {staff && (
            <>
              <Button
                variant="ghost"
                size="icon"
                disabled={busy}
                title={t('invoices.sendEmail')}
                aria-label={t('invoices.sendEmail')}
                onClick={(e) => {
                  e.stopPropagation()
                  sendEmail([i.id])
                }}
              >
                <Mail className="size-4" />
              </Button>
              <Button
                variant="ghost"
                size="icon"
                disabled={busy}
                className="text-destructive"
                title={t('common.delete')}
                aria-label={t('common.delete')}
                onClick={(e) => {
                  e.stopPropagation()
                  setDeleteTarget(i)
                }}
              >
                <Trash2 className="size-4" />
              </Button>
            </>
          )}
        </div>
      ),
    },
  ]

  return (
    <div className="space-y-4">
      <PageHeader title={t('nav.invoices')} description={t('invoices.subtitle')} />

      {staff && selected.size > 0 && (
        <div className="flex flex-wrap items-center gap-2 rounded-xl border bg-muted/40 p-3">
          <span className="text-sm font-medium">
            {t('invoices.selected', { count: selected.size })}
          </span>
          <div className="ms-auto flex flex-wrap gap-2">
            <Button variant="outline" size="sm" disabled={busy} onClick={downloadZip}>
              <Download className="size-4" />
              {t('invoices.downloadZip')}
            </Button>
            <Button variant="outline" size="sm" disabled={busy} onClick={() => sendEmail(selectedIds)}>
              <Mail className="size-4" />
              {t('invoices.sendEmail')}
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={busy}
              className="text-destructive"
              onClick={() => setDeleteTarget('bulk')}
            >
              <Trash2 className="size-4" />
              {t('common.delete')}
            </Button>
          </div>
        </div>
      )}

      <DataTable
        columns={columns}
        rows={rows}
        loading={loading}
        emptyLabel={t('invoices.empty')}
        selectable={staff}
        selectedIds={selected}
        onSelectionChange={setSelected}
      />

      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(o) => !o && setDeleteTarget(null)}
        title={t('invoices.deleteTitle')}
        description={t('invoices.deleteConfirm', { count: deleteCount })}
        confirmLabel={t('common.delete')}
        destructive
        onConfirm={performDelete}
      />
    </div>
  )
}
