<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'

const { t } = useI18n()
const route = useRoute()

const currentTitle = computed(() => {
  const key = route.meta?.title as string
  return key ? t(key) : t('app.title')
})
</script>

<template>
  <el-container>
    <el-header class="app-header">
      <div class="app-header__logo">
        <span>retask4j</span>
        <span style="font-size: 11px; color: var(--app-text-secondary); font-weight: 400">
          {{ t('app.subtitle') }}
        </span>
      </div>
      <div style="font-size: var(--brand-font-size-large); font-weight: 600; margin-left: 32px">
        {{ currentTitle }}
      </div>
      <div class="app-header__spacer" />
      <el-menu mode="horizontal" :default-active="route.name as string" router :ellipsis="false">
        <el-menu-item :index="'overview'" :route="{ name: 'overview' }">{{ t('nav.overview') }}</el-menu-item>
        <el-menu-item :index="'topics'" :route="{ name: 'topics' }">{{ t('nav.topics') }}</el-menu-item>
        <el-menu-item :index="'tasks'" :route="{ name: 'tasks' }">{{ t('nav.tasks') }}</el-menu-item>
        <el-menu-item :index="'metrics'" :route="{ name: 'metrics' }">{{ t('nav.metrics') }}</el-menu-item>
        <el-menu-item :index="'alerts'" :route="{ name: 'alerts' }">{{ t('nav.alerts') }}</el-menu-item>
        <el-menu-item :index="'tester'" :route="{ name: 'tester' }">{{ t('nav.tester') }}</el-menu-item>
        <el-menu-item :index="'settings'" :route="{ name: 'settings' }">{{ t('nav.settings') }}</el-menu-item>
      </el-menu>
    </el-header>

    <el-main class="app-main">
      <router-view />
    </el-main>
  </el-container>
</template>
