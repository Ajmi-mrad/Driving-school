import { useTranslation } from 'react-i18next'
import { Check, FlaskConical } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { ROLES } from '@/core/types'
import { useAuth } from '@/core/auth/AuthContext'

/** Dev-only helper to preview each role's experience. */
export function RoleSwitcher() {
  const { t } = useTranslation()
  const { roles, loginAs } = useAuth()
  const current = roles[0]

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="outline" size="sm" className="gap-2">
          <FlaskConical className="size-4 text-warning" />
          <span className="hidden sm:inline">
            {current ? t(`enums.role.${current}`) : t('roles.label')}
          </span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuLabel>{t('roles.previewByRole')}</DropdownMenuLabel>
        <DropdownMenuSeparator />
        {ROLES.map((role) => (
          <DropdownMenuItem
            key={role}
            onClick={() => loginAs(role)}
            className="justify-between"
          >
            {t(`enums.role.${role}`)}
            {current === role && <Check className="size-4" />}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}