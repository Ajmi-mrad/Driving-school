import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { dirFor } from '@/core/i18n/languages'

/**
 * Keeps <html dir> and <html lang> in sync with the active language so
 * Arabic renders right-to-left. Renders nothing.
 */
export function DirectionManager() {
  const { i18n } = useTranslation()

  useEffect(() => {
    const lng = i18n.language || 'fr'
    const root = document.documentElement
    root.setAttribute('lang', lng)
    root.setAttribute('dir', dirFor(lng))
  }, [i18n.language])

  return null
}
