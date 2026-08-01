import type { AuthStorage } from '@/core/auth/storage'

/**
 * Web (browser) implementation of the AuthStorage port, backed by
 * localStorage. The React Native app will provide an AsyncStorage-backed
 * adapter instead — core/auth never imports a platform API directly.
 */
export const webStorage: AuthStorage = {
  get: (key) => {
    try {
      return window.localStorage.getItem(key)
    } catch {
      return null
    }
  },
  set: (key, value) => {
    try {
      window.localStorage.setItem(key, value)
    } catch {
      /* ignore quota / privacy-mode errors */
    }
  },
  remove: (key) => {
    try {
      window.localStorage.removeItem(key)
    } catch {
      /* ignore */
    }
  },
}