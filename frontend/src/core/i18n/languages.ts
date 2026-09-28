/**
 * Language registry + translation resources. Framework-agnostic.
 *
 * The web app and the future React Native app each provide their own
 * i18next init (they need different language detectors), but both import
 * these `resources` and helpers — the translations themselves are shared.
 */
import { fr } from './locales/fr'
import { en } from './locales/en'
import { ar } from './locales/ar'

export const resources = {
  fr: { translation: fr },
  en: { translation: en },
  ar: { translation: ar },
} as const

export const SUPPORTED_LANGUAGES = [
  { code: 'fr', label: 'Français' },
  { code: 'en', label: 'English' },
  { code: 'ar', label: 'العربية' },
] as const

export type LanguageCode = (typeof SUPPORTED_LANGUAGES)[number]['code']

export const DEFAULT_LANGUAGE: LanguageCode = 'fr'

const RTL_LANGUAGES = new Set<string>(['ar'])

export function isRtl(lng: string): boolean {
  return RTL_LANGUAGES.has(lng.split('-')[0])
}

export function dirFor(lng: string): 'rtl' | 'ltr' {
  return isRtl(lng) ? 'rtl' : 'ltr'
}

/** Map an i18n language code to a BCP-47 locale for Intl formatting. */
export function intlLocaleFor(lng: string): string {
  switch (lng.split('-')[0]) {
    case 'fr':
      return 'fr-FR'
    case 'en':
      return 'en-GB'
    case 'ar':
      return 'ar-TN'
    default:
      return 'fr-FR'
  }
}