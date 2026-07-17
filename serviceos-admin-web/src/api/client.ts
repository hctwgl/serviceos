/** 最小 HTTP 客户端：只携带受信 Bearer，不信任客户端拼装的 tenant/scope。 */
import { requireAuthentication, usableAccessToken } from '../auth/oidc'

export type ApiError = {
  title?: string
  detail?: string
  code?: string
}

export type ApiResult<T> = {
  data: T
  etag: string | null
  correlationId: string
}

function apiBase(): string {
  return import.meta.env.VITE_API_BASE_URL ?? '/api/v1'
}

function authHeaders(extra: Record<string, string> = {}): HeadersInit {
  const token = usableAccessToken()
  const headers: Record<string, string> = {
    Accept: 'application/json',
    'X-Correlation-Id': crypto.randomUUID(),
    ...extra,
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  return headers
}

async function parseError(response: Response): Promise<never> {
  if (response.status === 401) {
    requireAuthentication()
  }
  let problem: ApiError = { title: response.statusText }
  try {
    problem = (await response.json()) as ApiError
  } catch {
    /* ignore */
  }
  throw Object.assign(new Error(problem.detail ?? problem.title ?? '请求失败'), {
    status: response.status,
    problem,
  })
}

export async function apiGet<T>(path: string, query: Record<string, string | undefined> = {}): Promise<T> {
  const result = await apiGetWithMeta<T>(path, query)
  return result.data
}

export async function apiGetWithMeta<T>(
  path: string,
  query: Record<string, string | undefined> = {},
): Promise<ApiResult<T>> {
  const url = new URL(`${apiBase()}${path}`, window.location.origin)
  for (const [key, value] of Object.entries(query)) {
    if (value != null && value !== '') {
      url.searchParams.set(key, value)
    }
  }
  const headers = authHeaders()
  const response = await fetch(url, { headers })
  if (!response.ok) {
    await parseError(response)
  }
  return {
    data: (await response.json()) as T,
    etag: response.headers.get('ETag'),
    correlationId: response.headers.get('X-Correlation-Id') ?? '',
  }
}

export async function apiPost<T>(
  path: string,
  options: {
    body?: unknown
    idempotencyKey?: string
    ifMatch?: string
  } = {},
): Promise<ApiResult<T>> {
  return apiWrite<T>('POST', path, options)
}

export async function apiPut<T>(
  path: string,
  options: {
    body?: unknown
    ifMatch?: string
  } = {},
): Promise<ApiResult<T>> {
  return apiWrite<T>('PUT', path, options)
}

export async function apiDelete(
  path: string,
): Promise<ApiResult<null>> {
  return apiWrite<null>('DELETE', path, {})
}

async function apiWrite<T>(
  method: 'POST' | 'PUT' | 'DELETE',
  path: string,
  options: {
    body?: unknown
    idempotencyKey?: string
    ifMatch?: string
  },
): Promise<ApiResult<T>> {
  const extra: Record<string, string> = {}
  if (options.idempotencyKey) {
    extra['Idempotency-Key'] = options.idempotencyKey
  }
  if (options.ifMatch) {
    extra['If-Match'] = options.ifMatch
  }
  const hasBody = options.body !== undefined
  if (hasBody) {
    extra['Content-Type'] = 'application/json'
  }
  const response = await fetch(`${apiBase()}${path}`, {
    method,
    headers: authHeaders(extra),
    body: hasBody ? JSON.stringify(options.body) : undefined,
  })
  if (!response.ok) {
    await parseError(response)
  }
  if (response.status === 204) {
    return {
      data: null as T,
      etag: response.headers.get('ETag'),
      correlationId: response.headers.get('X-Correlation-Id') ?? '',
    }
  }
  const text = await response.text()
  return {
    data: (text ? JSON.parse(text) : null) as T,
    etag: response.headers.get('ETag'),
    correlationId: response.headers.get('X-Correlation-Id') ?? '',
  }
}

export function quotedVersion(version: number): string {
  return `"${version}"`
}

export function newIdempotencyKey(prefix: string): string {
  return `${prefix}-${crypto.randomUUID()}`
}

export type HttpStatusError = Error & {
  status?: number
  problem?: ApiError
}

/** 探测目录可见性：403/404 表示无能力或不可见，不触发登录跳转以外的副作用。 */
export async function apiProbe(path: string): Promise<'allow' | 'deny' | 'unauthenticated'> {
  const url = new URL(`${apiBase()}${path}`, window.location.origin)
  const response = await fetch(url, { headers: authHeaders() })
  if (response.status === 401) {
    return 'unauthenticated'
  }
  if (response.ok) {
    return 'allow'
  }
  if (response.status === 403 || response.status === 404) {
    return 'deny'
  }
  return 'deny'
}

/** 治理深链失败关闭文案：不回显后端 detail，避免泄露姓名/部门/角色。 */
export function safeAccessDeniedMessage(err: unknown): string {
  const status = (err as HttpStatusError | undefined)?.status
  if (status === 403 || status === 404) {
    return '无权访问或不存在'
  }
  if (status === 409) {
    return '版本冲突，请刷新后重试'
  }
  if (status === 401) {
    return '需要重新登录'
  }
  return '请求失败，请稍后重试'
}

export function isConflictError(err: unknown): boolean {
  return (err as HttpStatusError | undefined)?.status === 409
}
