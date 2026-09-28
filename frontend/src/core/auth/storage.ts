/**
 * Storage abstraction so the auth layer stays platform-agnostic.
 *
 * The web app injects a `localStorage`-backed adapter; the future React
 * Native app injects an `AsyncStorage`-backed one. The auth logic itself
 * never touches a platform API directly.
 */
export interface AuthStorage {
  get(key: string): string | null
  set(key: string, value: string): void
  remove(key: string): void
}

/** No-op storage (SSR / tests / when persistence is undesirable). */
export const memoryStorage = (): AuthStorage => {
  const map = new Map<string, string>()
  return {
    get: (k) => map.get(k) ?? null,
    set: (k, v) => void map.set(k, v),
    remove: (k) => void map.delete(k),
  }
}