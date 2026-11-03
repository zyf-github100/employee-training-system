import { ref } from 'vue'
import { api, currentUser, type PlatformSettings } from '../../connected/api'
export const platformSettings = ref<PlatformSettings | null>(null)
export const unreadAnnouncements = ref(0)
let account: number | undefined
let refreshedAt = 0
let request = 0
let pending: Promise<void> | null = null
export function syncPlatform(force = false): Promise<void> {
  const id = currentUser.value?.id
  if (account !== id) {
    account = id
    request++
    pending = null
    refreshedAt = 0
    platformSettings.value = null
    unreadAnnouncements.value = 0
  }
  if (!id) return Promise.resolve()
  if (pending) return force ? pending.catch(() => {}).then(() => syncPlatform(true)) : pending
  if (!force && Date.now() - refreshedAt < 30000) return Promise.resolve()
  const ticket = ++request
  pending = (async () => {
    try {
      const [settings, unread] = await Promise.all([api.platform(), api.unreadAnnouncements()])
      if (ticket === request && currentUser.value?.id === id) {
        if (!platformSettings.value || settings.version >= platformSettings.value.version)
          platformSettings.value = settings
        unreadAnnouncements.value = unread
        refreshedAt = Date.now()
      }
    } finally {
      if (ticket === request) pending = null
    }
  })()
  return pending
}
