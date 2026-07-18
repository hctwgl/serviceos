import { createMemoryAccessTokenStore } from '@serviceos/web-core'

const tokenStore = createMemoryAccessTokenStore()
const verifierKey = 'serviceos.network.oidc.pkceVerifier'
const stateKey = 'serviceos.network.oidc.state'

function enabled() { return import.meta.env.DEV && import.meta.env.VITE_DEV_OIDC_ENABLED === 'true' }
function issuer() { return (import.meta.env.VITE_DEV_OIDC_ISSUER ?? '').replace(/\/$/, '') }
function clientId() { return import.meta.env.VITE_DEV_OIDC_CLIENT_ID ?? '' }
function redirectUri() { return `${window.location.origin}/auth/callback` }
function randomUrlSafe(length = 48) {
  const bytes = crypto.getRandomValues(new Uint8Array(length))
  return btoa(String.fromCharCode(...bytes)).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '')
}
async function challenge(value: string) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value))
  return btoa(String.fromCharCode(...new Uint8Array(digest))).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '')
}

export const accessToken = () => tokenStore.current()
export const isDevelopmentLoginAvailable = enabled
export const logout = () => tokenStore.clear()

/** 仅开发态 Authorization Code + PKCE；Token 只进入 M250 内存 Store，不写 local/session storage。 */
export async function beginDevelopmentLogin() {
  if (!enabled() || !issuer() || !clientId()) throw new Error('开发 OIDC 未配置')
  const verifier = randomUrlSafe()
  const state = randomUrlSafe(32)
  sessionStorage.setItem(verifierKey, verifier)
  sessionStorage.setItem(stateKey, state)
  const url = new URL(`${issuer()}/protocol/openid-connect/auth`)
  Object.entries({ client_id: clientId(), redirect_uri: redirectUri(), response_type: 'code', scope: 'openid profile', state,
    code_challenge_method: 'S256', code_challenge: await challenge(verifier) }).forEach(([key, value]) => url.searchParams.set(key, value))
  window.location.assign(url)
}

export async function completeDevelopmentLogin(search: string) {
  if (!enabled()) throw new Error('开发 OIDC 未启用')
  const query = new URLSearchParams(search)
  const code = query.get('code')
  const state = query.get('state')
  const verifier = sessionStorage.getItem(verifierKey)
  if (!code || !state || state !== sessionStorage.getItem(stateKey) || !verifier) throw new Error('OIDC 回调状态校验失败')
  const response = await fetch(`${issuer()}/protocol/openid-connect/token`, {
    method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'authorization_code', client_id: clientId(), redirect_uri: redirectUri(), code, code_verifier: verifier }),
  })
  if (!response.ok) throw new Error(`OIDC token 交换失败（HTTP ${response.status}）`)
  const body = await response.json() as { access_token?: string; expires_in?: number }
  if (!body.access_token || !body.expires_in) throw new Error('OIDC token 响应不完整')
  tokenStore.set({ accessToken: body.access_token, expiresAtEpochMs: Date.now() + body.expires_in * 1000 })
  sessionStorage.removeItem(verifierKey)
  sessionStorage.removeItem(stateKey)
}
