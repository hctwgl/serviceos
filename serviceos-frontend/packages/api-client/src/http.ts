import { accessToken, beginLogin } from '@serviceos/auth-context'

const SERVICEOS_CLIENT_KIND = 'ADMIN_WEB'
const SERVICEOS_CLIENT_VERSION = '1.0.0'

export type ProblemDetail = {
  code?: string
  detail?: string
  errorCode?: string
  title?: string
}

export class ApiProblem extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly problem: ProblemDetail,
    readonly correlationId: string,
  ) {
    super(message)
  }
}

/** 将协议错误转换为稳定的中文产品说明，技术错误码仍保留在 problem 中供诊断使用。 */
export function presentProblem(status: number, problem: ProblemDetail): string {
  if (problem.detail && /[\u3400-\u9fff]/u.test(problem.detail)) return problem.detail
  if (problem.title && /[\u3400-\u9fff]/u.test(problem.title)) return problem.title
  if (status === 403) return '当前账号无权查看或执行此操作。'
  if (status === 404) return '未找到请求的业务数据，请返回上一页后刷新。'
  if (status === 409 || status === 412) return '数据已被其他操作更新，请刷新后重新确认。'
  if (status >= 500) return '服务暂时无法完成请求，请稍后重试。'
  return '请求未能完成，请检查输入后重试。'
}

function apiBase(): string {
  const values = (import.meta as ImportMeta & { env?: Record<string, string> }).env
  return values?.VITE_API_BASE_URL ?? '/api/v1'
}

async function request<T>(
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  path: string,
  options: {
    body?: unknown
    headers?: Record<string, string>
    query?: Record<string, string | number | boolean | undefined>
  } = {},
): Promise<{ data: T; etag: string | null }> {
  const url = new URL(`${apiBase()}${path}`, window.location.origin)
  for (const [key, value] of Object.entries(options.query ?? {})) {
    if (value !== undefined && value !== '') url.searchParams.set(key, String(value))
  }
  const token = accessToken()
  const headers: Record<string, string> = {
    Accept: 'application/json',
    'X-Correlation-Id': crypto.randomUUID(),
    ...(options.headers ?? {}),
    // 客户端类型只参与能力兼容门禁与低基数诊断，不参与身份或 Scope 判定。
    // Admin 共享客户端必须显式声明，不能让后端把请求降为 UNKNOWN 后跳过能力校验。
    'X-ServiceOS-Client-Kind': SERVICEOS_CLIENT_KIND,
    'X-ServiceOS-Client-Version': SERVICEOS_CLIENT_VERSION,
  }
  if (token) headers.Authorization = `Bearer ${token}`
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'

  const response = await fetch(url, {
    method,
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })
  if (response.status === 401) {
    await beginLogin(window.location.pathname + window.location.search)
    throw new Error('登录状态已失效，正在重新登录')
  }
  if (!response.ok) {
    let problem: ProblemDetail = { title: response.statusText }
    try {
      problem = (await response.json()) as ProblemDetail
    } catch {
      // 非 JSON 响应同样属于明确失败，绝不能伪装为空数据。
    }
    throw new ApiProblem(
      presentProblem(response.status, problem),
      response.status,
      problem,
      response.headers.get('X-Correlation-Id') ?? '',
    )
  }
  if (response.status === 204) return { data: null as T, etag: response.headers.get('ETag') }
  return { data: (await response.json()) as T, etag: response.headers.get('ETag') }
}

export function get<T>(path: string, query?: Record<string, string | number | boolean | undefined>) {
  return request<T>('GET', path, { query })
}

export function post<T>(path: string, body: unknown, headers: Record<string, string> = {}) {
  return request<T>('POST', path, { body, headers })
}

export function put<T>(path: string, body: unknown, headers: Record<string, string> = {}) {
  return request<T>('PUT', path, { body, headers })
}

export function newIdempotencyKey(prefix: string): string {
  return `${prefix}-${crypto.randomUUID()}`
}
