import type {
  FuelType,
  MaintenanceRecord,
  MaintenanceType,
  Vehicle,
  VehicleStatus,
} from '../types'
import { db } from '../mock/db'
import { ApiError, clone, delay, uid } from './client'

export interface VehicleInput {
  brand: string
  model: string
  registrationNumber: string
  gearboxType: Vehicle['gearboxType']
  fuelType: FuelType
  manufactureYear: number
  mileage: number
  insuranceExpiry: string
  technicalInspectionExpiry: string
}

export interface MaintenanceInput {
  type: MaintenanceType
  description: string
  cost: number
  date: string
  mileage: number
}

export const vehiclesApi = {
  list(filter?: { status?: VehicleStatus; fuelType?: FuelType }): Promise<Vehicle[]> {
    const rows = db.vehicles.filter(
      (v) =>
        (!filter?.status || v.status === filter.status) &&
        (!filter?.fuelType || v.fuelType === filter.fuelType),
    )
    return delay(clone(rows))
  },

  get(id: string): Promise<Vehicle> {
    const row = db.vehicles.find((v) => v.id === id)
    if (!row) throw new ApiError(404, 'Véhicule introuvable')
    return delay(clone(row))
  },

  create(input: VehicleInput): Promise<Vehicle> {
    const vehicle: Vehicle = { id: uid('v'), status: 'AVAILABLE', ...input }
    db.vehicles.push(vehicle)
    return delay(clone(vehicle))
  },

  update(id: string, input: Partial<VehicleInput>): Promise<Vehicle> {
    const row = db.vehicles.find((v) => v.id === id)
    if (!row) throw new ApiError(404, 'Véhicule introuvable')
    Object.assign(row, input)
    return delay(clone(row))
  },

  setStatus(id: string, status: VehicleStatus): Promise<Vehicle> {
    const row = db.vehicles.find((v) => v.id === id)
    if (!row) throw new ApiError(404, 'Véhicule introuvable')
    row.status = status
    return delay(clone(row))
  },

  remove(id: string): Promise<void> {
    const idx = db.vehicles.findIndex((v) => v.id === id)
    if (idx === -1) throw new ApiError(404, 'Véhicule introuvable')
    db.vehicles.splice(idx, 1)
    return delay(undefined)
  },

  listMaintenance(vehicleId: string): Promise<MaintenanceRecord[]> {
    const rows = db.maintenance
      .filter((m) => m.vehicleId === vehicleId)
      .sort((a, b) => b.date.localeCompare(a.date))
    return delay(clone(rows))
  },

  addMaintenance(vehicleId: string, input: MaintenanceInput): Promise<MaintenanceRecord> {
    const record: MaintenanceRecord = { id: uid('m'), vehicleId, ...input }
    db.maintenance.push(record)
    return delay(clone(record))
  },
}