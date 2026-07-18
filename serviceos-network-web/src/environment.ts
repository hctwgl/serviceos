export const NETWORK_CLIENT_KIND = 'NETWORK_WEB' as const

export type NetworkEnvironmentName = 'local' | 'development' | 'test' | 'staging' | 'production'

export type NetworkRuntimeEnvironment = Readonly<{
  name: NetworkEnvironmentName
  apiBaseUrl: string
  clientKind: typeof NETWORK_CLIENT_KIND
  clientVersion: string
}>

export type NetworkEnvironmentInput = Readonly<{
  mode: string
  apiBaseUrl?: string
  clientVersion?: string
}>

const environmentNames = new Set<NetworkEnvironmentName>([
  'local', 'development', 'test', 'staging', 'production',
])
const clientVersionPattern = /^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]{1,32})?$/

/**
 * 构建环境只选择服务地址和可观测版本，不携带网点、角色、Capability 或任何授权结果。
 * 非开发环境禁止明文 HTTP；API 地址必须是同源绝对路径或 HTTPS。
 */
export function resolveNetworkEnvironment(input: NetworkEnvironmentInput): NetworkRuntimeEnvironment {
  const name = input.mode === 'development' ? 'development' : input.mode
  if (!environmentNames.has(name as NetworkEnvironmentName)) {
    throw new Error(`不支持的 Network Web 环境：${input.mode}`)
  }

  const apiBaseUrl = input.apiBaseUrl?.trim() || '/api/v1'
  if (!apiBaseUrl.startsWith('/') && !apiBaseUrl.startsWith('https://')) {
    throw new Error('Network Web API 地址必须是同源路径或 HTTPS')
  }
  const clientVersion = input.clientVersion?.trim() || '0.1.0'
  if (!clientVersionPattern.test(clientVersion)) {
    throw new Error('Network Web clientVersion 必须是受支持的语义版本')
  }

  return Object.freeze({
    name: name as NetworkEnvironmentName,
    apiBaseUrl,
    clientKind: NETWORK_CLIENT_KIND,
    clientVersion,
  })
}
