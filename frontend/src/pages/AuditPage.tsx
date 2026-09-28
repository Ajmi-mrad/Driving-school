import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { RefreshCw } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { DataTable, type Column } from '@/components/common/DataTable'
import { StatusBadge } from '@/components/common/StatusBadge'
import { Button } from '@/components/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useAsync } from '@/core/hooks/useAsync'
import { useRevalidateOnFocus } from '@/hooks/useRevalidateOnFocus'
import { auditApi, usersApi } from '@/core/api'
import { AUDIT_ACTIONS, AUDIT_ENTITIES, type AuditEvent } from '@/core/types'
import { fullName, formatDateTime } from '@/core/format'

type ActionFilter = (typeof AUDIT_ACTIONS)[number] | 'ALL'
type EntityFilter = (typeof AUDIT_ENTITIES)[number] | 'ALL'

/** Colour cue per action: green create, red destructive, amber account changes. */
const ACTION_TONE: Record<string, 'success' | 'info' | 'danger' | 'warning' | 'neutral'> = {
  CREATED: 'success',
  UPDATED: 'info',
  DELETED: 'danger',
  VOIDED: 'danger',
  DEACTIVATED: 'warning',
  PASSWORD_RESET: 'warning',
}

export function AuditPage() {
  const { t } = useTranslation()
  const [action, setAction] = useState<ActionFilter>('ALL')
  const [entityType, setEntityType] = useState<EntityFilter>('ALL')

  const { data, loading, refresh } = useAsync(
    () =>
      auditApi.list({
        action: action === 'ALL' ? undefined : action,
        entityType: entityType === 'ALL' ? undefined : entityType,
      }),
    [action, entityType],
  )
  useRevalidateOnFocus(refresh)

  // Resolve the actor's Keycloak sub to a display name (owner can list users).
  const { data: users } = useAsync(() => usersApi.list(), [])
  const nameOf = useMemo(() => {
    const map = new Map((users ?? []).map((u) => [u.id, fullName(u)]))
    return (sub: string) => map.get(sub) ?? (sub === 'system' ? t('audit.system') : sub)
  }, [users, t])

  const rows = data ?? []

  const columns: Column<AuditEvent>[] = [
    {
      key: 'when',
      header: t('audit.when'),
      className: 'whitespace-nowrap',
      cell: (e) => formatDateTime(e.occurredAt),
    },
    { key: 'who', header: t('audit.who'), cell: (e) => nameOf(e.actor) },
    {
      key: 'action',
      header: t('audit.action'),
      cell: (e) => (
        <StatusBadge tone={ACTION_TONE[e.action] ?? 'neutral'}>
          {t(`enums.auditAction.${e.action}`, { defaultValue: e.action })}
        </StatusBadge>
      ),
    },
    {
      key: 'area',
      header: t('audit.area'),
      cell: (e) => t(`enums.auditEntity.${e.entityType}`, { defaultValue: e.entityType }),
    },
    {
      key: 'detail',
      header: t('audit.detail'),
      cell: (e) => <span className="text-muted-foreground">{e.summary || '—'}</span>,
    },
  ]

  return (
    <div className="space-y-4">
      <PageHeader
        title={t('nav.audit')}
        description={t('audit.subtitle')}
        actions={
          <Button variant="outline" size="sm" onClick={refresh}>
            <RefreshCw className="size-4" />
            {t('common.refresh')}
          </Button>
        }
      />

      <div className="flex flex-wrap gap-2">
        <Select value={entityType} onValueChange={(v) => setEntityType(v as EntityFilter)}>
          <SelectTrigger className="w-44">
            <SelectValue placeholder={t('audit.filterArea')} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">{t('common.all')}</SelectItem>
            {AUDIT_ENTITIES.map((en) => (
              <SelectItem key={en} value={en}>
                {t(`enums.auditEntity.${en}`)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={action} onValueChange={(v) => setAction(v as ActionFilter)}>
          <SelectTrigger className="w-44">
            <SelectValue placeholder={t('audit.filterAction')} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">{t('common.all')}</SelectItem>
            {AUDIT_ACTIONS.map((a) => (
              <SelectItem key={a} value={a}>
                {t(`enums.auditAction.${a}`)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <DataTable
        columns={columns}
        rows={rows}
        loading={loading}
        emptyLabel={t('audit.empty')}
      />
    </div>
  )
}