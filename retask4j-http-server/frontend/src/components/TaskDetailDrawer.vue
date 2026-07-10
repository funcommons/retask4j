<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { dashboardApi, type TaskMessage } from '@/api/dashboard'

const props = defineProps<{
  visible: boolean
  topic: string
  taskId: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'refresh'): void
}>()

const { t, locale } = useI18n()
const task = ref<TaskMessage | null>(null)
const loading = ref(false)
const actionLoading = ref(false)
const activeTab = ref('input')

const formatTime = (ts?: number) => {
  if (!ts) return '-'
  return new Date(ts).toLocaleString(locale.value)
}

const formatJson = (s?: string | null) => {
  if (!s) return ''
  try {
    return JSON.stringify(JSON.parse(s), null, 2)
  } catch {
    return s
  }
}

const prettyInput = computed(() => formatJson(task.value?.input))
const prettyOutput = computed(() => formatJson(task.value?.output))

const load = async () => {
  if (!props.topic || !props.taskId) return
  loading.value = true
  try {
    task.value = await dashboardApi.taskDetail(props.topic, props.taskId)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
    task.value = null
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.visible, props.taskId, props.topic],
  () => {
    if (props.visible && props.taskId) load()
    if (!props.visible) task.value = null
  },
)

const close = () => emit('update:visible', false)

const refresh = () => {
  load()
  emit('refresh')
}

const doReplay = async () => {
  try {
    await ElMessageBox.confirm(
      t('detail.confirmReplay'),
      t('detail.confirmTitle'),
      { type: 'warning' },
    )
  } catch {
    return
  }
  actionLoading.value = true
  try {
    await dashboardApi.replay(props.topic, props.taskId)
    ElMessage.success(t('detail.replaySuccess'))
    refresh()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  } finally {
    actionLoading.value = false
  }
}

const doForceRetry = async () => {
  actionLoading.value = true
  try {
    await dashboardApi.forceRetry(props.topic, props.taskId)
    ElMessage.success(t('detail.forceRetrySuccess'))
    refresh()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  } finally {
    actionLoading.value = false
  }
}

const doForceComplete = async (status: 'SUCCESS' | 'FAIL') => {
  const isSuccess = status === 'SUCCESS'
  let output = ''
  let errorMsg = ''
  if (isSuccess) {
    try {
      const { value } = await ElMessageBox.prompt(
        t('detail.outputHint'),
        t('detail.forceCompleteSuccessTitle'),
        { inputType: 'textarea', inputPlaceholder: t('detail.outputPlaceholder') },
      )
      output = value || ''
    } catch {
      return
    }
  } else {
    try {
      const { value } = await ElMessageBox.prompt(
        t('detail.errorHint'),
        t('detail.forceCompleteFailTitle'),
        { inputType: 'textarea', inputPlaceholder: t('detail.errorPlaceholder') },
      )
      errorMsg = value || ''
    } catch {
      return
    }
  }
  actionLoading.value = true
  try {
    await dashboardApi.forceComplete(props.topic, props.taskId, status, output, errorMsg)
    ElMessage.success(t('detail.forceCompleteSuccess'))
    refresh()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  } finally {
    actionLoading.value = false
  }
}

const doDelete = async () => {
  try {
    await ElMessageBox.confirm(
      t('detail.confirmDelete', { id: props.taskId }),
      t('detail.confirmTitle'),
      { type: 'error', inputType: 'text', inputValue: '', inputValidator: (v) => v === props.taskId || t('detail.confirmDeleteMismatch') },
    )
  } catch {
    return
  }
  actionLoading.value = true
  try {
    const result = await dashboardApi.deleteTask(props.topic, props.taskId)
    ElMessage.success(t('detail.deleteSuccess', { n: result.removed }))
    close()
    emit('refresh')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  } finally {
    actionLoading.value = false
  }
}
</script>

