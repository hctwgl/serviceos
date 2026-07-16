/** 最小 HTTP 客户端：只携带受信 Bearer，不信任客户端拼装的 tenant/scope。 */

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
  const token = localStorage.getItem('serviceos.accessToken')
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
    idempotencyKey: string
    ifMatch: string
  },
): Promise<ApiResult<T>> {
  const extra: Record<string, string> = {
    'Idempotency-Key': options.idempotencyKey,
    'If-Match': options.ifMatch,
  }
  const hasBody = options.body !== undefined
  if (hasBody) {
    extra['Content-Type'] = 'application/json'
  }
  const response = await fetch(`${apiBase()}${path}`, {
    method: 'POST',
    headers: authHeaders(extra),
    body: hasBody ? JSON.stringify(options.body) : undefined,
  })
  if (!response.ok) {
    await parseError(response)
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
