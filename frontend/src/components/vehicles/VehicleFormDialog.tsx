import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { vehiclesApi, type VehicleInput } from '@/core/api'
import {
  FUEL_TYPES,
  GEARBOX_TYPES,
  type FuelType,
  type GearboxType,
  type Vehicle,
} from '@/core/types'

interface FormState {
  brand: string
  model: string
  registrationNumber: string
  gearboxType: GearboxType
  fuelType: FuelType
  manufactureYear: string
  mileage: string
  insuranceExpiry: string
  technicalInspectionExpiry: string
}

function toDateInput(iso: string): string {
  return iso ? iso.slice(0, 10) : ''
}

const EMPTY: FormState = {
  brand: '',
  model: '',
  registrationNumber: '',
  gearboxType: 'MANUAL',
  fuelType: 'DIESEL',
  manufactureYear: String(new Date().getFullYear()),
  mileage: '0',
  insuranceExpiry: '',
  technicalInspectionExpiry: '',
}

function fromVehicle(v: Vehicle): FormState {
  return {
    brand: v.brand,
    model: v.model,
    registrationNumber: v.registrationNumber,
    gearboxType: v.gearboxType,
    fuelType: v.fuelType,
    manufactureYear: String(v.manufactureYear),
    mileage: String(v.mileage),
    insuranceExpiry: toDateInput(v.insuranceExpiry),
    technicalInspectionExpiry: toDateInput(v.technicalInspectionExpiry),
  }
}

export function VehicleFormDialog({
  open,
  onOpenChange,
  vehicle,
  onSaved,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  vehicle: Vehicle | null
  onSaved: () => void
}) {
  const { t } = useTranslation()
  const [form, setForm] = useState<FormState>(EMPTY)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (open) setForm(vehicle ? fromVehicle(vehicle) : EMPTY)
  }, [open, vehicle])

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setForm((f) => ({ ...f, [key]: value }))

  const valid =
    form.brand.trim() && form.model.trim() && form.registrationNumber.trim()

  const submit = async () => {
    if (!valid) return
    setSaving(true)
    const payload: VehicleInput = {
      brand: form.brand.trim(),
      model: form.model.trim(),
      registrationNumber: form.registrationNumber.trim(),
      gearboxType: form.gearboxType,
      fuelType: form.fuelType,
      manufactureYear: Number(form.manufactureYear) || new Date().getFullYear(),
      mileage: Number(form.mileage) || 0,
      insuranceExpiry: form.insuranceExpiry
        ? new Date(form.insuranceExpiry).toISOString()
        : new Date().toISOString(),
      technicalInspectionExpiry: form.technicalInspectionExpiry
        ? new Date(form.technicalInspectionExpiry).toISOString()
        : new Date().toISOString(),
    }
    try {
      if (vehicle) {
        await vehiclesApi.update(vehicle.id, payload)
        toast.success(t('vehicles.updatedToast'))
      } else {
        await vehiclesApi.create(payload)
        toast.success(t('vehicles.createdToast'))
      }
      onSaved()
      onOpenChange(false)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90svh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>
            {vehicle ? t('vehicles.editTitle') : t('vehicles.newTitle')}
          </DialogTitle>
        </DialogHeader>

        <div className="grid gap-4 py-2">
          <div className="grid grid-cols-2 gap-3">
            <Field label={t('vehicles.brand')}>
              <Input value={form.brand} onChange={(e) => set('brand', e.target.value)} />
            </Field>
            <Field label={t('vehicles.model')}>
              <Input value={form.model} onChange={(e) => set('model', e.target.value)} />
            </Field>
          </div>

          <Field label={t('vehicles.registration')}>
            <Input
              value={form.registrationNumber}
              onChange={(e) => set('registrationNumber', e.target.value)}
            />
          </Field>

          <div className="grid grid-cols-2 gap-3">
            <Field label={t('vehicles.gearbox')}>
              <Select
                value={form.gearboxType}
                onValueChange={(v) => set('gearboxType', v as GearboxType)}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {GEARBOX_TYPES.map((g) => (
                    <SelectItem key={g} value={g}>
                      {t(`enums.gearbox.${g}`)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>
            <Field label={t('vehicles.fuel')}>
              <Select
                value={form.fuelType}
                onValueChange={(v) => set('fuelType', v as FuelType)}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {FUEL_TYPES.map((fuel) => (
                    <SelectItem key={fuel} value={fuel}>
                      {t(`enums.fuel.${fuel}`)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <Field label={t('vehicles.year')}>
              <Input
                type="number"
                value={form.manufactureYear}
                onChange={(e) => set('manufactureYear', e.target.value)}
              />
            </Field>
            <Field label={t('vehicles.mileage')}>
              <Input
                type="number"
                value={form.mileage}
                onChange={(e) => set('mileage', e.target.value)}
              />
            </Field>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <Field label={t('vehicles.insuranceExpiry')}>
              <Input
                type="date"
                value={form.insuranceExpiry}
                onChange={(e) => set('insuranceExpiry', e.target.value)}
              />
            </Field>
            <Field label={t('vehicles.inspectionExpiry')}>
              <Input
                type="date"
                value={form.technicalInspectionExpiry}
                onChange={(e) => set('technicalInspectionExpiry', e.target.value)}
              />
            </Field>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('common.cancel')}
          </Button>
          <Button onClick={submit} disabled={!valid || saving}>
            {t('common.save')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1.5">
      <Label>{label}</Label>
      {children}
    </div>
  )
}
