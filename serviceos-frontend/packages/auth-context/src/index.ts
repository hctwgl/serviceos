const ACCESS_TOKEN_KEY = 'serviceos.accessToken'
const ACCESS_TOKEN_EXPIRY_KEY = 'serviceos.accessTokenExpiresAt'
const PKCE_VERIFIER_KEY = 'serviceos.oidc.pkceVerifier'
const OIDC_STATE_KEY = 'serviceos.oidc.state'

function env(name: string, fallback = ''): string {
  const values = (import.meta as ImportMeta & { env?: Record<string, string> }).env
  return values?.[name] ?? fallback
}

function issuer(): string {
  return env('VITE_DEV_OIDC_ISSUER', 'http://localhost:8081/realms/serviceos').replace(/\/$/, '')
}

function clientId(): string {
  return env('VITE_DEV_OIDC_CLIENT_ID', 'serviceos-local-cli')
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

export function currentSession(): { authenticated: boolean; expiresAt: number | null } {
  const token = localStorage.getItem(ACCESS_TOKEN_KEY)
  const expiresAt = Number(localStorage.getItem(ACCESS_TOKEN_EXPIRY_KEY))
  if (!token || !Number.isFinite(expiresAt) || expiresAt <= Date.now()) {
    clearAccessSession()
    return { authenticated: false, expiresAt: null }
  }
  return { authenticated: true, expiresAt }
}

export function accessToken(): string | null {
  return currentSession().authenticated ? localStorage.getItem(ACCESS_TOKEN_KEY) : null
}

export function currentIdentity(): { displayName: string; initials: string } {
  const token = accessToken()
  if (!token) return { displayName: '未登录', initials: '—' }
  try {
    const encoded = token.split('.')[1]
    if (!encoded) return { displayName: '已登录用户', initials: '用' }
    const normalized = encoded.replaceAll('-', '+').replaceAll('_', '/')
    const bytes = Uint8Array.from(atob(normalized), (character) => character.charCodeAt(0))
    const claims = JSON.parse(new TextDecoder().decode(bytes)) as {
      name?: string
      preferred_username?: string
    }
    const displayName = claims.name?.trim() || claims.preferred_username?.trim() || '已登录用户'
    return { displayName, initials: displayName.slice(0, 1) }
  } catch {
    return { displayName: '已登录用户', initials: '用' }
  }
}

function clearAccessSession(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(ACCESS_TOKEN_EXPIRY_KEY)
}

/**
 * 显式退出或登录失败时才清理 PKCE 事务。访问令牌自然过期时只能清理访问会话；如果同时删除
 * verifier 和 state，正在进行的重新登录会在摘要计算与页面跳转之间被并发 API 请求破坏。
 */
export function clearSession(): void {
  clearAccessSession()
  sessionStorage.removeItem(PKCE_VERIFIER_KEY)
  sessionStorage.removeItem(OIDC_STATE_KEY)
}

/**
 * 清除本地会话并结束 Keycloak 单点登录，确保切换产品角色时不会被旧会话自动登录。
 */
export function endSession(): void {
  clearSession()
  const logout = new URL(`${issuer()}/protocol/openid-connect/logout`)
  logout.searchParams.set('client_id', clientId())
  logout.searchParams.set('post_logout_redirect_uri', `${window.location.origin}/`)
  window.location.assign(logout)
}

/**
 * 本地开发使用标准 Authorization Code + PKCE。前端从不保存 client secret，正式部署必须由
 * 受审 OIDC/BFF 配置提供身份入口。
 */
export async function beginLogin(returnTo = window.location.pathname): Promise<void> {
  const verifier = randomUrlSafe()
  const state = randomUrlSafe(32)
  sessionStorage.setItem(PKCE_VERIFIER_KEY, verifier)
  sessionStorage.setItem(OIDC_STATE_KEY, state)
  sessionStorage.setItem('serviceos.oidc.returnTo', returnTo)

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

export async function completeLogin(search: string): Promise<string> {
  const query = new URLSearchParams(search)
  const code = query.get('code')
  const state = query.get('state')
  const verifier = sessionStorage.getItem(PKCE_VERIFIER_KEY)
  const expectedState = sessionStorage.getItem(OIDC_STATE_KEY)
  if (!code || !state || state !== expectedState || !verifier) {
    clearSession()
    throw new Error('登录回调校验失败，请重新登录')
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
    clearSession()
    throw new Error(`登录令牌交换失败（HTTP ${response.status}）`)
  }
  const result = (await response.json()) as { access_token?: string; expires_in?: number }
  if (!result.access_token || !result.expires_in) {
    clearSession()
    throw new Error('登录服务返回的信息不完整')
  }
  localStorage.setItem(ACCESS_TOKEN_KEY, result.access_token)
  localStorage.setItem(ACCESS_TOKEN_EXPIRY_KEY, String(Date.now() + result.expires_in * 1000))
  const returnTo = sessionStorage.getItem('serviceos.oidc.returnTo') ?? '/workbench'
  sessionStorage.removeItem('serviceos.oidc.returnTo')
  sessionStorage.removeItem(PKCE_VERIFIER_KEY)
  sessionStorage.removeItem(OIDC_STATE_KEY)
  return returnTo
}
