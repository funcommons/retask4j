import { ref, watch, onMounted } from 'vue'

const THEME_KEY = 'retask4j.theme'
const BRAND_KEY = 'retask4j.brand'
const LOCALE_KEY = 'retask4j.locale'

export type Theme = 'light' | 'dark' | 'orange-black'
export type Brand = 'mchuan'

const themeRef = ref<Theme>('light')
const brandRef = ref<Brand>('mchuan')

function applyTheme(t: Theme) {
  document.documentElement.setAttribute('data-theme', t)
  localStorage.setItem(THEME_KEY, t)
  themeRef.value = t
}

function applyBrand(b: Brand) {
  document.documentElement.setAttribute('data-brand', b)
  localStorage.setItem(BRAND_KEY, b)
  brandRef.value = b
}

export function useTheme() {
  // Restore on first use (called from main.ts)
  onMounted(() => {
    const savedTheme = (localStorage.getItem(THEME_KEY) as Theme) || 'light'
    const savedBrand = (localStorage.getItem(BRAND_KEY) as Brand) || 'mchuan'
    applyTheme(savedTheme)
    applyBrand(savedBrand)
  })

  // Cross-tab sync
  const onStorage = (e: StorageEvent) => {
    if (e.key === THEME_KEY && e.newValue) applyTheme(e.newValue as Theme)
    if (e.key === BRAND_KEY && e.newValue) applyBrand(e.newValue as Brand)
  }
  if (typeof window !== 'undefined') {
    window.addEventListener('storage', onStorage)
  }

  return {
    theme: themeRef,
    brand: brandRef,
    setTheme: applyTheme,
    setBrand: applyBrand,
  }
}

export { LOCALE_KEY, THEME_KEY, BRAND_KEY }
