import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Plus, Wrench } from 'lucide-react'
import { toast } from 'sonner'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useAsync } from '@/core/hooks/useAsync'
import { vehiclesApi, type MaintenanceInput } from '@/core/api'
import { MAINTENANCE_TYPES, type MaintenanceType, type Vehicle } from '@/core/types'
import { formatCurrency, formatDate } from '@/core/format'
import { toDateInput } from '@/core/datetime'

export function MaintenanceDialog({
  open,
  onOpenChange,
  vehicle,
  canWrite,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  vehicle: Vehicle | null
  canWrite: boolean
}) {
  const { t } = useTranslation()
  const [adding, setAdding] = useState(false)
  const [type, setType] = useState<MaintenanceType>('REVISION')
  const [description, setDescription] = useState('')
  const [cost, setCost] = useState('')
  const [date, setDate] = useState(toDateInput(new Date()))

  const { data, loading, reload } = useAsync(
    () => (vehicle ? vehiclesApi.listMaintenance(vehicle.id) : Promise.resolve([])),
    [vehicle?.id, open],
  )

  const submit = async () => {
    if (!vehicle || !description.trim()) return
    const payload: MaintenanceInput = {
      type,
      description: description.trim(),
      cost: Number(cost) || 0,
      date: new Date(date).toISOString(),
      mileage: vehicle.mileage,
    }
    await vehiclesApi.addMaintenance(vehicle.id, payload)
    toast.success(t('vehicles.maintenanceToast'))
    setDescription('')
    setCost('')
    setAdding(false)
    reload()
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90svh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>
            {t('vehicles.maintenanceHistory')}
            {vehicle && (
              <span className="ms-2 text-sm font-normal text-muted-foreground">
                {vehicle.brand} {vehicle.model}
              </span>
            )}
          </DialogTitle>
        </DialogHeader>

        <div className="space-y-3">
          {loading ? (
            <Skeleton className="h-20 w-full" />
          ) : (data ?? []).length === 0 ? (
            <p className="py-6 text-center text-sm text-muted-foreground">
              {t('vehicles.noMaintenance')}
            </p>
          ) : (
            <ul className="space-y-2">
              {(data ?? []).map((m) => (
                <li
                  key={m.id}
                  className="flex items-start gap-3 rounded-lg border p-3 text-sm"
                >
                  <Wrench className="mt-0.5 size-4 text-muted-foreground" />
                  <div className="flex-1">
                    <div className="flex items-center justify-between">
                      <span className="font-medium">
                        {t(`enums.maintenanceType.${m.type}`)}
                      </span>
                      <span className="text-muted-foreground">{formatDate(m.date)}</span>
                    </div>
                    <p className="text-muted-foreground">{m.description}</p>
                  </div>
                  <span className="font-medium">{formatCurrency(m.cost)}</span>
                </li>
              ))}
            </ul>
          )}

          {canWrite &&
            (adding ? (
              <div className="space-y-3 rounded-lg border p-3">
                <div className="grid grid-cols-2 gap-3">
                  <div className="space-y-1.5">
                    <Label>{t('vehicles.maintenanceType')}</Label>
                    <Select
                      value={type}
                      onValueChange={(v) => setType(v as MaintenanceType)}
                    >
                      <SelectTrigger>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {MAINTENANCE_TYPES.map((mt) => (
                          <SelectItem key={mt} value={mt}>
                            {t(`enums.maintenanceType.${mt}`)}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="space-y-1.5">
                    <Label>{t('vehicles.maintenanceDate')}</Label>
                    <Input
                      type="date"
                      value={date}
                      onChange={(e) => setDate(e.target.value)}
                    />
                  </div>
                </div>
                <div className="space-y-1.5">
                  <Label>{t('vehicles.maintenanceDesc')}</Label>
                  <Textarea
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    rows={2}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label>{t('vehicles.maintenanceCost')}</Label>
                  <Input
                    type="number"
                    value={cost}
                    onChange={(e) => setCost(e.target.value)}
                  />
                </div>
                <div className="flex justify-end gap-2">
                  <Button variant="outline" size="sm" onClick={() => setAdding(false)}>
                    {t('common.cancel')}
                  </Button>
                  <Button size="sm" onClick={submit} disabled={!description.trim()}>
                    {t('common.save')}
                  </Button>
                </div>
              </div>
            ) : (
              <Button variant="outline" className="w-full" onClick={() => setAdding(true)}>
                <Plus className="size-4" />
                {t('vehicles.addMaintenance')}
              </Button>
            ))}
        </div>
      </DialogContent>
    </Dialog>
  )
}
