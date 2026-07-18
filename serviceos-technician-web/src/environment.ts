export const TECHNICIAN_CLIENT_KIND = 'TECHNICIAN_WEB' as const

export type TechnicianEnvironmentName = 'local' | 'development' | 'test' | 'staging' | 'production'

export type TechnicianRuntimeEnvironment = Readonly<{
  name: TechnicianEnvironmentName
  apiBaseUrl: string
  clientKind: typeof TECHNICIAN_CLIENT_KIND
  clientVersion: string
}>

export type TechnicianEnvironmentInput = Readonly<{
  mode: string
  apiBaseUrl?: string
  clientVersion?: string
}>

const environmentNames = new Set<TechnicianEnvironmentName>([
  'local', 'development', 'test', 'staging', 'production',
])
const clientVersionPattern = /^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]{1,32})?$/

/**
 * 构建环境只选择服务地址和可观测版本，不携带网点、角色、Capability 或任何授权结果。
 * 非开发环境禁止明文 HTTP；API 地址必须是同源绝对路径或 HTTPS。
 */
export function resolveTechnicianEnvironment(input: TechnicianEnvironmentInput): TechnicianRuntimeEnvironment {
  const name = input.mode === 'development' ? 'development' : input.mode
  if (!environmentNames.has(name as TechnicianEnvironmentName)) {
    throw new Error(`不支持的 Technician Web 环境：${input.mode}`)
  }

  const apiBaseUrl = input.apiBaseUrl?.trim() || '/api/v1'
  if (!apiBaseUrl.startsWith('/') && !apiBaseUrl.startsWith('https://')) {
    throw new Error('Technician Web API 地址必须是同源路径或 HTTPS')
  }
  const clientVersion = input.clientVersion?.trim() || '0.1.0'
  if (!clientVersionPattern.test(clientVersion)) {
    throw new Error('Technician Web clientVersion 必须是受支持的语义版本')
  }

  return Object.freeze({
    name: name as TechnicianEnvironmentName,
    apiBaseUrl,
    clientKind: TECHNICIAN_CLIENT_KIND,
    clientVersion,
  })
}
