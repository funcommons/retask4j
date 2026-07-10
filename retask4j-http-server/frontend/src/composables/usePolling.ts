import { ref, onMounted, onUnmounted } from 'vue'

/**
 * Polls a data loader at a regular interval with:
 * - visibilitychange pause: stops polling when the tab is hidden
 * - error backoff: on failure, delays the next attempt (5s → 10s → 30s → 60s cap)
 * - automatic re-sync when the tab becomes visible again
 *
 * @param loader async function that returns the data
 * @param intervalMs normal interval between successful polls
 * @param immediate whether to call loader immediately on mount
 */
export function usePolling<T>(loader: () => Promise<T>, intervalMs = 5000, immediate = true) {
  const data = ref<T | null>(null)
  const error = ref<string | null>(null)
  const loading = ref(false)
  const lastUpdate = ref<number>(0)

  let timer: number | undefined
  let consecutiveFailures = 0
  let active = true

  const backoffMs = (failures: number) => {
    // 5s, 10s, 30s, 60s (capped)
    if (failures <= 0) return intervalMs
    if (failures === 1) return intervalMs * 2
    if (failures === 2) return intervalMs * 6
    return 60_000
  }

  const tick = async () => {
    if (!active || document.hidden) return
    loading.value = true
    try {
      data.value = await loader()
      error.value = null
      consecutiveFailures = 0
      lastUpdate.value = Date.now()
      scheduleNext()
    } catch (e) {
      error.value = e instanceof Error ? e.message : String(e)
      consecutiveFailures++
      scheduleNext()
    } finally {
      loading.value = false
    }
  }

  const scheduleNext = () => {
    if (!active) return
    if (timer) clearTimeout(timer)
    timer = window.setTimeout(tick, backoffMs(consecutiveFailures))
  }

  const onVisibilityChange = () => {
    if (!active) return
    if (document.hidden) {
      // Pause: clear timer
      if (timer) {
        clearTimeout(timer)
        timer = undefined
      }
    } else {
      // Resume: poll immediately, then continue
      tick()
    }
  }

  onMounted(() => {
    document.addEventListener('visibilitychange', onVisibilityChange)
    if (immediate) tick()
  })

  onUnmounted(() => {
    active = false
    if (timer) clearTimeout(timer)
    document.removeEventListener('visibilitychange', onVisibilityChange)
  })

  return { data, error, loading, lastUpdate, refresh: tick }
}
