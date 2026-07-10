<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { usePolling } from '@/composables/usePolling'
import { dashboardApi, type TopicInfo, type TaskMessage } from '@/api/dashboard'
import TopicsTable from '@/components/TopicsTable.vue'
import TaskDetailDrawer from '@/components/TaskDetailDrawer.vue'

const { t, locale } = useI18n()
const route = useRoute()
const currentTopic = ref<string>('')

const { data: topicsData } = usePolling<TopicInfo[]>(
  async () => (await dashboardApi.overview()).topics,
  10000,
  false,
)
const topics = computed<TopicInfo[]>(() => topicsData.value ?? [])

const tasksFetcher = async () => {
  if (!currentTopic.value) return { messages: [] as TaskMessage[], offset: 0, limit: 0 }
  return dashboardApi.tasks(currentTopic.value, 0, 20)
}
const { data: tasksData, error, loading, refresh: refreshTasks } = usePolling(tasksFetcher, 5000, false)
const messages = computed<TaskMessage[]>(() => tasksData.value?.messages ?? [])

const drawerVisible = ref(false)
const drawerTaskId = ref('')
const openTaskDetail = (id: string) => {
  drawerTaskId.value = id
  drawerVisible.value = true
}

const initTopic = () => {
  const fromRoute = route.params.topic as string | undefined
  if (fromRoute) {
    currentTopic.value = fromRoute
  } else if (!currentTopic.value && topics.value.length > 0) {
    currentTopic.value = topics.value[0].topic
  }
}

watch(topics, () => initTopic(), { immediate: true })
watch(() => route.params.topic, (t) => {
  if (t) currentTopic.value = t as string
})

const formatTime = (ts?: number) => {
  if (!ts) return '-'
  return new Date(ts).toLocaleString(locale.value)
}

const statusTag = (status?: string): 'success' | 'warning' | 'info' | 'danger' | undefined => {
  const map: Record<string, 'success' | 'warning' | 'info' | 'danger'> = {
    WAITING: 'info',
    PENDING: 'warning',
    SUCCESS: 'success',
    FAIL: 'danger',
  }
  return map[status || '']
}
</script>

<template>
  <div v-loading="loading">
    <div class="app-section">
      <div class="app-section__title">{{ t('tasks.title') }}</div>

      <el-select v-model="currentTopic" :placeholder="t('topics.name')" style="width: 240px; margin-bottom: 12px">
        <el-option v-for="topic in topics" :key="topic.topic" :label="topic.topic" :value="topic.topic" />
      </el-select>

      <TopicsTable :topics="topics" :loading="false" />

      <el-divider />

      <el-table :data="messages" stripe empty-text=" " @row-click="(row: TaskMessage) => openTaskDetail(row.id)" :row-style="{ cursor: 'pointer' }">
        <el-table-column prop="id" :label="t('tasks.taskId')" width="280" />
        <el-table-column :label="t('tasks.status')" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status" :type="statusTag(row.status)">{{ row.status }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="mode" :label="t('tasks.mode')" width="100" />
        <el-table-column :label="t('tasks.createTime')" width="180">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column :label="t('tasks.executeTime')" width="180">
          <template #default="{ row }">{{ formatTime(row.executeTime) }}</template>
        </el-table-column>
        <el-table-column :label="t('tasks.completeTime')" width="180">
          <template #default="{ row }">{{ formatTime(row.completeTime) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && messages.length === 0" :description="t('common.noData')" />
    </div>

    <TaskDetailDrawer
      v-model:visible="drawerVisible"
      :topic="currentTopic"
      :task-id="drawerTaskId"
      @refresh="refreshTasks"
    />

    <el-alert v-if="error" :title="t('common.error')" :description="error" type="error" show-icon style="margin-top: 12px" />
  </div>
</template>
