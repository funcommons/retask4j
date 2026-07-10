import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import { i18n } from './locales'
import './styles/index.scss'

// Restore theme/brand from localStorage before app mount
const savedTheme = localStorage.getItem('retask4j.theme') || 'light'
const savedBrand = localStorage.getItem('retask4j.brand') || 'mchuan'
document.documentElement.setAttribute('data-theme', savedTheme)
document.documentElement.setAttribute('data-brand', savedBrand)

const app = createApp(App)
app.use(router)
app.use(i18n)
app.use(ElementPlus)
app.mount('#app')
