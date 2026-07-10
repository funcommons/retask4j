import { createI18n } from 'vue-i18n'
import enUS from './en-US'
import zhCN from './zh-CN'

const savedLocale = localStorage.getItem('retask4j.locale') || 'en-US'

export const i18n = createI18n({
  legacy: false,
  locale: savedLocale,
  fallbackLocale: 'en-US',
  messages: {
    'en-US': enUS,
    'zh-CN': zhCN,
  },
})

export function setLocale(locale: 'en-US' | 'zh-CN' | string) {
  (i18n.global.locale.value as string) = locale
  localStorage.setItem('retask4j.locale', locale)
  document.documentElement.lang = locale
}
