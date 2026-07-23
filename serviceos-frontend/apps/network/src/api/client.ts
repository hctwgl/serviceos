import { accessToken, beginLogin } from '@serviceos/auth-context'

// 师傅端本地 API 客户端：复用 @serviceos/auth-context 的 OIDC 访问令牌，经 Vite /api 代理访问后端。
// 不依赖已废弃的 @serviceos/web-core；保持与旧端相同的 apiGet/apiGetWithMeta/apiPost 契约，
// 使移植的 technicianPortal/me/pages 无需改动。
const API_BASE =
  (import.meta as ImportMeta & { env?: Record<string, string> }).env?.VITE_API_BASE_URL ?? '/api/v1'

export type ProblemBody = {
  errorCode?: string
  code?: string
  title?: string
  detail?: string
}

export type HttpStatusError = Error & { status?: number; problem?: ProblemBody }

/** 后端 Problem 响应统一封装；status/problem 供 userFacingError 与诊断使用。 */
export class WebApiError extends Error {
  readonly status: number
  readonly problem: ProblemBody
  readonly correlationId: string

  constructor(status: number, problem: ProblemBody, correlationId: string) {
    super(problem.detail || problem.title || `HTTP ${status}`)
    this.name = 'WebApiError'
    this.status = status
    this.problem = problem
    this.correlationId = correlationId
  }
}

export type ApiResult<T> = { data: T; etag: string | null; correlationId: string }

const FIXED_MESSAGES: Record<number, string> = {
  403: '当前账号无权查看或执行此操作。',
  404: '未找到请求的业务数据，请返回上一页后刷新。',
  409: '数据已被其他操作更新，请刷新后重新确认。',
  412: '数据已被其他操作更新，请刷新后重新确认。',
}

/** HTTP 冲突、失权和服务故障只展示固定可行动文案；服务端中文 detail/title 视为权威说明。 */
export function safeProblemMessage(error: unknown): string {
  if (error instanceof WebApiError) {
    const { problem, status } = error
    if (problem.detail && /[\u3400-\u9fff]/u.test(problem.detail)) return problem.detail
    if (problem.title && /[\u3400-\u9fff]/u.test(problem.title)) return problem.title
    if (FIXED_MESSAGES[status]) return FIXED_MESSAGES[status] as string
    if (status >= 500) return '服务暂时无法完成请求，请稍后重试。'
    return '请求未能完成，请检查输入后重试。'
  }
  return error instanceof Error ? error.message : '发生未知错误'
}

/** CLIENT_CAPABILITY_UNSUPPORTED 的服务端中文 detail 是权威能力说明，允许原样展示。 */
export function userFacingError(error: unknown, fallback: string): string {
  if (error instanceof WebApiError) {
    const code = String(error.problem.errorCode ?? error.problem.code ?? '')
    const detail = error.problem.detail
    if (code === 'CLIENT_CAPABILITY_UNSUPPORTED' && typeof detail === 'string' && detail.trim()) {
      return detail.trim()
    }
    return safeProblemMessage(error)
  }
  return error instanceof Error ? error.message : fallback
}

type RequestOptions = { body?: unknown; headers?: Record<string, string> }

async function request<T>(
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  path: string,
  options: RequestOptions = {},
): Promise<ApiResult<T>> {
  const token = accessToken()
  const headers: Record<string, string> = {
    Accept: 'application/json',
    'X-Correlation-Id': crypto.randomUUID(),
    ...(options.headers ?? {}),
  }
  if (token) headers.Authorization = `Bearer ${token}`
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'

  const response = await fetch(`${API_BASE}${path}`, {
    method,
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })
  const correlationId = response.headers.get('X-Correlation-Id') ?? ''
  if (response.status === 401) {
    await beginLogin(window.location.pathname + window.location.search)
    throw new WebApiError(401, { title: '登录状态已失效，正在重新登录' }, correlationId)
  }
  if (!response.ok) {
    let problem: ProblemBody = { title: response.statusText }
    try {
      problem = (await response.json()) as ProblemBody
    } catch {
      // 非 JSON 响应同样是明确失败，绝不伪装为空数据。
    }
    throw new WebApiError(response.status, problem, correlationId)
  }
  const etag = response.headers.get('ETag')
  if (response.status === 204) return { data: null as T, etag, correlationId }
  return { data: (await response.json()) as T, etag, correlationId }
}

function queryPath(path: string, query: Record<string, string | undefined>): string {
  const values = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value) values.set(key, value)
  }
  return values.size ? `${path}?${values.toString()}` : path
}

export async function apiGet<T>(
  path: string,
  query: Record<string, string | undefined> = {},
  headers: Record<string, string> = {},
): Promise<T> {
  return (await request<T>('GET', queryPath(path, query), { headers })).data
}

export async function apiGetWithMeta<T>(
  path: string,
  query: Record<string, string | undefined> = {},
  headers: Record<string, string> = {},
): Promise<ApiResult<T>> {
  return request<T>('GET', queryPath(path, query), { headers })
}

export async function apiPost<T>(
  path: string,
  options: { body?: unknown; idempotencyKey?: string; ifMatch?: string; headers?: Record<string, string> } = {},
): Promise<ApiResult<T>> {
  const headers = { ...(options.headers ?? {}) }
  if (options.idempotencyKey) headers['Idempotency-Key'] = options.idempotencyKey
  if (options.ifMatch) headers['If-Match'] = options.ifMatch
  return request<T>('POST', path, { body: options.body, headers })
}
