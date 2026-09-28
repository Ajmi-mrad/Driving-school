/**
 * Pure date-math helpers (no Intl, no DOM). Reusable by React Native.
 * Weeks start on Monday.
 */

export function addDays(date: Date, n: number): Date {
  const d = new Date(date)
  d.setDate(d.getDate() + n)
  return d
}

export function startOfDay(date: Date): Date {
  const d = new Date(date)
  d.setHours(0, 0, 0, 0)
  return d
}

/** Monday of the week containing `date`. */
export function startOfWeek(date: Date): Date {
  const d = startOfDay(date)
  const day = (d.getDay() + 6) % 7 // 0 = Monday
  return addDays(d, -day)
}

export function weekDays(date: Date): Date[] {
  const start = startOfWeek(date)
  return Array.from({ length: 7 }, (_, i) => addDays(start, i))
}

export function isSameDay(a: Date, b: Date): boolean {
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  )
}

export function isToday(date: Date): boolean {
  return isSameDay(date, new Date())
}

/** True if an ISO datetime is in the past. */
export function isExpired(iso: string): boolean {
  return new Date(iso) < new Date()
}

/** Fractional hour of day, e.g. 14:30 -> 14.5. */
export function hourOfDay(date: Date): number {
  return date.getHours() + date.getMinutes() / 60
}

/** "YYYY-MM-DD" in local time (for <input type="date">). */
export function toDateInput(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

/** "HH:MM" in local time (for <input type="time">). */
export function toTimeInput(date: Date): string {
  const h = String(date.getHours()).padStart(2, '0')
  const m = String(date.getMinutes()).padStart(2, '0')
  return `${h}:${m}`
}

/** Combine a "YYYY-MM-DD" and "HH:MM" into a local Date. */
export function fromDateTimeInput(dateStr: string, timeStr: string): Date {
  const [y, m, d] = dateStr.split('-').map(Number)
  const [hh, mm] = timeStr.split(':').map(Number)
  return new Date(y, m - 1, d, hh, mm)
}
