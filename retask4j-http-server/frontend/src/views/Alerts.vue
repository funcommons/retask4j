<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePolling } from '@/composables/usePolling'
import { dashboardApi, type AlertsResponse } from '@/api/dashboard'

const { t, locale } = useI18n()

const { data, error, loading, refresh } = usePolling<AlertsResponse>(
  () => dashboardApi.alerts(),
  10000,
)

const active = computed(() => data.value?.active ?? [])
const history = computed(() => data.value?.history ?? [])

const formatTime = (ts?: number) => {
  if (!ts) return '-'
  return new Date(ts).toLocaleString(locale.value)
}

const formatSnapshot = (s: Record<string, unknown>) => {
  return Object.entries(s)
    .map(([k, v]) => `${k}=${v}`)
    .join(', ')
}
</script>

<template>
  <div v-loading="loading">
    <div class="app-section">
      <div class="app-section__title">{{ t('alerts.title') }}</div>
      <div class="app-section__desc">{{ t('alerts.subtitle') }}</div>

      <el-alert
        v-if="active.length > 0"
        :title="t('alerts.activeCount', { n: active.length })"
        type="error"
        show-icon
        :closable="false"
        style="margin-bottom: 12px"
      >
        <ul style="margin: 0; padding-left: 20px">
          <li v-for="a in active" :key="a.id">
            <strong>{{ a.ruleName }}</strong> ({{ a.ruleId }}) — {{ formatSnapshot(a.snapshot) }} — {{ formatTime(a.firedAt) }}
          </li>
        </ul>
      </el-alert>
      <el-alert
        v-else
        :title="t('alerts.noActive')"
        type="success"
        show-icon
        :closable="false"
        style="margin-bottom: 12px"
      />
    </div>

    <div class="app-section">
      <div class="app-section__title">{{ t('alerts.historyTitle') }}</div>
      <el-table :data="history" stripe>
        <el-table-column prop="ruleName" :label="t('alerts.ruleName')" width="240" />
        <el-table-column prop="ruleId" label="Rule ID" width="220" />
        <el-table-column :label="t('alerts.firedAt')" width="200">
          <template #default="{ row }">{{ formatTime(row.firedAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('alerts.snapshot')">
          <template #default="{ row }">
            <code style="font-size: 11px">{{ formatSnapshot(row.snapshot) }}</code>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && history.length === 0" :description="t('alerts.noHistory')" />
    </div>

    <el-button size="small" @click="refresh">{{ t('common.retry') }}</el-button>

    <el-alert v-if="error" :title="t('common.error')" :description="error" type="error" show-icon style="margin-top: 12px" />
  </div>
</template>
