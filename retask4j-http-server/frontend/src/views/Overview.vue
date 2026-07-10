<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePolling } from '@/composables/usePolling'
import { useSSE } from '@/composables/useSSE'
import { dashboardApi, type TopicInfo } from '@/api/dashboard'
import TopicsTable from '@/components/TopicsTable.vue'

const { t } = useI18n()

const { data, error, loading, lastUpdate, refresh } = usePolling(
  () => dashboardApi.overview(),
  10000,
)

const topics = computed<TopicInfo[]>(() => data.value?.topics ?? [])
const totalTopics = computed(() => data.value?.totalTopics ?? 0)

const totals = (key: keyof TopicInfo) =>
  topics.value.reduce((sum, item) => sum + (Number(item[key]) || 0), 0)

const lastUpdateText = computed(() => {
  if (!lastUpdate.value) return '-'
  return new Date(lastUpdate.value).toLocaleString()
})

// Trigger immediate refresh on any task event from SSE
useSSE(() => { refresh() })
</script>

<template>
  <div v-loading="loading">
    <div class="app-section">
      <div class="app-section__title">{{ t('overview.title') }}</div>

      <div class="metric-grid">
        <div class="metric-card">
          <div class="metric-card__label">{{ t('overview.totalTopics') }}</div>
          <div class="metric-card__value">{{ totalTopics }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-card__label">{{ t('overview.working') }}</div>
          <div class="metric-card__value">{{ totals('working') }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-card__label">{{ t('overview.pending') }}</div>
          <div class="metric-card__value">{{ totals('pending') }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-card__label">{{ t('overview.timing') }}</div>
          <div class="metric-card__value">{{ totals('timing') }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-card__label">{{ t('overview.retry') }}</div>
          <div class="metric-card__value">{{ totals('retry') }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-card__label">{{ t('overview.callback') }}</div>
          <div class="metric-card__value">{{ totals('callbackWorking') }}</div>
        </div>
      </div>
    </div>

    <div class="app-section">
      <div class="app-section__title">{{ t('topics.title') }}</div>
      <TopicsTable :topics="topics" :loading="loading" />
      <el-empty v-if="!loading && topics.length === 0" :description="t('common.noData')" />
    </div>

    <div style="display: flex; gap: 12px; align-items: center; margin-top: 8px">
      <span style="color: var(--app-text-secondary); font-size: 12px">
        {{ t('common.lastUpdate') }}: {{ lastUpdateText }}
      </span>
      <el-button size="small" @click="refresh">{{ t('common.retry') }}</el-button>
    </div>

    <el-alert v-if="error" :title="t('common.error')" :description="error" type="error" show-icon style="margin-top: 12px" />
  </div>
</template>
