const ACCESS_TOKEN_KEY = 'serviceos.accessToken'
const ACCESS_TOKEN_EXPIRY_KEY = 'serviceos.accessTokenExpiresAt'
const PKCE_VERIFIER_KEY = 'serviceos.oidc.pkceVerifier'
const OIDC_STATE_KEY = 'serviceos.oidc.state'

export const AUTH_REQUIRED_EVENT = 'serviceos:auth-required'

export type LocalOidcSession = {
  authenticated: boolean
  expiresAt: number | null
}

function localOidcEnabled(): boolean {
  return import.meta.env.DEV && import.meta.env.VITE_DEV_OIDC_ENABLED === 'true'
}

function issuer(): string {
  return (import.meta.env.VITE_DEV_OIDC_ISSUER ?? 'http://localhost:8081/realms/serviceos').replace(
    /\/$/,
    '',
  )
}

function clientId(): string {
  return import.meta.env.VITE_DEV_OIDC_CLIENT_ID ?? 'serviceos-local-cli'
}

function redirectUri(): string {
  return `${window.location.origin}/auth/callback`
}

function randomUrlSafe(length = 64): string {
  const bytes = crypto.getRandomValues(new Uint8Array(length))
  return btoa(String.fromCharCode(...bytes))
    .replaceAll('+', '-')
    .replaceAll('/', '_')
    .replaceAll('=', '')
}

async function sha256UrlSafe(value: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value))
  return btoa(String.fromCharCode(...new Uint8Array(digest)))
    .replaceAll('+', '-')
    .replaceAll('/', '_')
    .replaceAll('=', '')
}

/**
 * 仅在显式开启的 Vite 开发模式使用本地 Keycloak。授权码 + PKCE 不需要前端保存
 * client secret；生产构建始终失败关闭，必须由正式部署提供受审 OIDC/BFF 方案。
 */
export async function beginLocalOidcLogin(): Promise<void> {
  if (!localOidcEnabled()) {
    throw new Error('本地 OIDC 未启用，生产环境禁止使用开发登录适配器')
  }
  const verifier = randomUrlSafe()
  const state = randomUrlSafe(32)
  sessionStorage.setItem(PKCE_VERIFIER_KEY, verifier)
  sessionStorage.setItem(OIDC_STATE_KEY, state)

  const authorize = new URL(`${issuer()}/protocol/openid-connect/auth`)
  authorize.searchParams.set('client_id', clientId())
  authorize.searchParams.set('redirect_uri', redirectUri())
  authorize.searchParams.set('response_type', 'code')
  authorize.searchParams.set('scope', 'openid profile')
  authorize.searchParams.set('state', state)
  authorize.searchParams.set('code_challenge_method', 'S256')
  authorize.searchParams.set('code_challenge', await sha256UrlSafe(verifier))
  window.location.assign(authorize)
}

export async function completeLocalOidcLogin(search: string): Promise<void> {
  if (!localOidcEnabled()) {
    throw new Error('本地 OIDC 未启用')
  }
  const query = new URLSearchParams(search)
  const error = query.get('error')
  if (error) {
    throw new Error(query.get('error_description') ?? error)
  }
  const code = query.get('code')
  const state = query.get('state')
  const expectedState = sessionStorage.getItem(OIDC_STATE_KEY)
  const verifier = sessionStorage.getItem(PKCE_VERIFIER_KEY)
  if (!code || !state || !expectedState || state !== expectedState || !verifier) {
    clearLocalOidcSession()
    throw new Error('OIDC 回调状态校验失败，请重新登录')
  }

  const body = new URLSearchParams({
    grant_type: 'authorization_code',
    client_id: clientId(),
    redirect_uri: redirectUri(),
    code,
    code_verifier: verifier,
  })
  const response = await fetch(`${issuer()}/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  })
  if (!response.ok) {
    clearLocalOidcSession()
    throw new Error(`OIDC token 交换失败（HTTP ${response.status}）`)
  }
  const token = (await response.json()) as { access_token?: string; expires_in?: number }
  if (!token.access_token || !token.expires_in) {
    clearLocalOidcSession()
    throw new Error('OIDC token 响应缺少 access_token 或 expires_in')
  }
  localStorage.setItem(ACCESS_TOKEN_KEY, token.access_token)
  localStorage.setItem(ACCESS_TOKEN_EXPIRY_KEY, String(Date.now() + token.expires_in * 1000))
  sessionStorage.removeItem(PKCE_VERIFIER_KEY)
  sessionStorage.removeItem(OIDC_STATE_KEY)
}

export function clearLocalOidcSession(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(ACCESS_TOKEN_EXPIRY_KEY)
  sessionStorage.removeItem(PKCE_VERIFIER_KEY)
  sessionStorage.removeItem(OIDC_STATE_KEY)
}

export function currentLocalOidcSession(): LocalOidcSession {
  const token = localStorage.getItem(ACCESS_TOKEN_KEY)
  const expiresAt = Number(localStorage.getItem(ACCESS_TOKEN_EXPIRY_KEY))
  const validExpiry = Number.isFinite(expiresAt) && expiresAt > Date.now()
  if (!token || !validExpiry) {
    if (token || localStorage.getItem(ACCESS_TOKEN_EXPIRY_KEY)) {
      clearLocalOidcSession()
    }
    return { authenticated: false, expiresAt: null }
  }
  return { authenticated: true, expiresAt }
}

export function usableAccessToken(): string | null {
  return currentLocalOidcSession().authenticated ? localStorage.getItem(ACCESS_TOKEN_KEY) : null
}

export function isLocalOidcAvailable(): boolean {
  return localOidcEnabled()
}

export function requireAuthentication(): void {
  clearLocalOidcSession()
  window.dispatchEvent(new CustomEvent(AUTH_REQUIRED_EVENT))
}
