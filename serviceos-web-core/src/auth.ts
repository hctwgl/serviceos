export type AccessTokenSnapshot = Readonly<{
  accessToken: string
  expiresAtEpochMs: number
}>

export type AccessTokenProvider = () => AccessTokenSnapshot | null | Promise<AccessTokenSnapshot | null>

export type MemoryAccessTokenStore = Readonly<{
  set(snapshot: AccessTokenSnapshot): void
  clear(): void
  current(): AccessTokenSnapshot | null
  provider: AccessTokenProvider
}>

/**
 * Web 共享层只提供内存 Token 容器。刷新、OIDC/BFF 交换和重新登录由宿主应用负责，
 * 共享层不写 localStorage/sessionStorage，也不猜测任何 Portal 身份。
 */
export function createMemoryAccessTokenStore(
  now: () => number = () => Date.now(),
  expirySkewMs = 30_000,
): MemoryAccessTokenStore {
  let snapshot: AccessTokenSnapshot | null = null

  const current = (): AccessTokenSnapshot | null => {
    if (!snapshot) return null
    if (snapshot.expiresAtEpochMs <= now() + expirySkewMs) {
      snapshot = null
      return null
    }
    return snapshot
  }

  return {
    set(next) {
      if (!next.accessToken || !Number.isFinite(next.expiresAtEpochMs)) {
        throw new Error('Access Token 快照非法')
      }
      snapshot = Object.freeze({ ...next })
    },
    clear() {
      snapshot = null
    },
    current,
    provider: current,
  }
}