<template>
  <el-drawer
    :model-value="visible"
    :title="t('detail.title', { id: taskId })"
    size="640px"
    direction="rtl"
    :close-on-click-modal="false"
    @update:model-value="(v: boolean) => emit('update:visible', v)"
  >
    <div v-loading="loading">
      <template v-if="task">
        <el-descriptions :column="2" border size="small" style="margin-bottom: 16px">
          <el-descriptions-item :label="t('detail.id')">{{ task.id }}</el-descriptions-item>
          <el-descriptions-item :label="t('detail.topic')">{{ task.topic }}</el-descriptions-item>
          <el-descriptions-item :label="t('detail.status')">
            <el-tag :type="(task.status === 'SUCCESS' ? 'success' : task.status === 'FAIL' ? 'danger' : task.status === 'PENDING' ? 'warning' : 'info')">
              {{ task.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('detail.mode')">{{ task.mode || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('detail.retryTimes')">{{ task.retryTimes ?? 0 }}</el-descriptions-item>
          <el-descriptions-item :label="t('detail.callerId')">{{ task.callerId || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('detail.createTime')">{{ formatTime(task.createTime) }}</el-descriptions-item>
          <el-descriptions-item :label="t('detail.executeTime')">{{ formatTime(task.executeTime) }}</el-descriptions-item>
          <el-descriptions-item :label="t('detail.completeTime')">{{ formatTime(task.completeTime) }}</el-descriptions-item>
          <el-descriptions-item :label="t('detail.strategy')">{{ task.strategy || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-tabs v-model="activeTab">
          <el-tab-pane :label="t('detail.tabInput')" name="input">
            <pre class="json-block">{{ prettyInput || '-' }}</pre>
          </el-tab-pane>
          <el-tab-pane :label="t('detail.tabOutput')" name="output">
            <pre class="json-block">{{ prettyOutput || '-' }}</pre>
          </el-tab-pane>
          <el-tab-pane :label="t('detail.tabError')" name="error">
            <pre class="json-block error-block">{{ task.error || '-' }}</pre>
          </el-tab-pane>
          <el-tab-pane :label="t('detail.tabMeta')" name="meta">
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item :label="t('detail.retryPlan')">{{ (task.retryPlan || []).join(', ') || '-' }}</el-descriptions-item>
              <el-descriptions-item :label="t('detail.delayTime')">{{ task.delayTime ?? 0 }}s</el-descriptions-item>
              <el-descriptions-item :label="t('detail.executeExpire')">{{ task.executeExpire ?? 0 }}s</el-descriptions-item>
              <el-descriptions-item :label="t('detail.resultExpire')">{{ task.resultExpire ?? 0 }}s</el-descriptions-item>
              <el-descriptions-item :label="t('detail.tag')">{{ task.tag || '-' }}</el-descriptions-item>
              <el-descriptions-item :label="t('detail.callbackStatus')">{{ task.callbackStatus || '-' }}</el-descriptions-item>
              <el-descriptions-item :label="t('detail.callbackRetryTimes')">{{ task.callbackRetryTimes ?? 0 }}</el-descriptions-item>
              <el-descriptions-item :label="t('detail.scheduleTime')">{{ formatTime(task.scheduleTime) }}</el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>
        </el-tabs>

        <el-divider />

        <div class="action-bar">
          <el-button type="primary" :loading="actionLoading" @click="doReplay" data-testid="replay-btn">
            {{ t('detail.actionReplay') }}
          </el-button>
          <el-button :loading="actionLoading" @click="doForceRetry" data-testid="force-retry-btn">
            {{ t('detail.actionForceRetry') }}
          </el-button>
          <el-button type="success" :loading="actionLoading" @click="doForceComplete('SUCCESS')" data-testid="force-complete-success-btn">
            {{ t('detail.actionForceSuccess') }}
          </el-button>
          <el-button type="warning" :loading="actionLoading" @click="doForceComplete('FAIL')" data-testid="force-complete-fail-btn">
            {{ t('detail.actionForceFail') }}
          </el-button>
          <el-button type="danger" :loading="actionLoading" @click="doDelete" data-testid="delete-btn">
            {{ t('detail.actionDelete') }}
          </el-button>
        </div>
      </template>
      <el-empty v-else :description="t('common.noData')" />
    </div>
  </el-drawer>
</template>

<style scoped>
.json-block {
  background: var(--app-bg-page);
  border: 1px solid var(--app-border-extra-light);
  border-radius: var(--brand-radius-md);
  padding: 12px;
  font-family: 'SF Mono', Menlo, Consolas, monospace;
  font-size: 12px;
  max-height: 360px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--app-text);
  margin: 0;
}
.error-block {
  color: var(--el-color-danger);
  background: color-mix(in srgb, var(--el-color-danger) 6%, var(--app-bg-page));
  border-color: color-mix(in srgb, var(--el-color-danger) 30%, var(--app-border-extra-light));
}
.action-bar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
