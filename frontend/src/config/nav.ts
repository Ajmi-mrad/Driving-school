import {
  Bell,
  CalendarDays,
  Car,
  CreditCard,
  FileText,
  LayoutDashboard,
  MessageSquare,
  Package,
  Settings,
  Users,
  Wallet,
  type LucideIcon,
} from 'lucide-react'
import type { Role } from '@/core/types'
import { hasAnyRole, isStaff, permissions } from '@/core/auth/roles'

export interface NavItem {
  to: string
  /** i18n key, e.g. 'nav.dashboard'. */
  labelKey: string
  icon: LucideIcon
  visible: (roles: Role[]) => boolean
}

const always = () => true

export const NAV_ITEMS: NavItem[] = [
  { to: '/', labelKey: 'nav.dashboard', icon: LayoutDashboard, visible: always },
  { to: '/users', labelKey: 'nav.users', icon: Users, visible: permissions.manageUsers },
  {
    to: '/vehicles',
    labelKey: 'nav.vehicles',
    icon: Car,
    visible: (r) => hasAnyRole(r, 'OWNER', 'SECRETARY', 'MONITOR'),
  },
  { to: '/sessions', labelKey: 'nav.sessions', icon: CalendarDays, visible: always },
  { to: '/forfaits', labelKey: 'nav.forfaits', icon: Package, visible: always },
  {
    to: '/enrollments',
    labelKey: 'nav.enrollments',
    icon: FileText,
    visible: (r) => hasAnyRole(r, 'OWNER', 'SECRETARY', 'CLIENT'),
  },
  {
    to: '/payments',
    labelKey: 'nav.payments',
    icon: CreditCard,
    visible: (r) => hasAnyRole(r, 'OWNER', 'SECRETARY', 'CLIENT'),
  },
  {
    to: '/invoices',
    labelKey: 'nav.invoices',
    icon: Wallet,
    visible: (r) => hasAnyRole(r, 'OWNER', 'SECRETARY', 'CLIENT'),
  },
  { to: '/messages', labelKey: 'nav.messages', icon: MessageSquare, visible: permissions.chat },
  { to: '/notifications', labelKey: 'nav.notifications', icon: Bell, visible: always },
  { to: '/settings', labelKey: 'nav.settings', icon: Settings, visible: isStaff },
]