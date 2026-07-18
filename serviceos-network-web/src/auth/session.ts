import { createMemoryAccessTokenStore } from '@serviceos/web-core'

const tokenStore = createMemoryAccessTokenStore()
const verifierKey = 'serviceos.network.oidc.pkceVerifier'
const stateKey = 'serviceos.network.oidc.state'
const returnPathKey = 'serviceos.network.oidc.returnPath'

function issuer() { return (import.meta.env.VITE_OIDC_ISSUER ?? import.meta.env.VITE_DEV_OIDC_ISSUER ?? '').replace(/\/$/, '') }
function clientId() { return import.meta.env.VITE_OIDC_CLIENT_ID ?? import.meta.env.VITE_DEV_OIDC_CLIENT_ID ?? '' }
function enabled() {
  const explicitlyEnabled = import.meta.env.VITE_OIDC_ENABLED === 'true'
    || (import.meta.env.DEV && import.meta.env.VITE_DEV_OIDC_ENABLED === 'true')
  if (!explicitlyEnabled) return false
  if (!issuer() || !clientId()) throw new Error('OIDC 已启用但 issuer/clientId 配置不完整')
  if (!import.meta.env.DEV && !issuer().startsWith('https://')) {
    throw new Error('非开发环境 OIDC issuer 必须使用 HTTPS')
  }
  return true
}
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
export const isLoginAvailable = enabled
export function logout() {
  tokenStore.clear()
  sessionStorage.removeItem(verifierKey)
  sessionStorage.removeItem(stateKey)
  sessionStorage.removeItem(returnPathKey)
}

/**
 * Web 公共客户端使用 Authorization Code + PKCE；授权回调完成后 Token 只进入 M250
 * 内存 Store，不写 local/session storage。sessionStorage 仅保存一次性 state、verifier 与返回路径。
 */
export async function beginLogin(returnPathOverride?: string) {
  if (!enabled()) throw new Error('OIDC 未配置')
  const verifier = randomUrlSafe()
  const state = randomUrlSafe(32)
  sessionStorage.setItem(verifierKey, verifier)
  sessionStorage.setItem(stateKey, state)
  const currentPath = `${window.location.pathname}${window.location.search}${window.location.hash}`
  const returnPath = returnPathOverride ?? currentPath
  if (!returnPath.startsWith('/') || returnPath.startsWith('//')) throw new Error('OIDC 返回路径非法')
  sessionStorage.setItem(returnPathKey, returnPath)
  const url = new URL(`${issuer()}/protocol/openid-connect/auth`)
  Object.entries({ client_id: clientId(), redirect_uri: redirectUri(), response_type: 'code', scope: 'openid profile', state,
    code_challenge_method: 'S256', code_challenge: await challenge(verifier) }).forEach(([key, value]) => url.searchParams.set(key, value))
  window.location.assign(url)
}

export async function completeLogin(search: string) {
  if (!enabled()) throw new Error('OIDC 未启用')
  const query = new URLSearchParams(search)
  const oidcError = query.get('error')
  if (oidcError) throw new Error(query.get('error_description') ?? oidcError)
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
  const returnPath = sessionStorage.getItem(returnPathKey) ?? '/network-portal/workbench'
  sessionStorage.removeItem(returnPathKey)
  return returnPath.startsWith('/') && !returnPath.startsWith('//')
    ? returnPath
    : '/network-portal/workbench'
}
