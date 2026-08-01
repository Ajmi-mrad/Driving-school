import type { BookingSettings } from '../types'
import { db } from '../mock/db'
import { clone, delay } from './client'

export const settingsApi = {
  get(): Promise<BookingSettings> {
    return delay(clone(db.bookingSettings))
  },
  update(input: BookingSettings): Promise<BookingSettings> {
    db.bookingSettings = { ...input }
    return delay(clone(db.bookingSettings))
  },
}