import assert from 'node:assert/strict'
import { afterEach, beforeEach, test } from 'node:test'
import { currentUser } from '../src/connected/api.ts'
import {
  platformSettings,
  syncPlatform,
  unreadAnnouncements
} from '../src/training/live/platform.ts'

const originalFetch = globalThis.fetch
let pending: Array<{ url: string; resolve: (value: Response) => void }> = []
const user = (id: number) => ({
  id,
  employeeNo: `QA${id}`,
  name: 'QA',
  roleCode: 'EMPLOYEE' as const,
  version: 0
})
const settings = (version: number) => ({
  companyName: `Company ${version}`,
  contactName: 'Training',
  contactEmail: '',
  unreadReminders: true,
  version,
  updatedAt: new Date().toISOString()
})
function finish(index: number, value: unknown) {
  pending[index].resolve(
    new Response(JSON.stringify({ code: 'OK', data: value }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    })
  )
}
beforeEach(async () => {
  currentUser.value = null
  await syncPlatform()
  pending = []
  globalThis.fetch = ((input: string | URL | Request) =>
    new Promise<Response>((resolve) =>
      pending.push({ url: String(input), resolve })
    )) as typeof fetch
})
afterEach(async () => {
  globalThis.fetch = originalFetch
  currentUser.value = null
  await syncPlatform()
})

test('a previous account response cannot populate the new account unread count', async () => {
  currentUser.value = user(1)
  const first = syncPlatform()
  currentUser.value = user(2)
  const second = syncPlatform()
  assert.equal(pending.length, 4)
  finish(0, settings(1))
  finish(1, 99)
  await first
  assert.equal(unreadAnnouncements.value, 0)
  finish(2, settings(2))
  finish(3, 3)
  await second
  assert.equal(unreadAnnouncements.value, 3)
  assert.equal(platformSettings.value?.version, 2)
})
test('a forced unread refresh waits for older requests and reads the updated server count', async () => {
  currentUser.value = user(1)
  const first = syncPlatform()
  const forced = syncPlatform(true)
  finish(0, settings(1))
  finish(1, 1)
  await first
  await new Promise((resolve) => setTimeout(resolve, 0))
  assert.equal(pending.length, 4)
  finish(2, settings(1))
  finish(3, 0)
  await forced
  assert.equal(unreadAnnouncements.value, 0)
})
test('an older background response cannot overwrite freshly saved settings', async () => {
  currentUser.value = user(1)
  const request = syncPlatform()
  platformSettings.value = settings(8)
  finish(0, settings(7))
  finish(1, 2)
  await request
  assert.equal(platformSettings.value?.companyName, 'Company 8')
  assert.equal(unreadAnnouncements.value, 2)
})
