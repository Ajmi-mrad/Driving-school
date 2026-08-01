import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Plus } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { DataTable, type Column } from '@/components/common/DataTable'
import { StatusBadge } from '@/components/common/StatusBadge'
import { Button } from '@/components/ui/button'
import { PaymentFormDialog } from '@/components/finance/PaymentFormDialog'
import { useAuth } from '@/core/auth/AuthContext'
import { isStaff } from '@/core/auth/roles'
import { useAsync } from '@/core/hooks/useAsync'
import { paymentsApi, usersApi } from '@/core/api'
import type { Payment } from '@/core/types'
import { fullName, formatCurrency, formatDate } from '@/core/format'

export function PaymentsPage() {
  const { t } = useTranslation()
  const { user, roles } = useAuth()
  const staff = isStaff(roles)
  const [formOpen, setFormOpen] = useState(false)

  const { data, loading, reload } = useAsync(
    () => paymentsApi.list(staff ? undefined : user?.id),
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

      <DataTable
        columns={columns}
        rows={data ?? []}
        loading={loading}
        emptyLabel={t('payments.empty')}
      />

      <PaymentFormDialog open={formOpen} onOpenChange={setFormOpen} onSaved={reload} />
    </div>
  )
}
