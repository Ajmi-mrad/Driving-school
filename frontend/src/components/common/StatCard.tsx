import type { LucideIcon } from 'lucide-react'
import { Card, CardContent } from '@/components/ui/card'
import { cn } from '@/lib/utils'

export function StatCard({
  label,
  value,
  icon: Icon,
  hint,
  tone = 'primary',
}: {
  label: string
  value: string | number
  icon: LucideIcon
  hint?: string
  tone?: 'primary' | 'success' | 'warning' | 'danger'
}) {
  const toneClass = {
    primary: 'bg-primary/10 text-primary',
    success: 'bg-success/15 text-success',
    warning: 'bg-warning/15 text-warning',
    danger: 'bg-destructive/15 text-destructive',
  }[tone]

  return (
    <Card>
      <CardContent className="flex items-center gap-4 p-5">
        <div className={cn('flex size-11 items-center justify-center rounded-lg', toneClass)}>
          <Icon className="size-5" />
        </div>
        <div className="min-w-0">
          <p className="text-sm text-muted-foreground">{label}</p>
          <p className="text-2xl font-semibold tracking-tight">{value}</p>
          {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
        </div>
      </CardContent>
    </Card>
  )
}