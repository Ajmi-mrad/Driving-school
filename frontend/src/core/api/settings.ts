import type { BookingSettings } from '../types'
import { request } from './client'

export const settingsApi = {
  get(): Promise<BookingSettings> {
    return request<BookingSettings>('/booking-settings')
  },
  update(input: BookingSettings): Promise<BookingSettings> {
    return request<BookingSettings>('/booking-settings', {
      method: 'PUT',
      body: input,
    })
  },
}