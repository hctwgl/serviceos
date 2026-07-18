import { createWebApiClient, safeProblemMessage, WebApiError } from '@serviceos/web-core'
import { accessToken } from '../auth/session'
import type { TechnicianRuntimeEnvironment } from '../environment'
import { resolveTechnicianEnvironment } from '../environment'

export function createTechnicianApi(environment: TechnicianRuntimeEnvironment) {
  return createWebApiClient({ baseUrl: environment.apiBaseUrl, accessToken,
    clientMetadata: { kind: 'TECHNICIAN_WEB', version: environment.clientVersion } })
}

const defaultApi = createTechnicianApi(resolveTechnicianEnvironment({ mode: import.meta.env.MODE,
  apiBaseUrl: import.meta.env.VITE_SERVICEOS_API_BASE_URL, clientVersion: import.meta.env.VITE_SERVICEOS_CLIENT_VERSION }))
export type HttpStatusError = Error & { status?: number; problem?: { errorCode?: string; code?: string; title?: string; detail?: string } }
export type ApiResult<T> = { data: T; etag: string | null; correlationId: string }

/** HTTP 冲突、失权和服务故障只展示固定可行动文案，后端 detail 不直接回显到现场端。 */
export function userFacingError(error: unknown, fallback: string) {
  if (error instanceof WebApiError) return safeProblemMessage(error)
  return error instanceof Error ? error.message : fallback
}

function queryPath(path: string, query: Record<string, string | undefined>) {
  const values = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => { if (value) values.set(key, value) })
  return values.size ? `${path}?${values}` : path
}
export async function apiGet<T>(path: string, query: Record<string, string | undefined> = {}, headers: Record<string, string> = {}): Promise<T> {
  return (await defaultApi.request<T>({ path: queryPath(path, query), headers })).data
}
export async function apiGetWithMeta<T>(path: string, query: Record<string, string | undefined> = {}, headers: Record<string, string> = {}): Promise<ApiResult<T>> {
  const result = await defaultApi.request<T>({ path: queryPath(path, query), headers })
  return { data: result.data, etag: result.etag, correlationId: result.diagnostics.correlationId ?? '' }
}
export async function apiPost<T>(path: string, options: { body?: unknown; idempotencyKey?: string; ifMatch?: string; headers?: Record<string, string> } = {}): Promise<ApiResult<T>> {
  const headers = { ...(options.headers ?? {}) }
  if (options.idempotencyKey) headers['Idempotency-Key'] = options.idempotencyKey
  if (options.ifMatch) headers['If-Match'] = options.ifMatch
  try {
    const result = await defaultApi.request<T>({ path, method: 'POST', body: options.body, headers })
    return { data: result.data, etag: result.etag, correlationId: result.diagnostics.correlationId ?? '' }
  } catch (error) {
    throw error
  }
}
