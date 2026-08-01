import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { PageHeader } from '@/components/common/PageHeader'
import { DataTable, type Column } from '@/components/common/DataTable'
import { StatusBadge } from '@/components/common/StatusBadge'
import { useAuth } from '@/core/auth/AuthContext'
import { isStaff } from '@/core/auth/roles'
import { useAsync } from '@/core/hooks/useAsync'
import { invoicesApi, usersApi } from '@/core/api'
import type { Invoice } from '@/core/types'
import { fullName, formatCurrency, formatDate } from '@/core/format'

export function InvoicesPage() {
  const { t } = useTranslation()
  const { user, roles } = useAuth()
  const staff = isStaff(roles)

  const { data, loading } = useAsync(
    () => invoicesApi.list(staff ? undefined : user?.id),
    [staff, user?.id],
  )
  const { data: users } = useAsync(
    () => (staff ? usersApi.list('CLIENT') : Promise.resolve([])),
    [staff],
  )

  const nameOf = useMemo(() => {
    const map = new Map((users ?? []).map((u) => [u.id, fullName(u)]))
    if (user) map.set(user.id, fullName(user))
    return (id: string) => map.get(id) ?? '—'
  }, [users, user])

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
  ]

  return (
    <div className="space-y-6">
      <PageHeader title={t('nav.invoices')} description={t('invoices.subtitle')} />
      <DataTable
        columns={columns}
        rows={data ?? []}
        loading={loading}
        emptyLabel={t('invoices.empty')}
      />
    </div>
  )
}
