// 师傅端登录会话：统一复用 @serviceos/auth-context 的 OIDC PKCE（与 Admin 一致，令牌存 localStorage，
// 回调固定 <origin>/auth/callback，client 为 serviceos-local-cli）。本模块只做薄适配，保持旧端调用面。
import {
  accessToken as sessionAccessToken,
  beginLogin as beginOidcLogin,
  completeLogin as completeOidcLogin,
  currentSession,
  endSession,
} from '@serviceos/auth-context'

export const accessToken = (): string | null => sessionAccessToken()

/** 本地/正式均由受审 OIDC 提供身份入口，前端不接受手工 Token；此处恒为可用。 */
export const isLoginAvailable = (): boolean => true

export function isAuthenticated(): boolean {
  return currentSession().authenticated
}

export async function beginLogin(returnPathOverride?: string): Promise<void> {
  await beginOidcLogin(returnPathOverride)
}

export async function completeLogin(search: string): Promise<string> {
  return completeOidcLogin(search)
}

/** 显式退出并结束 Keycloak 单点会话，避免切换角色时被旧会话自动登录。 */
export function logout(): void {
  endSession()
}
