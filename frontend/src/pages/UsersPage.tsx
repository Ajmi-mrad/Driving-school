import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { KeyRound, MoreHorizontal, Plus, UserX } from 'lucide-react'
import { toast } from 'sonner'
import { PageHeader } from '@/components/common/PageHeader'
import { DataTable, type Column } from '@/components/common/DataTable'
import { StatusBadge } from '@/components/common/StatusBadge'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { UserFormDialog } from '@/components/users/UserFormDialog'
import { useAsync } from '@/core/hooks/useAsync'
import { usersApi } from '@/core/api'
import { ROLES, type Role, type User } from '@/core/types'
import { fullName, initials } from '@/core/format'

export function UsersPage() {
  const { t } = useTranslation()
  const [roleFilter, setRoleFilter] = useState<Role | 'ALL'>('ALL')
  const [query, setQuery] = useState('')
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<User | null>(null)
  const [toDeactivate, setToDeactivate] = useState<User | null>(null)

  const { data, loading, reload } = useAsync(
    () => usersApi.list(roleFilter === 'ALL' ? undefined : roleFilter),
    [roleFilter],
  )

  const rows = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return data ?? []
    return (data ?? []).filter((u) =>
      [u.firstName, u.lastName, u.username, u.email]
        .join(' ')
        .toLowerCase()
        .includes(q),
    )
  }, [data, query])

  const openCreate = () => {
    setEditing(null)
    setFormOpen(true)
  }
  const openEdit = (user: User) => {
    setEditing(user)
    setFormOpen(true)
  }

  const deactivate = async (user: User) => {
    await usersApi.deactivate(user.id)
    toast.success(t('users.deactivatedToast'))
    reload()
  }

  const resetPassword = async (user: User) => {
    await usersApi.resetPassword(user.id)
    toast.success(t('users.resetPasswordSent'))
  }

  const columns: Column<User>[] = [
    {
      key: 'name',
      header: t('users.name'),
      cell: (u) => (
        <div className="flex items-center gap-3">
          <Avatar className="size-8">
            <AvatarFallback className="bg-primary/10 text-xs text-primary">
              {initials(u)}
            </AvatarFallback>
          </Avatar>
          <div>
            <p className="font-medium">{fullName(u)}</p>
            <p className="text-xs text-muted-foreground">@{u.username}</p>
          </div>
        </div>
      ),
    },
    { key: 'email', header: t('users.email'), cell: (u) => u.email },
    {
      key: 'roles',
      header: t('users.roles'),
      cell: (u) => (
        <div className="flex flex-wrap gap-1">
          {u.roles.map((r) => (
            <StatusBadge key={r} tone="info">
              {t(`enums.role.${r}`)}
            </StatusBadge>
          ))}
        </div>
      ),
    },
    {
      key: 'status',
      header: t('common.status'),
      cell: (u) => (
        <StatusBadge tone={u.active ? 'success' : 'neutral'}>
          {u.active ? t('common.active') : t('common.inactive')}
        </StatusBadge>
      ),
    },
    {
      key: 'actions',
      header: '',
      className: 'w-12 text-end',
      cell: (u) => (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="icon" onClick={(e) => e.stopPropagation()}>
              <MoreHorizontal className="size-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem onClick={() => openEdit(u)}>
              {t('common.edit')}
            </DropdownMenuItem>
            <DropdownMenuItem onClick={() => resetPassword(u)}>
              <KeyRound className="size-4" />
              {t('users.resetPassword')}
            </DropdownMenuItem>
            {u.active && (
              <DropdownMenuItem
                className="text-destructive"
                onClick={() => setToDeactivate(u)}
              >
                <UserX className="size-4" />
                {t('users.deactivate')}
              </DropdownMenuItem>
            )}
          </DropdownMenuContent>
        </DropdownMenu>
      ),
    },
  ]

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('nav.users')}
        description={t('users.subtitle')}
        actions={
          <Button onClick={openCreate}>
            <Plus className="size-4" />
            {t('users.new')}
          </Button>
        }
      />

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <Input
          placeholder={t('common.search')}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          className="sm:max-w-xs"
        />
        <Select
          value={roleFilter}
          onValueChange={(v) => setRoleFilter(v as Role | 'ALL')}
        >
          <SelectTrigger className="sm:w-52">
            <SelectValue placeholder={t('users.filterRole')} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">{t('common.all')}</SelectItem>
            {ROLES.map((r) => (
              <SelectItem key={r} value={r}>
                {t(`enums.role.${r}`)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <DataTable
        columns={columns}
        rows={rows}
        loading={loading}
        emptyLabel={t('users.empty')}
      />

      <UserFormDialog
        open={formOpen}
        onOpenChange={setFormOpen}
        user={editing}
        onSaved={reload}
      />

      <ConfirmDialog
        open={!!toDeactivate}
        onOpenChange={(o) => !o && setToDeactivate(null)}
        title={t('users.deactivate')}
        description={t('users.deactivateConfirm')}
        confirmLabel={t('users.deactivate')}
        destructive
        onConfirm={() => toDeactivate && deactivate(toDeactivate)}
      />
    </div>
  )
}
