import { ref, onUnmounted, watch } from 'vue'

const TOKEN_KEY = 'retask4j.dashboard.token'

export function useSSE(onEvent: (event: { type: string; topic?: string; taskId?: string; [k: string]: unknown }) => void) {
  const connected = ref(false)
  const lastEvent = ref<unknown>(null)
  let es: EventSource | null = null
  let retryTimer: number | undefined
  let stopped = false

  const connect = () => {
    if (stopped) return
    const token = localStorage.getItem(TOKEN_KEY)
    if (!token) return
    try {
      es = new EventSource(`/dashboard/api/stream?token=${encodeURIComponent(token)}`)
      es.addEventListener('open', () => {
        connected.value = true
      })
      es.addEventListener('task-event', (e: MessageEvent) => {
        try {
          const data = JSON.parse(e.data)
          lastEvent.value = data
          onEvent(data)
        } catch (err) {
          // ignore malformed event
        }
      })
      es.addEventListener('error', () => {
        connected.value = false
        es?.close()
        es = null
        if (!stopped) {
          retryTimer = window.setTimeout(connect, 5000)
        }
      })
    } catch (e) {
      connected.value = false
      if (!stopped) {
        retryTimer = window.setTimeout(connect, 5000)
      }
    }
  }

  const start = () => {
    if (es) return
    stopped = false
    connect()
  }

  const stop = () => {
    stopped = true
    if (retryTimer) clearTimeout(retryTimer)
    retryTimer = undefined
    if (es) {
      es.close()
      es = null
    }
    connected.value = false
  }

  // Reconnect when token changes
  const origSet = localStorage.setItem.bind(localStorage)
  localStorage.setItem = function (k: string, v: string) {
    origSet(k, v)
    if (k === TOKEN_KEY) {
      stop()
      start()
    }
  }

  start()

  onUnmounted(() => {
    stop()
  })

  return { connected, lastEvent, start, stop }
}
