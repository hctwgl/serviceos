import { createWebApiClient } from '@serviceos/web-core'
import { accessToken } from '../auth/session'
import type { NetworkRuntimeEnvironment } from '../environment'
import { resolveNetworkEnvironment } from '../environment'

export function createNetworkApi(environment: NetworkRuntimeEnvironment) {
  return createWebApiClient({ baseUrl: environment.apiBaseUrl, accessToken,
    clientMetadata: { kind: 'NETWORK_WEB', version: environment.clientVersion } })
}

const defaultApi = createNetworkApi(resolveNetworkEnvironment({ mode: import.meta.env.MODE,
  apiBaseUrl: import.meta.env.VITE_SERVICEOS_API_BASE_URL, clientVersion: import.meta.env.VITE_SERVICEOS_CLIENT_VERSION }))
export type HttpStatusError = Error & { status?: number; problem?: { errorCode?: string; code?: string; title?: string; detail?: string } }
export type ApiResult<T> = { data: T; etag: string | null; correlationId: string }

function queryPath(path: string, query: Record<string, string | undefined>) {
  const values = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => { if (value) values.set(key, value) })
  return values.size ? `${path}?${values}` : path
}
export async function apiGet<T>(path: string, query: Record<string, string | undefined> = {}, headers: Record<string, string> = {}): Promise<T> {
  return (await defaultApi.request<T>({ path: queryPath(path, query), headers })).data
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
