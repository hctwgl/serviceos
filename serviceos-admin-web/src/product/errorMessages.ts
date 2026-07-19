/**
 * 将后端 Problem Details / 英文错误映射为业务可理解中文。
 * 生产环境不展示异常类名、SQL、堆栈。
 */
const MESSAGE_MAP: Array<{ match: RegExp; message: string }> = [
  { match: /work order not found/i, message: '未找到对应工单' },
  { match: /permission denied|access denied|forbidden/i, message: '您没有执行该操作的权限' },
  { match: /invalid status transition/i, message: '当前工单状态不允许执行此操作' },
  { match: /network not assigned/i, message: '工单尚未分配服务网点' },
  { match: /technician not assigned/i, message: '任务尚未指派服务师傅' },
  { match: /unauthorized|authentication/i, message: '登录状态已失效，请重新登录' },
  { match: /not found/i, message: '未找到对应资源' },
  { match: /conflict|version mismatch|if-match/i, message: '数据已被他人更新，请刷新后重试' },
  { match: /timeout|timed out/i, message: '请求超时，请稍后重试' },
]

export type UserFacingError = {
  message: string
  errorCode: string
  status?: number
  detailDev?: string
}

function newErrorCode(): string {
  const stamp = new Date().toISOString().replace(/[-:TZ.]/g, '').slice(0, 14)
  const rand = Math.random().toString(36).slice(2, 6).toUpperCase()
  return `SOS-${stamp}-${rand}`
}

export function toUserFacingError(err: unknown): UserFacingError {
  const errorCode = newErrorCode()
  if (err == null) {
    return { message: '发生未知错误', errorCode }
  }
  const anyErr = err as {
    message?: string
    status?: number
    problem?: { detail?: string; title?: string; code?: string; errorCode?: string }
  }
  const status = anyErr.status
  if (status === 401) {
    return {
      message: '登录状态已失效，请重新登录',
      errorCode,
      status,
    }
  }
  if (status === 403) {
    return {
      message: '您当前没有访问该功能的权限',
      errorCode,
      status,
    }
  }
  if (status === 404) {
    return {
      message: '页面不存在或功能已被调整',
      errorCode,
      status,
    }
  }
  const raw =
    anyErr.problem?.detail ||
    anyErr.problem?.title ||
    anyErr.message ||
    String(err)
  for (const rule of MESSAGE_MAP) {
    if (rule.match.test(raw)) {
      return {
        message: rule.message,
        errorCode,
        status,
        detailDev: import.meta.env.DEV ? raw : undefined,
      }
    }
  }
  // 过滤疑似技术堆栈
  if (/Exception|SQL|at com\.|Caused by:/i.test(raw)) {
    return {
      message: '服务暂时不可用，请稍后重试或联系管理员',
      errorCode,
      status,
      detailDev: import.meta.env.DEV ? raw : undefined,
    }
  }
  return {
    message: raw || '数据加载失败',
    errorCode,
    status,
    detailDev: import.meta.env.DEV ? raw : undefined,
  }
}
