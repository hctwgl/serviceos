/** 最小 HTTP 客户端：只携带受信 Bearer，不信任客户端拼装的 tenant/scope。 */

export type ApiError = {
  title?: string
  detail?: string
  code?: string
}

function apiBase(): string {
  return import.meta.env.VITE_API_BASE_URL ?? '/api/v1'
}

function authHeaders(): HeadersInit {
  const token = localStorage.getItem('serviceos.accessToken')
  const headers: Record<string, string> = {
    Accept: 'application/json',
    'X-Correlation-Id': crypto.randomUUID(),
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  return headers
}

export async function apiGet<T>(path: string, query: Record<string, string | undefined> = {}): Promise<T> {
  const url = new URL(`${apiBase()}${path}`, window.location.origin)
  for (const [key, value] of Object.entries(query)) {
    if (value != null && value !== '') {
      url.searchParams.set(key, value)
    }
  }
  const response = await fetch(url, { headers: authHeaders() })
  if (!response.ok) {
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
  return (await response.json()) as T
}
