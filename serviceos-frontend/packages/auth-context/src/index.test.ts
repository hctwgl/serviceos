import { beforeEach, describe, expect, it } from 'vitest'
import { currentSession } from './index'

class MemoryStorage implements Storage {
  private readonly values = new Map<string, string>()

  get length() {
    return this.values.size
  }

  clear() {
    this.values.clear()
  }

  getItem(key: string) {
    return this.values.get(key) ?? null
  }

  key(index: number) {
    return [...this.values.keys()][index] ?? null
  }

  removeItem(key: string) {
    this.values.delete(key)
  }

  setItem(key: string, value: string) {
    this.values.set(key, value)
  }
}

beforeEach(() => {
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: new MemoryStorage() })
  Object.defineProperty(globalThis, 'sessionStorage', { configurable: true, value: new MemoryStorage() })
})

describe('currentSession', () => {
  it('访问令牌过期时保留正在进行的 PKCE 登录事务', () => {
    localStorage.setItem('serviceos.accessToken', 'expired-token')
    localStorage.setItem('serviceos.accessTokenExpiresAt', String(Date.now() - 1))
    sessionStorage.setItem('serviceos.oidc.pkceVerifier', 'verifier-in-progress')
    sessionStorage.setItem('serviceos.oidc.state', 'state-in-progress')

    expect(currentSession()).toEqual({ authenticated: false, expiresAt: null })
    expect(localStorage.getItem('serviceos.accessToken')).toBeNull()
    expect(sessionStorage.getItem('serviceos.oidc.pkceVerifier')).toBe('verifier-in-progress')
    expect(sessionStorage.getItem('serviceos.oidc.state')).toBe('state-in-progress')
  })
})
