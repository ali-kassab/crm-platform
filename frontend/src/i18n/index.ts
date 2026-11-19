import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import ar from './ar.json'
import en from './en.json'

export type Language = 'ar' | 'en'

const STORAGE_KEY = 'ui-language'

function initialLanguage(): Language {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved === 'ar' || saved === 'en') return saved
  } catch {
    // storage unavailable (private mode): fall back to Arabic
  }
  return 'ar' // Arabic-first product (directive)
}

export function directionOf(language: string): 'rtl' | 'ltr' {
  return language === 'ar' ? 'rtl' : 'ltr'
}

/** Keeps <html lang/dir> in sync so native controls, scrollbars and fonts follow the language. */
function applyToDocument(language: string) {
  document.documentElement.lang = language
  document.documentElement.dir = directionOf(language)
}

void i18n.use(initReactI18next).init({
  resources: { ar: { translation: ar }, en: { translation: en } },
  lng: initialLanguage(),
  fallbackLng: 'ar',
  interpolation: { escapeValue: false },
})
applyToDocument(i18n.language)

i18n.on('languageChanged', (language) => {
  applyToDocument(language)
  try {
    localStorage.setItem(STORAGE_KEY, language)
  } catch {
    // ignore
  }
})

export default i18n
