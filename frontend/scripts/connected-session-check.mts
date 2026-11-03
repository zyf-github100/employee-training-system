import assert from 'node:assert/strict'
import { test, afterEach } from 'node:test'
import { api, clearSession, currentUser, login } from '../src/connected/api'

const originalFetch = globalThis.fetch
afterEach(() => {
  globalThis.fetch = originalFetch
  clearSession()
})

function server() {
  const user = { id: 1, employeeNo: 'TEST', name: 'Test', roleCode: 'ADMIN', version: 0 }
  const state = {
    csrf: 'initial',
    csrfReads: 0,
    refreshes: 0,
    expiredReads: 0,
    revoked: false,
    csrfOffline: false
  }
  const ok = (data: unknown) => Response.json({ code: 'OK', data })
  const denied = (status: number) =>
    Response.json({ code: 'REJECTED', message: 'rejected' }, { status })
  globalThis.fetch = async (input, init) => {
    const path = String(input)
    const headers = new Headers(init?.headers)
    assert.equal(init?.credentials, 'include')
    if (path === '/api/v1/auth/csrf') {
      state.csrfReads++
      if (state.csrfOffline) throw new TypeError('network unavailable')
      return ok(state.csrf)
    }
    if (path === '/api/v1/auth/login') {
      assert.equal(headers.get('X-XSRF-TOKEN'), state.csrf)
      return ok({ accessToken: 'old-access', user })
    }
    if (path === '/api/v1/auth/refresh') {
      state.refreshes++
      assert.equal(headers.has('Authorization'), false, 'refresh must not carry an expired bearer')
      if (headers.get('X-XSRF-TOKEN') !== state.csrf) return denied(403)
      if (state.revoked) return denied(401)
      return ok({ accessToken: 'renewed-access', user })
    }
    if (path === '/api/v1/me' || path === '/api/v1/admin/employee-imports/template') {
      if (headers.get('Authorization') !== 'Bearer renewed-access') {
        state.expiredReads++
        return denied(401)
      }
      return path === '/api/v1/me' ? ok(user) : new Response('workbook')
    }
    throw new Error(`Unexpected request: ${path}`)
  }
  return state
}

test('expired access token refreshes with the current CSRF value and retries the JSON request', async () => {
  const state = server()
  await login('TEST', 'test-password')
  state.csrf = 'rotated-cookie'
  assert.equal((await api.me()).employeeNo, 'TEST')
  assert.equal(state.refreshes, 1)
  assert.equal(state.expiredReads, 1)
  assert.equal(currentUser.value?.employeeNo, 'TEST')
})

test('concurrent JSON and file requests share one refresh after CSRF rotation', async () => {
  const state = server()
  await login('TEST', 'test-password')
  state.csrf = 'rotated-cookie'
  const [user, file] = await Promise.all([api.me(), api.importTemplate()])
  assert.equal(user.employeeNo, 'TEST')
  assert.equal(await file.text(), 'workbook')
  assert.equal(state.refreshes, 1)
  assert.equal(state.expiredReads, 2)
})

test('a revoked refresh session clears local identity without looping', async () => {
  const state = server()
  await login('TEST', 'test-password')
  state.revoked = true
  await assert.rejects(api.me(), { status: 401 })
  assert.equal(currentUser.value, null)
  assert.equal(state.refreshes, 1)
})

test('a failed CSRF fetch does not attempt refresh or discard identity, and can be retried', async () => {
  const state = server()
  await login('TEST', 'test-password')
  state.csrf = 'rotated-cookie'
  state.csrfOffline = true
  await assert.rejects(api.me(), TypeError)
  assert.equal(state.refreshes, 0)
  assert.equal(currentUser.value?.employeeNo, 'TEST')
  state.csrfOffline = false
  assert.equal((await api.me()).employeeNo, 'TEST')
  assert.equal(state.refreshes, 1)
})
