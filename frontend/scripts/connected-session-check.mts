import assert from 'node:assert/strict'
import { test, afterEach } from 'node:test'
import { api, clearSession, currentUser, login, logout } from '../src/connected/api'

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

function delayedRefresh(accountChangedInAnotherTab = false) {
  const alice = { id: 1, employeeNo: 'ALICE', name: 'Alice', roleCode: 'EMPLOYEE', version: 0 }
  const bob = { ...alice, id: 2, employeeNo: 'BOB', name: 'Bob' }
  const ok = (data: unknown) => Response.json({ code: 'OK', data })
  let started!: () => void, release!: () => void
  const refreshing = new Promise<void>((resolve) => {
    started = resolve
  })
  const held = new Promise<void>((resolve) => {
    release = resolve
  })
  const requests: string[] = []
  globalThis.fetch = async (input, init) => {
    const path = String(input),
      authorization = new Headers(init?.headers).get('Authorization')
    if (path === '/api/v1/auth/csrf') return ok('csrf')
    if (path === '/api/v1/auth/login') {
      const isBob = JSON.parse(String(init?.body)).employeeNo === 'BOB'
      return ok({ accessToken: isBob ? 'bob' : 'alice-expired', user: isBob ? bob : alice })
    }
    if (path === '/api/v1/auth/logout') return ok(null)
    if (path === '/api/v1/auth/refresh') {
      started()
      await held
      return ok({ accessToken: 'alice-renewed', user: accountChangedInAnotherTab ? bob : alice })
    }
    if (path === '/api/v1/me') {
      requests.push(authorization ?? '')
      if (authorization === 'Bearer alice-expired')
        return Response.json({ code: 'EXPIRED' }, { status: 401 })
      return ok(authorization === 'Bearer bob' ? bob : alice)
    }
    throw new Error(`Unexpected request: ${path}`)
  }
  return { refreshing, release, requests }
}

test('a delayed refresh cannot restore identity after logout', async () => {
  const state = delayedRefresh()
  await login('ALICE', 'test-password')
  const pending = api.me()
  await state.refreshing
  await logout()
  state.release()
  await assert.rejects(pending, { code: 'SESSION_CHANGED' })
  assert.equal(currentUser.value, null)
  assert.deepEqual(state.requests, ['Bearer alice-expired'])
})

test('a previous account refresh cannot overwrite a newly signed-in account', async () => {
  const state = delayedRefresh()
  await login('ALICE', 'test-password')
  const pending = api.me()
  await state.refreshing
  await login('BOB', 'test-password')
  state.release()
  await assert.rejects(pending, { code: 'SESSION_CHANGED' })
  assert.equal(currentUser.value?.employeeNo, 'BOB')
  assert.equal((await api.me()).employeeNo, 'BOB')
  assert.deepEqual(state.requests, ['Bearer alice-expired', 'Bearer bob'])
})

test('a refresh for a different cookie account never replays the original account request', async () => {
  const state = delayedRefresh(true)
  await login('ALICE', 'test-password')
  const pending = api.me()
  await state.refreshing
  state.release()
  await assert.rejects(pending, { code: 'SESSION_CHANGED' })
  assert.equal(currentUser.value, null)
  assert.deepEqual(state.requests, ['Bearer alice-expired'])
})

test('an expired write arriving after an account switch is never retried for the new account', async () => {
  const state = delayedRefresh()
  const fetchFromServer = globalThis.fetch
  let sent!: () => void, release!: () => void
  const started = new Promise<void>((resolve) => {
    sent = resolve
  })
  const held = new Promise<void>((resolve) => {
    release = resolve
  })
  let writes = 0
  globalThis.fetch = async (input, init) => {
    if (String(input) === '/api/v1/me/profile') {
      writes++
      sent()
      await held
      return Response.json({ code: 'EXPIRED' }, { status: 401 })
    }
    return fetchFromServer(input, init)
  }
  await login('ALICE', 'test-password')
  const pending = api.updateProfile({ name: 'Alice changed', version: 0 })
  await started
  await login('BOB', 'test-password')
  release()
  await assert.rejects(pending, { code: 'SESSION_CHANGED' })
  assert.equal(currentUser.value?.employeeNo, 'BOB')
  assert.equal(writes, 1)
  assert.deepEqual(state.requests, [])
})
