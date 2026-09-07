import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { MoreHorizontal, Plus, Wrench } from 'lucide-react'
import { toast } from 'sonner'
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
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { VehicleFormDialog } from '@/components/vehicles/VehicleFormDialog'
import { MaintenanceDialog } from '@/components/vehicles/MaintenanceDialog'
import { useAuth } from '@/core/auth/AuthContext'
import { isStaff } from '@/core/auth/roles'
import { useAsync } from '@/core/hooks/useAsync'
import { useRevalidateOnFocus } from '@/hooks/useRevalidateOnFocus'
import { vehiclesApi } from '@/core/api'
import {
  FUEL_TYPES,
  VEHICLE_STATUSES,
  type FuelType,
  type Vehicle,
  type VehicleStatus,
} from '@/core/types'
import { formatDate } from '@/core/format'
import { isExpired } from '@/core/datetime'
import { VEHICLE_STATUS_TONE } from '@/lib/tones'

export function VehiclesPage() {
  const { t } = useTranslation()
  const { roles } = useAuth()
  const canWrite = isStaff(roles)

  const [status, setStatus] = useState<VehicleStatus | 'ALL'>('ALL')
  const [fuel, setFuel] = useState<FuelType | 'ALL'>('ALL')
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<Vehicle | null>(null)
  const [maintenanceFor, setMaintenanceFor] = useState<Vehicle | null>(null)

  const { data, loading, reload, refresh } = useAsync(
    () =>
      vehiclesApi.list({
        status: status === 'ALL' ? undefined : status,
        fuelType: fuel === 'ALL' ? undefined : fuel,
      }),
    [status, fuel],
  )
  useRevalidateOnFocus(refresh)

  const changeStatus = async (v: Vehicle, next: VehicleStatus) => {
    await vehiclesApi.setStatus(v.id, next)
    toast.success(t('vehicles.statusToast'))
    reload()
  }

  const columns: Column<Vehicle>[] = [
    {
      key: 'vehicle',
      header: t('vehicles.brand'),
      cell: (v) => (
        <div>
          <p className="font-medium">
            {v.brand} {v.model}
          </p>
          <p className="text-xs text-muted-foreground">{v.registrationNumber}</p>
        </div>
      ),
    },
    {
      key: 'spec',
      header: t('vehicles.fuel'),
      cell: (v) => (
        <span className="text-sm text-muted-foreground">
          {t(`enums.gearbox.${v.gearboxType}`)} · {t(`enums.fuel.${v.fuelType}`)}
        </span>
      ),
    },
    {
      key: 'status',
      header: t('common.status'),
      cell: (v) => (
        <StatusBadge tone={VEHICLE_STATUS_TONE[v.status]}>
          {t(`enums.vehicleStatus.${v.status}`)}
        </StatusBadge>
      ),
    },
    {
      key: 'mileage',
      header: t('vehicles.mileage'),
      cell: (v) => `${v.mileage.toLocaleString()} km`,
    },
    {
      key: 'inspection',
      header: t('vehicles.inspectionExpiry'),
      cell: (v) =>
        isExpired(v.technicalInspectionExpiry) ? (
          <StatusBadge tone="danger">{t('vehicles.expired')}</StatusBadge>
        ) : (
          <span className="text-sm">{formatDate(v.technicalInspectionExpiry)}</span>
        ),
    },
    {
      key: 'actions',
      header: '',
      className: 'w-12 text-end',
      cell: (v) => (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="icon">
              <MoreHorizontal className="size-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem onClick={() => setMaintenanceFor(v)}>
              <Wrench className="size-4" />
              {t('vehicles.maintenance')}
            </DropdownMenuItem>
            {canWrite && (
              <>
                <DropdownMenuItem
                  onClick={() => {
                    setEditing(v)
                    setFormOpen(true)
                  }}
                >
                  {t('common.edit')}
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuLabel className="text-xs font-normal text-muted-foreground">
                  {t('vehicles.changeStatus')}
                </DropdownMenuLabel>
                {VEHICLE_STATUSES.filter((s) => s !== v.status).map((s) => (
                  <DropdownMenuItem key={s} onClick={() => changeStatus(v, s)}>
                    {t(`enums.vehicleStatus.${s}`)}
                  </DropdownMenuItem>
                ))}
              </>
            )}
          </DropdownMenuContent>
        </DropdownMenu>
      ),
    },
  ]

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('nav.vehicles')}
        description={t('vehicles.subtitle')}
        actions={
          canWrite && (
            <Button
              onClick={() => {
                setEditing(null)
                setFormOpen(true)
              }}
            >
              <Plus className="size-4" />
              {t('vehicles.new')}
            </Button>
          )
        }
      />

      <div className="flex flex-col gap-3 sm:flex-row">
        <Select value={status} onValueChange={(v) => setStatus(v as VehicleStatus | 'ALL')}>
          <SelectTrigger className="sm:w-52">
            <SelectValue placeholder={t('vehicles.filterStatus')} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">{t('common.all')}</SelectItem>
            {VEHICLE_STATUSES.map((s) => (
              <SelectItem key={s} value={s}>
                {t(`enums.vehicleStatus.${s}`)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={fuel} onValueChange={(v) => setFuel(v as FuelType | 'ALL')}>
          <SelectTrigger className="sm:w-52">
            <SelectValue placeholder={t('vehicles.filterFuel')} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">{t('common.all')}</SelectItem>
            {FUEL_TYPES.map((f) => (
              <SelectItem key={f} value={f}>
                {t(`enums.fuel.${f}`)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <DataTable
        columns={columns}
        rows={data ?? []}
        loading={loading}
        emptyLabel={t('vehicles.empty')}
      />

      <VehicleFormDialog
        open={formOpen}
        onOpenChange={setFormOpen}
        vehicle={editing}
        onSaved={reload}
      />

      <MaintenanceDialog
        open={!!maintenanceFor}
        onOpenChange={(o) => !o && setMaintenanceFor(null)}
        vehicle={maintenanceFor}
        canWrite={canWrite}
      />
    </div>
  )
}
