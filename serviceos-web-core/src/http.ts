import type { AccessTokenProvider } from './auth.js'
import { parseProblemResponse, readDiagnosticContext, type DiagnosticContext } from './problem.js'

export type WebApiResult<T> = Readonly<{
  data: T
  etag: string | null
  diagnostics: DiagnosticContext
}>

export type WebApiRequest = Readonly<{
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  path: string
  body?: unknown
  headers?: Readonly<Record<string, string>>
}>

export type WebApiClient = Readonly<{
  request<T>(request: WebApiRequest): Promise<WebApiResult<T>>
}>

export type WebApiClientOptions = Readonly<{
  baseUrl: string
  accessToken: AccessTokenProvider
  fetch?: typeof globalThis.fetch
  createCorrelationId?: () => string
  onAuthenticationRequired?: () => void
}>

export function createWebApiClient(options: WebApiClientOptions): WebApiClient {
  const executeFetch = options.fetch ?? globalThis.fetch
  const createCorrelationId = options.createCorrelationId ?? (() => globalThis.crypto.randomUUID())
  const baseUrl = options.baseUrl.replace(/\/$/, '')

  if (!baseUrl) throw new Error('Web API baseUrl 不得为空')

  return {
    async request<T>(request: WebApiRequest): Promise<WebApiResult<T>> {
      if (!request.path.startsWith('/')) throw new Error('Web API path 必须以 / 开头')
      const token = await options.accessToken()
      const headers = new Headers(request.headers)
      headers.set('Accept', 'application/json')
      headers.set('X-Correlation-Id', createCorrelationId())
      if (token) headers.set('Authorization', `Bearer ${token.accessToken}`)

      const hasBody = request.body !== undefined
      if (hasBody && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
      const response = await executeFetch(`${baseUrl}${request.path}`, {
        method: request.method ?? 'GET',
        headers,
        body: hasBody ? JSON.stringify(request.body) : undefined,
      })

      if (!response.ok) {
        if (response.status === 401) options.onAuthenticationRequired?.()
        throw await parseProblemResponse(response)
      }

      const text = response.status === 204 ? '' : await response.text()
      return Object.freeze({
        data: (text ? JSON.parse(text) : null) as T,
        etag: response.headers.get('ETag'),
        diagnostics: readDiagnosticContext(response.headers),
      })
    },
  }
}
