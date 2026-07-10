<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { usePolling } from '@/composables/usePolling'
import { dashboardApi, type TopicInfo } from '@/api/dashboard'
import TopicsTable from '@/components/TopicsTable.vue'

const { t } = useI18n()
const router = useRouter()

const { data, error, loading } = usePolling<TopicInfo[]>(
  async () => (await dashboardApi.overview()).topics,
  5000,
)

const viewTasks = (topic: TopicInfo) => {
  router.push({ name: 'tasks', params: { topic: topic.topic } })
}
</script>

<template>
  <div v-loading="loading">
    <div class="app-section">
      <div class="app-section__title">{{ t('topics.title') }}</div>
      <TopicsTable :topics="data ?? []" :loading="loading" :clickable="true" @row-click="viewTasks" />
      <el-empty v-if="!loading && (data?.length ?? 0) === 0" :description="t('common.noData')" />
    </div>

    <el-alert v-if="error" :title="t('common.error')" :description="error" type="error" show-icon style="margin-top: 12px" />
  </div>
</template>
