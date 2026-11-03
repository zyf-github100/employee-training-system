import { ref } from 'vue'
import { ApiError, currentUser, refresh } from '../../connected/api'
export const sessionError = ref('')
let initialized: Promise<void> | undefined
export function restoreSession(): Promise<void> {
  return (initialized ??= (async () => {
    try {
      await refresh()
    } catch (error) {
      if (!(error instanceof ApiError && error.status === 401))
        sessionError.value = '暂时无法连接服务，请稍后重试。'
    }
  })())
}
export { currentUser }
export function errorMessage(error: unknown) {
  return error instanceof ApiError
    ? `${error.message}${error.traceId ? `（请求编号 ${error.traceId}）` : ''}`
    : '网络连接失败，请检查连接后重试。'
}
