/**
 * Web i18next initialization. Uses the browser language detector and
 * persists the choice in localStorage. The React Native app will have its
 * own init file; both share `resources` from @/core/i18n/languages.
 */
import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import LanguageDetector from 'i18next-browser-languagedetector'
import { DEFAULT_LANGUAGE, resources } from '@/core/i18n/languages'

void i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources,
    fallbackLng: DEFAULT_LANGUAGE,
    supportedLngs: ['fr', 'en', 'ar'],
    nonExplicitSupportedLngs: true,
    detection: {
      order: ['localStorage', 'navigator'],
      caches: ['localStorage'],
      lookupLocalStorage: 'ae.lang',
    },
    interpolation: { escapeValue: false },
  })

export default i18n
