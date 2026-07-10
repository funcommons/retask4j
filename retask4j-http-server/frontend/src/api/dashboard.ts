import axios from 'axios'

const TOKEN_KEY = 'retask4j.dashboard.token'

const http = axios.create({
  baseURL: '/dashboard/api',
  timeout: 10000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers['X-Dashboard-Token'] = token
  }
  return config
})

export interface TopicInfo {
  topic: string
  working: number
  pending: number
  timing: number
  retry: number
  callbackWorking: number
  callbackPending: number
}

export interface OverviewResponse {
  topics: TopicInfo[]
  totalTopics: number
}

export interface TaskMessage {
  id: string
  topic?: string
  status?: string
  mode?: string
  retryTimes?: number
  retryPlan?: number[]
  delayTime?: number
  executeExpire?: number
  resultExpire?: number
  strategy?: string
  tag?: string
  callerId?: string
  createTime?: number
  scheduleTime?: number
  executeTime?: number
  completeTime?: number
  callbackStatus?: string
  callbackRetryTimes?: number
  input?: string | null
  output?: string | null
  error?: string | null
}

export interface TasksResponse {
  messages: TaskMessage[]
  offset: number
  limit: number
}

export const dashboardApi = {
  setToken(token: string) {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
  },
  getToken(): string {
    return localStorage.getItem(TOKEN_KEY) || ''
  },
  overview: () => http.get<OverviewResponse>('/overview').then(r => r.data),
  tasks: (topic: string, offset = 0, limit = 20) =>
    http.get<TasksResponse>(`/tasks/${encodeURIComponent(topic)}`, { params: { offset, limit } }).then(r => r.data),
  taskDetail: (topic: string, id: string) =>
    http.get<TaskMessage>(`/tasks/${encodeURIComponent(topic)}/${encodeURIComponent(id)}`).then(r => r.data),
  replay: (topic: string, id: string) =>
    http.post<{ ok: boolean; operation: string }>(`/tasks/${encodeURIComponent(topic)}/${encodeURIComponent(id)}/replay`).then(r => r.data),
  forceRetry: (topic: string, id: string) =>
    http.post<{ ok: boolean; operation: string }>(`/tasks/${encodeURIComponent(topic)}/${encodeURIComponent(id)}/force-retry`).then(r => r.data),
  forceComplete: (topic: string, id: string, status: 'SUCCESS' | 'FAIL', output: string, error: string) =>
    http.post<{ ok: boolean; operation: string; status: string }>(
      `/tasks/${encodeURIComponent(topic)}/${encodeURIComponent(id)}/force-complete`,
      { status, output, error },
      { headers: { 'Content-Type': 'application/json' } },
    ).then(r => r.data),
  deleteTask: (topic: string, id: string) =>
    http.delete<{ ok: boolean; operation: string; removed: number }>(`/tasks/${encodeURIComponent(topic)}/${encodeURIComponent(id)}`).then(r => r.data),
  monitors: () => http.get<{ callers: Array<{ topic: string; metrics: Record<string, number> }> }>('/monitors').then(r => r.data),
  metrics: () => http.get<MetricsResponse>('/metrics').then(r => r.data),
  alerts: () => http.get<AlertsResponse>('/alerts').then(r => r.data),
}

export interface AlertItem {
  id: number
  ruleId: string
  ruleName: string
  severity: string
  snapshot: Record<string, unknown>
  firedAt: number
  resolved: boolean
}

export interface AlertsResponse {
  active: AlertItem[]
  history: AlertItem[]
  snapshot: Record<string, unknown>
}

export interface MetricsResponse {
  success: number
  fail: number
  successRate: number
  sendSuccess: number
  sendFail: number
  sendSuccessRate: number
  funcComplete: number
  funcTimeout: number
  funcResultMissing: number
  callbackComplete: number
  callbackFail: number
}
