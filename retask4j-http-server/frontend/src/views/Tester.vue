<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import axios from 'axios'

const { t } = useI18n()

const topic = ref('demo')
const path = ref('/proxy/push')
const mode = ref<'NORMAL' | 'FUNCTION' | 'CALLBACK'>('NORMAL')
const inputJson = ref('{\n  "orderId": "demo-001",\n  "amount": 100\n}')
const retryPlan = ref('')
const taskTiming = ref<number | null>(null)
const callbackUrl = ref('')
const submitting = ref(false)
const result = ref<{ ok: boolean; status: number; data: string; durationMs: number } | null>(null)
const error = ref<string | null>(null)

const baseUrl = window.location.origin

const buildUrl = () => {
  const p = path.value || '/proxy/push'
  return `${baseUrl}${p}/https://httpbin.org/post`
}

const submit = async () => {
  submitting.value = true
  result.value = null
  error.value = null
  const start = performance.now()
  try {
    let parsed: unknown
    try { parsed = JSON.parse(inputJson.value) }
    catch (e) { throw new Error(t('tester.invalidJson', { msg: String(e) })) }
    const headers: Record<string, string> = { 'Content-Type': 'application/json' }
    if (retryPlan.value.trim()) headers['retask4j-retry-plan'] = retryPlan.value.trim()
    if (taskTiming.value != null) headers['retask4j-task-timing'] = String(taskTiming.value)
    if (callbackUrl.value.trim()) headers['retask4j-callback-url'] = callbackUrl.value.trim()
    const response = await axios.post(buildUrl(), parsed, { headers, timeout: 30000 })
    result.value = {
      ok: true,
      status: response.status,
      data: typeof response.data === 'string' ? response.data : JSON.stringify(response.data, null, 2),
      durationMs: Math.round(performance.now() - start),
    }
  } catch (e) {
    const msg = axios.isAxiosError(e) ? `${e.message} (status=${e.response?.status})` : String(e)
    error.value = msg
    result.value = {
      ok: false,
      status: axios.isAxiosError(e) ? e.response?.status ?? 0 : 0,
      data: axios.isAxiosError(e) && e.response?.data ? JSON.stringify(e.response.data, null, 2) : '',
      durationMs: Math.round(performance.now() - start),
    }
  } finally {
    submitting.value = false
  }
}

const presetNormal = () => {
  topic.value = 'demo'
  path.value = '/proxy/push'
  mode.value = 'NORMAL'
  retryPlan.value = ''
  taskTiming.value = null
  callbackUrl.value = ''
}

const presetRetry = () => {
  topic.value = 'demo'
  path.value = '/proxy/push'
  mode.value = 'NORMAL'
  retryPlan.value = '[2,5,10]'
  taskTiming.value = null
  callbackUrl.value = ''
}

const presetSchedule = () => {
  topic.value = 'demo'
  path.value = '/proxy/push'
  mode.value = 'NORMAL'
  taskTiming.value = Date.now() + 60_000
  retryPlan.value = ''
  callbackUrl.value = ''
}

const presetCallback = () => {
  topic.value = 'demo'
  path.value = '/proxy/task'
  mode.value = 'CALLBACK'
  callbackUrl.value = `${baseUrl}/demo/callback-receiver`
  retryPlan.value = ''
  taskTiming.value = null
}
</script>

<template>
  <div>
    <div class="app-section">
      <div class="app-section__title">{{ t('tester.title') }}</div>
      <div class="app-section__desc">{{ t('tester.subtitle') }}</div>

      <div style="display: flex; gap: 8px; margin-bottom: 16px; flex-wrap: wrap">
        <el-button size="small" @click="presetNormal">NORMAL</el-button>
        <el-button size="small" @click="presetRetry">+ Retry</el-button>
        <el-button size="small" @click="presetSchedule">+ Schedule</el-button>
        <el-button size="small" @click="presetCallback">CALLBACK</el-button>
      </div>

      <el-form label-width="140px">
        <el-form-item :label="t('tester.topic')">
          <el-input v-model="topic" :placeholder="t('tester.topicHint')" />
        </el-form-item>
        <el-form-item :label="t('tester.path')">
          <el-input v-model="path" :placeholder="'/proxy/push'" />
        </el-form-item>
        <el-form-item :label="t('tester.mode')">
          <el-radio-group v-model="mode">
            <el-radio-button value="NORMAL">NORMAL</el-radio-button>
            <el-radio-button value="FUNCTION">FUNCTION</el-radio-button>
            <el-radio-button value="CALLBACK">CALLBACK</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('tester.retryPlan')">
          <el-input v-model="retryPlan" :placeholder="t('tester.retryPlanHint')" />
        </el-form-item>
        <el-form-item :label="t('tester.taskTiming')">
          <el-input-number v-model="taskTiming" :placeholder="t('tester.taskTimingHint')" :controls="false" style="width: 240px" />
        </el-form-item>
        <el-form-item :label="t('tester.callbackUrl')">
          <el-input v-model="callbackUrl" :placeholder="t('tester.callbackUrlHint')" />
        </el-form-item>
        <el-form-item :label="t('tester.inputJson')">
          <el-input v-model="inputJson" type="textarea" :rows="8" :placeholder="'{ ... }'" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submit" data-testid="tester-submit-btn">
            {{ t('tester.submit') }}
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <div v-if="result || error" class="app-section">
      <div class="app-section__title">{{ t('tester.resultTitle') }}</div>
      <el-descriptions :column="3" border size="small" style="margin-bottom: 12px">
        <el-descriptions-item :label="t('tester.status')">
          <el-tag :type="result?.ok ? 'success' : 'danger'">
            {{ result?.status ?? '-' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('tester.duration')">{{ result?.durationMs ?? 0 }} ms</el-descriptions-item>
        <el-descriptions-item :label="t('tester.url')">
          <code style="font-size: 11px">{{ buildUrl() }}</code>
        </el-descriptions-item>
      </el-descriptions>
      <el-alert v-if="error" :title="t('common.error')" :description="error" type="error" show-icon :closable="false" style="margin-bottom: 12px" />
      <pre class="response-block">{{ result?.data || (error ? '' : '...') }}</pre>
    </div>
  </div>
</template>

<style scoped>
.response-block {
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
</style>
