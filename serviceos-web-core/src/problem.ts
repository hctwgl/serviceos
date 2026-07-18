export type ProblemDetails = Readonly<{
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  errorCode?: string
  code?: string
  traceId?: string
  correlationId?: string
  [key: string]: unknown
}>

export type DiagnosticContext = Readonly<{
  correlationId: string | null
  traceId: string | null
  traceparent: string | null
}>

export class WebApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetails
  readonly diagnostics: DiagnosticContext

  constructor(status: number, problem: ProblemDetails, diagnostics: DiagnosticContext) {
    super(problem.title || '请求失败')
    this.name = 'WebApiError'
    this.status = status
    this.problem = problem
    this.diagnostics = diagnostics
  }
}

export function readDiagnosticContext(headers: Headers): DiagnosticContext {
  return Object.freeze({
    correlationId: headers.get('X-Correlation-Id'),
    traceId: headers.get('X-Trace-Id'),
    traceparent: headers.get('traceparent'),
  })
}

export async function parseProblemResponse(response: Response): Promise<WebApiError> {
  let problem: ProblemDetails = { title: response.statusText || '请求失败', status: response.status }
  try {
    const body = await response.json() as unknown
    if (body && typeof body === 'object' && !Array.isArray(body)) {
      problem = Object.freeze({ ...(body as Record<string, unknown>), status: response.status })
    }
  } catch {
    // 非 JSON 错误体不得作为 HTML/文本回显给用户，保留状态与诊断标识即可。
  }
  return new WebApiError(response.status, problem, readDiagnosticContext(response.headers))
}

/** 面向最终用户的固定安全文案；后端 detail 只用于受控诊断，不直接回显。 */
export function safeProblemMessage(error: unknown): string {
  const status = error instanceof WebApiError ? error.status : undefined
  if (status === 401) return '需要重新登录'
  if (status === 403 || status === 404) return '无权访问或不存在'
  if (status === 409 || status === 412) return '数据已变化，请刷新后重试'
  if (status === 429) return '请求过于频繁，请稍后重试'
  if (status != null && status >= 500) return '服务暂时不可用，请稍后重试'
  return '请求失败，请稍后重试'
}
