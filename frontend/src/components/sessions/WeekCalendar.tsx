import { useTranslation } from 'react-i18next'
import type { Session } from '@/core/types'
import { hourOfDay, isSameDay, isToday } from '@/core/datetime'
import { formatDayMonth, formatWeekday } from '@/core/format'
import { cn } from '@/lib/utils'
import { SESSION_STATUS_TONE } from '@/lib/tones'

const START_HOUR = 7
const END_HOUR = 21
const ROW_H = 48 // px per hour

const TONE_BG: Record<string, string> = {
  neutral: 'bg-muted text-muted-foreground border-border',
  success: 'bg-success/15 text-success border-success/30',
  warning: 'bg-warning/15 text-warning border-warning/30',
  danger: 'bg-destructive/15 text-destructive border-destructive/30',
  info: 'bg-info/15 text-info border-info/30',
}

export function WeekCalendar({
  days,
  sessions,
  onSelect,
  label,
}: {
  days: Date[]
  sessions: Session[]
  onSelect: (s: Session) => void
  label: (s: Session) => string
}) {
  const { t } = useTranslation()
  const hours = Array.from({ length: END_HOUR - START_HOUR }, (_, i) => START_HOUR + i)

  return (
    <div className="overflow-x-auto rounded-xl border bg-card">
      <div className="min-w-[720px]">
        {/* Header */}
        <div className="grid grid-cols-[3.5rem_repeat(7,1fr)] border-b">
          <div />
          {days.map((day) => (
            <div
              key={day.toISOString()}
              className={cn(
                'border-s p-2 text-center',
                isToday(day) && 'bg-primary/5',
              )}
            >
              <p className="text-xs uppercase text-muted-foreground">
                {formatWeekday(day)}
              </p>
              <p
                className={cn(
                  'text-sm font-medium',
                  isToday(day) && 'text-primary',
                )}
              >
                {formatDayMonth(day)}
              </p>
            </div>
          ))}
        </div>

        {/* Body */}
        <div className="grid grid-cols-[3.5rem_repeat(7,1fr)]">
          {/* Time gutter */}
          <div>
            {hours.map((h) => (
              <div
                key={h}
                className="relative border-b text-end pe-1"
                style={{ height: ROW_H }}
              >
                <span className="absolute -top-2 end-1 text-xs text-muted-foreground">
                  {String(h).padStart(2, '0')}:00
                </span>
              </div>
            ))}
          </div>

          {/* Day columns */}
          {days.map((day) => {
            const daySessions = sessions.filter((s) =>
              isSameDay(new Date(s.startTime), day),
            )
            return (
              <div
                key={day.toISOString()}
                className={cn('relative border-s', isToday(day) && 'bg-primary/5')}
                style={{ height: ROW_H * hours.length }}
              >
                {hours.map((h) => (
                  <div key={h} className="border-b" style={{ height: ROW_H }} />
                ))}

                {daySessions.map((s) => {
                  const start = hourOfDay(new Date(s.startTime))
                  const end = hourOfDay(new Date(s.endTime))
                  // Clamp to the visible range so sessions crossing the top or
                  // bottom edge aren't drawn oversized or shifted.
                  const visibleStart = Math.max(start, START_HOUR)
                  const visibleEnd = Math.min(end, END_HOUR)
                  const top = (visibleStart - START_HOUR) * ROW_H
                  const height = Math.max((visibleEnd - visibleStart) * ROW_H, 22)
                  const tone = SESSION_STATUS_TONE[s.status]
                  return (
                    <button
                      key={s.id}
                      type="button"
                      onClick={() => onSelect(s)}
                      className={cn(
                        'absolute overflow-hidden rounded-md border p-1 text-start text-xs leading-tight',
                        TONE_BG[tone],
                      )}
                      style={{
                        top,
                        height,
                        insetInlineStart: 3,
                        insetInlineEnd: 3,
                      }}
                      title={label(s)}
                    >
                      <span className="block font-medium">
                        {t(`enums.sessionType.${s.type}`)}
                      </span>
                      <span className="block truncate opacity-80">{label(s)}</span>
                    </button>
                  )
                })}
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}
