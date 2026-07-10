<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { setLocale } from '@/locales'
import { useTheme, type Theme, type Brand } from '@/composables/useTheme'
import { dashboardApi } from '@/api/dashboard'

const { t, locale } = useI18n()
const { theme, brand, setTheme, setBrand } = useTheme()

const themes: Theme[] = ['light', 'dark', 'orange-black']
const brands: Brand[] = ['mchuan']

const tokenInput = ref(dashboardApi.getToken())

const changeLocale = (val: string | number | boolean | undefined) => {
  const s = String(val)
  setLocale(s)
  locale.value = s
}

const changeTheme = (val: string | number | boolean | undefined) => {
  setTheme(String(val) as Theme)
}

const changeBrand = (val: string | number | boolean | undefined) => {
  setBrand(String(val) as Brand)
}

const saveToken = () => {
  dashboardApi.setToken(tokenInput.value.trim())
}
</script>

<template>
  <div>
    <div class="app-section">
      <div class="app-section__title">{{ t('settings.title') }}</div>

      <el-form label-width="160px">
        <el-form-item :label="t('settings.dashboardToken')">
          <div style="display: flex; gap: 8px; width: 100%">
            <el-input
              v-model="tokenInput"
              type="password"
              show-password
              :placeholder="t('settings.dashboardTokenHint')"
              style="max-width: 480px"
            />
            <el-button type="primary" @click="saveToken">{{ t('settings.dashboardTokenSave') }}</el-button>
          </div>
        </el-form-item>

        <el-form-item :label="t('settings.language')">
          <el-radio-group :model-value="locale" @change="changeLocale">
            <el-radio-button value="en-US">{{ t('settings.languageEn') }}</el-radio-button>
            <el-radio-button value="zh-CN">{{ t('settings.languageZh') }}</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item :label="t('settings.theme')">
          <el-radio-group :model-value="theme" @change="changeTheme">
            <el-radio-button v-for="th in themes" :key="th" :value="th">
              {{ th === 'light' ? t('settings.themeLight')
                : th === 'dark' ? t('settings.themeDark')
                : t('settings.themeOrangeBlack') }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item :label="t('settings.brand')">
          <el-radio-group :model-value="brand" @change="changeBrand">
            <el-radio-button v-for="br in brands" :key="br" :value="br">
              {{ br === 'mchuan' ? t('settings.brandMchuan') : br }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>
