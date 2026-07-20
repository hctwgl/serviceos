/**
 * 生成 Core OpenAPI Client 工厂。页面不得手写 URL；履约等新 API 经本工厂调用。
 */
import { Configuration, DefaultApi, ResponseError } from '@serviceos/core-client'
import { requireAuthentication, usableAccessToken } from '../auth/oidc'
import type { ApiResponse } from '@serviceos/core-client'

function apiBase(): string {
  // 生成客户端 BASE_PATH 已是 /api/v1；此处只提供同源前缀（空或网关根）。
  const configured = import.meta.env.VITE_API_BASE_URL as string | undefined
  if (!configured || configured === '/api/v1') {
    return ''
  }
  return configured.replace(/\/api\/v1\/?$/, '')
}

export function createCoreApi(): DefaultApi {
  return new DefaultApi(
    new Configuration({
      basePath: apiBase() || undefined,
      accessToken: async () => {
        const token = usableAccessToken()
        if (!token) {
          requireAuthentication()
          return ''
        }
        return token
      },
      headers: {
        Accept: 'application/json',
      },
      middleware: [
        {
          pre: async (context) => {
            const headers = new Headers(context.init.headers)
            if (!headers.has('X-Correlation-Id')) {
              headers.set('X-Correlation-Id', crypto.randomUUID())
            }
            return {
              url: context.url,
              init: { ...context.init, headers },
            }
          },
          post: async (context) => {
            if (context.response.status === 401) {
              requireAuthentication()
            }
            return context.response
          },
        },
      ],
    }),
  )
}

export type ApiResult<T> = {
  data: T
  etag: string | null
  correlationId: string
}

export async function fromRaw<T>(rawPromise: Promise<ApiResponse<T>>): Promise<ApiResult<T>> {
  try {
    const response = await rawPromise
    const data = await response.value()
    return {
      data,
      etag: response.raw.headers.get('ETag'),
      correlationId: response.raw.headers.get('X-Correlation-Id') ?? '',
    }
  } catch (err) {
    const response = err instanceof ResponseError ? err.response : undefined
    if (response?.status === 401) {
      requireAuthentication()
    }
    let detail = '请求失败'
    try {
      const problem = response ? ((await response.clone().json()) as { detail?: string; title?: string }) : null
      detail = problem?.detail ?? problem?.title ?? detail
    } catch {
      /* ignore */
    }
    throw Object.assign(new Error(detail), {
      status: response?.status,
      problem: { detail },
    })
  }
}

export { newIdempotencyKey, quotedVersion } from './client'
