import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Plus } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { DataTable, type Column } from '@/components/common/DataTable'
import { StatusBadge } from '@/components/common/StatusBadge'
import { Button } from '@/components/ui/button'
import { EnrollmentFormDialog } from '@/components/finance/EnrollmentFormDialog'
import { EnrollmentDetailDialog } from '@/components/finance/EnrollmentDetailDialog'
import { useAuth } from '@/core/auth/AuthContext'
import { isStaff } from '@/core/auth/roles'
import { useAsync } from '@/core/hooks/useAsync'
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

  const { data, loading, reload } = useAsync(
    () => enrollmentsApi.list(staff ? undefined : user?.id),
    [staff, user?.id],
  )
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

      <DataTable
        columns={columns}
        rows={data ?? []}
        loading={loading}
        emptyLabel={t('enrollments.empty')}
        onRowClick={setSelected}
      />

      <EnrollmentFormDialog open={formOpen} onOpenChange={setFormOpen} onSaved={reload} />

      <EnrollmentDetailDialog
        enrollment={selected}
        onOpenChange={(o) => !o && setSelected(null)}
        resolveName={nameOf}
        resolveForfait={forfaitOf}
        canRemind={staff}
      />
    </div>
  )
}
