import type {
  FuelType,
  MaintenanceRecord,
  MaintenanceType,
  Vehicle,
  VehicleStatus,
} from '../types'
import { qs, request } from './client'

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

/** Backend MaintenanceResponse uses `performedAt` and omits `mileage`. */
interface MaintenanceResponse {
  id: string
  vehicleId: string
  type: MaintenanceType
  performedAt: string
  cost: number
  description: string
}

function toMaintenance(res: MaintenanceResponse): MaintenanceRecord {
  return {
    id: res.id,
    vehicleId: res.vehicleId,
    type: res.type,
    description: res.description,
    cost: res.cost,
    date: res.performedAt,
    mileage: 0, // not tracked server-side
  }
}

export const vehiclesApi = {
  list(filter?: { status?: VehicleStatus; fuelType?: FuelType }): Promise<Vehicle[]> {
    return request<Vehicle[]>(`/vehicles${qs(filter)}`)
  },

  get(id: string): Promise<Vehicle> {
    return request<Vehicle>(`/vehicles/${id}`)
  },

  create(input: VehicleInput): Promise<Vehicle> {
    return request<Vehicle>('/vehicles', { method: 'POST', body: input })
  },

  update(id: string, input: Partial<VehicleInput>): Promise<Vehicle> {
    return request<Vehicle>(`/vehicles/${id}`, { method: 'PUT', body: input })
  },

  setStatus(id: string, status: VehicleStatus): Promise<Vehicle> {
    return request<Vehicle>(`/vehicles/${id}/status`, {
      method: 'PATCH',
      body: { status },
    })
  },

  remove(id: string): Promise<void> {
    return request<void>(`/vehicles/${id}`, { method: 'DELETE' })
  },

  async listMaintenance(vehicleId: string): Promise<MaintenanceRecord[]> {
    const rows = await request<MaintenanceResponse[]>(
      `/vehicles/${vehicleId}/maintenance`,
    )
    return rows.map(toMaintenance)
  },

  async addMaintenance(
    vehicleId: string,
    input: MaintenanceInput,
  ): Promise<MaintenanceRecord> {
    const res = await request<MaintenanceResponse>(
      `/vehicles/${vehicleId}/maintenance`,
      {
        method: 'POST',
        body: {
          type: input.type,
          performedAt: input.date,
          cost: input.cost,
          description: input.description,
        },
      },
    )
    return toMaintenance(res)
  },
}