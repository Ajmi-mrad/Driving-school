import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { Area, AreaChart, CartesianGrid, XAxis, Cell, Pie, PieChart } from 'recharts'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from '@/components/ui/chart'
import { addDays, isSameDay, startOfDay } from '@/core/datetime'
import { formatDayMonth } from '@/core/format'
import type { Session, Vehicle, VehicleStatus } from '@/core/types'
import { VEHICLE_STATUSES } from '@/core/types'

/** Sessions per day over the last `days` days (cancelled excluded). */
export function SessionsAreaChart({
  sessions,
  days = 14,
}: {
  sessions: Session[]
  days?: number
}) {
  const { t } = useTranslation()

  const data = useMemo(() => {
    const today = startOfDay(new Date())
    return Array.from({ length: days }, (_, i) => {
      const day = addDays(today, i - (days - 1))
      const count = sessions.filter(
        (s) => s.status !== 'CANCELLED' && isSameDay(new Date(s.startTime), day),
      ).length
      return { label: formatDayMonth(day), count }
    })
  }, [sessions, days])

  const config = {
    count: { label: t('dashboard.sessionsSeries'), color: 'var(--chart-1)' },
  } satisfies ChartConfig

  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle>{t('dashboard.sessionsChartTitle')}</CardTitle>
        <CardDescription>{t('dashboard.sessionsChartDesc')}</CardDescription>
      </CardHeader>
      <CardContent>
        <ChartContainer config={config} className="aspect-auto h-56 w-full">
          <AreaChart data={data} margin={{ left: 4, right: 4, top: 8 }}>
            <defs>
              <linearGradient id="fillSessions" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="var(--color-count)" stopOpacity={0.7} />
                <stop offset="95%" stopColor="var(--color-count)" stopOpacity={0.05} />
              </linearGradient>
            </defs>
            <CartesianGrid vertical={false} />
            <XAxis
              dataKey="label"
              tickLine={false}
              axisLine={false}
              tickMargin={8}
              minTickGap={24}
            />
            <ChartTooltip cursor={false} content={<ChartTooltipContent />} />
            <Area
              dataKey="count"
              type="natural"
              fill="url(#fillSessions)"
              stroke="var(--color-count)"
              strokeWidth={2}
            />
          </AreaChart>
        </ChartContainer>
      </CardContent>
    </Card>
  )
}

const STATUS_COLORS: Record<VehicleStatus, string> = {
  AVAILABLE: 'var(--success)',
  IN_USE: 'var(--chart-1)',
  MAINTENANCE: 'var(--warning)',
  OUT_OF_SERVICE: 'var(--destructive)',
}

/** Donut of the fleet broken down by vehicle status. */
export function FleetStatusChart({ vehicles }: { vehicles: Vehicle[] }) {
  const { t } = useTranslation()

  const data = useMemo(
    () =>
      VEHICLE_STATUSES.map((status) => ({
        status,
        label: t(`enums.vehicleStatus.${status}`),
        value: vehicles.filter((v) => v.status === status).length,
        fill: STATUS_COLORS[status],
      })).filter((d) => d.value > 0),
    [vehicles, t],
  )

  const config = Object.fromEntries(
    VEHICLE_STATUSES.map((status) => [
      status,
      { label: t(`enums.vehicleStatus.${status}`), color: STATUS_COLORS[status] },
    ]),
  ) satisfies ChartConfig

  return (
    <Card className="flex h-full flex-col">
      <CardHeader>
        <CardTitle>{t('dashboard.fleetChartTitle')}</CardTitle>
        <CardDescription>{t('dashboard.fleetChartDesc')}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-1 flex-col justify-center pb-4">
        {data.length === 0 ? (
          <div className="flex h-56 items-center justify-center text-sm text-muted-foreground">
            {t('dashboard.noData')}
          </div>
        ) : (
          <ChartContainer config={config} className="mx-auto aspect-square h-56">
            <PieChart>
              <ChartTooltip cursor={false} content={<ChartTooltipContent nameKey="label" />} />
              <Pie data={data} dataKey="value" nameKey="label" innerRadius={55} strokeWidth={4}>
                {data.map((entry) => (
                  <Cell key={entry.status} fill={entry.fill} />
                ))}
              </Pie>
            </PieChart>
          </ChartContainer>
        )}
        <div className="mt-2 flex flex-wrap justify-center gap-x-4 gap-y-1">
          {data.map((entry) => (
            <span key={entry.status} className="flex items-center gap-1.5 text-xs text-muted-foreground">
              <span className="size-2.5 rounded-full" style={{ backgroundColor: entry.fill }} />
              {entry.label} ({entry.value})
            </span>
          ))}
        </div>
      </CardContent>
    </Card>
  )
}