import { useEffect } from 'react'

/**
 * Web adapter: run `refresh` whenever the tab/window regains focus or becomes
 * visible again, so a page that stayed mounted picks up changes made elsewhere
 * (e.g. another staff member editing an account) without a manual reload.
 *
 * DOM-only, so it lives in `src/hooks` (not `src/core`, which stays
 * framework-agnostic for the future React Native app — RN would revalidate via
 * `AppState` instead). Pair with `useAsync().refresh` (silent, no loading flash).
 */
export function useRevalidateOnFocus(refresh: () => void): void {
  useEffect(() => {
    const onFocus = () => {
      if (document.visibilityState === 'visible') refresh()
    }
    window.addEventListener('focus', onFocus)
    document.addEventListener('visibilitychange', onFocus)
    return () => {
      window.removeEventListener('focus', onFocus)
      document.removeEventListener('visibilitychange', onFocus)
    }
  }, [refresh])
}