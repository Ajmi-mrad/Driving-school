/**
 * Minimal async-data hook. React-only (no DOM) — reusable by React Native.
 * For a real backend you might swap this for TanStack Query; the call sites
 * (`const { data, loading, error, reload } = useAsync(...)`) stay similar.
 */
import { useCallback, useEffect, useRef, useState } from 'react'

export interface AsyncState<T> {
  data: T | null
  loading: boolean
  error: string | null
  /** Re-fetch with a loading state (skeletons). Use after a mutation. */
  reload: () => void
  /** Silent background re-fetch: keeps the current data until the new data
   *  arrives (no loading flash). Use for revalidation, e.g. on window focus. */
  refresh: () => void
}

export function useAsync<T>(
  fn: () => Promise<T>,
  deps: unknown[] = [],
): AsyncState<T> {
  const [data, setData] = useState<T | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [nonce, setNonce] = useState(0)

  // Latest fn kept in a ref so `refresh` stays stable across renders (call
  // sites pass a new inline closure each render).
  const fnRef = useRef(fn)
  fnRef.current = fn

  const reload = useCallback(() => setNonce((n) => n + 1), [])
  const refresh = useCallback(() => {
    fnRef.current()
      .then((result) => setData(result))
      .catch(() => {
        /* keep the last good data on a background-refresh failure */
      })
  }, [])

  useEffect(() => {
    let active = true
    setLoading(true)
    setError(null)
    fn()
      .then((result) => {
        if (active) setData(result)
      })
      .catch((err: unknown) => {
        if (active) setError(err instanceof Error ? err.message : 'Erreur inconnue')
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, nonce])

  return { data, loading, error, reload, refresh }
}