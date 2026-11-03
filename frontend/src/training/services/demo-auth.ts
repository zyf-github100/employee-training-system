import type { DemoAccount } from '../types'

export const DEMO_PASSWORD = 'Zhixing@123'
export const emailValid = (value: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)
export function passwordError(value: string): string {
  if (value.length < 8 || value.length > 32 || !/[a-zA-Z]/.test(value) || !/\d/.test(value))
    return '请设置 8–32 位密码，至少包含字母和数字'
  if (/\s/.test(value)) return '密码不能包含空格'
  return ''
}
// Demonstration only: client-held credentials do not provide authentication security.
export async function deriveDemoPassword(password: string, salt: string): Promise<string> {
  if (!globalThis.crypto?.subtle)
    throw new Error('当前环境不支持本地密码演示，请使用 localhost 或 HTTPS')
  const key = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(password),
    'PBKDF2',
    false,
    ['deriveBits']
  )
  const bits = await crypto.subtle.deriveBits(
    { name: 'PBKDF2', salt: new TextEncoder().encode(salt), iterations: 100000, hash: 'SHA-256' },
    key,
    256
  )
  return Array.from(new Uint8Array(bits), (n) => n.toString(16).padStart(2, '0')).join('')
}
export async function checkDemoPassword(account: DemoAccount, password: string) {
  return account.passwordHash
    ? (await deriveDemoPassword(password, account.salt)) === account.passwordHash
    : password === DEMO_PASSWORD
}
export async function updateDemoPassword(account: DemoAccount, password: string) {
  const error = passwordError(password)
  if (error) throw new Error(error)
  const salt = crypto.randomUUID()
  const passwordHash = await deriveDemoPassword(password, salt)
  Object.assign(account, { salt, passwordHash, passwordChangedAt: new Date().toISOString() })
}
