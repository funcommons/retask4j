<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { TopicInfo } from '@/api/dashboard'

defineProps<{
  topics: TopicInfo[]
  clickable?: boolean
  loading?: boolean
}>()

const emit = defineEmits<{
  (e: 'rowClick', topic: TopicInfo): void
}>()

const { t } = useI18n()

const onRowClick = (topic: TopicInfo) => emit('rowClick', topic)
</script>

<template>
  <el-table
    :data="topics"
    :loading="loading"
    stripe
    :row-style="clickable ? { cursor: 'pointer' } : undefined"
    @row-click="clickable ? onRowClick : undefined"
  >
    <el-table-column prop="topic" :label="t('topics.name')" />
    <el-table-column prop="working" :label="t('topics.workingCount')" sortable />
    <el-table-column prop="pending" :label="t('topics.pendingCount')" sortable />
    <el-table-column prop="timing" :label="t('topics.timingCount')" sortable />
    <el-table-column prop="retry" :label="t('topics.retryCount')" sortable />
    <el-table-column prop="callbackWorking" :label="t('topics.callbackCount')" sortable />
  </el-table>
</template>
