/**
 * Formatting helpers. Framework-agnostic — uses only `Intl` (available in
 * React Native too) and reads the active language from the i18next
 * singleton so currency/dates follow the selected locale (fr / en / ar).
 */
import i18n from 'i18next'
import { intlLocaleFor } from './i18n/languages'

function locale(): string {
  return intlLocaleFor(i18n.language || 'fr')
}

export function formatCurrency(amount: number): string {
  return new Intl.NumberFormat(locale(), {
    style: 'currency',
    currency: 'EUR',
  }).format(amount)
}

export function formatDate(iso: string): string {
  return new Intl.DateTimeFormat(locale(), {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  }).format(new Date(iso))
}

export function formatDateTime(iso: string): string {
  return new Intl.DateTimeFormat(locale(), {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(iso))
}

export function formatTime(iso: string): string {
  return new Intl.DateTimeFormat(locale(), {
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(iso))
}

export function formatShortDateTime(iso: string): string {
  return new Intl.DateTimeFormat(locale(), {
    day: '2-digit',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(iso))
}

/** Localized short relative time, e.g. "5 min ago", "il y a 2 h", "أمس". */
export function formatRelativeShort(iso: string): string {
  const rtf = new Intl.RelativeTimeFormat(locale(), { numeric: 'auto', style: 'short' })
  const min = Math.round((new Date(iso).getTime() - Date.now()) / 60000)
  if (Math.abs(min) < 60) return rtf.format(min, 'minute')
  const hours = Math.round(min / 60)
  if (Math.abs(hours) < 24) return rtf.format(hours, 'hour')
  const days = Math.round(hours / 24)
  if (Math.abs(days) < 7) return rtf.format(days, 'day')
  return formatDayMonth(new Date(iso))
}

export function formatWeekday(date: Date): string {
  return new Intl.DateTimeFormat(locale(), { weekday: 'short' }).format(date)
}

export function formatDayMonth(date: Date): string {
  return new Intl.DateTimeFormat(locale(), {
    day: '2-digit',
    month: 'short',
  }).format(date)
}

export function formatMonthYear(date: Date): string {
  return new Intl.DateTimeFormat(locale(), {
    month: 'long',
    year: 'numeric',
  }).format(date)
}

interface NameParts {
  firstName: string
  lastName: string
  username?: string
  email?: string
}

/** Full name, falling back to username / email when names are unset. */
export function fullName(p: NameParts): string {
  const name = `${p.firstName} ${p.lastName}`.trim()
  return name || p.username || p.email || ''
}

/** Up to two uppercase initials, falling back to username / email. */
export function initials(p: NameParts): string {
  const fromName = `${p.firstName.charAt(0)}${p.lastName.charAt(0)}`.trim()
  if (fromName) return fromName.toUpperCase()
  const fallback = p.username || p.email || ''
  return fallback.slice(0, 2).toUpperCase()
}
