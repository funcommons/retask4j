<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePolling } from '@/composables/usePolling'
import { dashboardApi, type MetricsResponse } from '@/api/dashboard'

const { t } = useI18n()

const { data, error, loading, lastUpdate, refresh } = usePolling<MetricsResponse>(
  () => dashboardApi.metrics(),
  5000,
)

const successRate = computed(() => data.value?.successRate ?? 0)
const sendSuccessRate = computed(() => data.value?.sendSuccessRate ?? 0)
const successColor = computed(() => successRate.value >= 95 ? 'success' : successRate.value >= 80 ? 'warning' : 'exception')
const sendColor = computed(() => sendSuccessRate.value >= 95 ? 'success' : sendSuccessRate.value >= 80 ? 'warning' : 'exception')

const lastUpdateText = computed(() => {
  if (!lastUpdate.value) return '-'
  return new Date(lastUpdate.value).toLocaleString()
})
</script>

<template>
  <div v-loading="loading">
    <div class="app-section">
      <div class="app-section__title">{{ t('metrics.title') }}</div>
      <div class="app-section__desc">{{ t('metrics.subtitle') }}</div>

      <div class="metric-grid">
        <div class="metric-card">
          <div class="metric-card__label">{{ t('metrics.successRate') }}</div>
          <div class="metric-card__value">{{ successRate.toFixed(2) }}%</div>
          <el-progress :percentage="successRate" :status="successColor" :show-text="false" />
          <div class="metric-card__sub">
            {{ t('metrics.success') }}: {{ data?.success ?? 0 }} · {{ t('metrics.fail') }}: {{ data?.fail ?? 0 }}
          </div>
        </div>
        <div class="metric-card">
          <div class="metric-card__label">{{ t('metrics.sendSuccessRate') }}</div>
          <div class="metric-card__value">{{ sendSuccessRate.toFixed(2) }}%</div>
          <el-progress :percentage="sendSuccessRate" :status="sendColor" :show-text="false" />
          <div class="metric-card__sub">
            {{ t('metrics.sendSuccess') }}: {{ data?.sendSuccess ?? 0 }} · {{ t('metrics.sendFail') }}: {{ data?.sendFail ?? 0 }}
          </div>
        </div>
        <div class="metric-card">
          <div class="metric-card__label">{{ t('metrics.funcComplete') }}</div>
          <div class="metric-card__value">{{ data?.funcComplete ?? 0 }}</div>
          <div class="metric-card__sub">
            {{ t('metrics.funcTimeout') }}: {{ data?.funcTimeout ?? 0 }} · {{ t('metrics.funcResultMissing') }}: {{ data?.funcResultMissing ?? 0 }}
          </div>
        </div>
        <div class="metric-card">
          <div class="metric-card__label">{{ t('metrics.callbackComplete') }}</div>
          <div class="metric-card__value">{{ data?.callbackComplete ?? 0 }}</div>
          <div class="metric-card__sub">
            {{ t('metrics.callbackFail') }}: {{ data?.callbackFail ?? 0 }}
          </div>
        </div>
      </div>
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

<style scoped>
.metric-card__sub {
  font-size: 11px;
  color: var(--app-text-secondary);
  margin-top: 8px;
}
</style>
