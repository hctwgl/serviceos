/**
 * 业务时间统一显示为本地：2026-07-19 17:09:15
 * 服务端 UTC（含 Z / 带偏移）按客户端本地时区格式化。
 */
export function formatDateTime(
  value: string | number | Date | null | undefined,
): string {
  if (value == null || value === '') {
    return '—'
  }
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '无效时间'
  }
  const pad = (n: number) => String(n).padStart(2, '0')
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    ` ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  )
}

/** 相对时间，例如「3 分钟前」；无效时回退绝对时间。 */
export function formatRelativeTime(
  value: string | number | Date | null | undefined,
  now: Date = new Date(),
): string {
  if (value == null || value === '') {
    return '—'
  }
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '无效时间'
  }
  const diffMs = now.getTime() - date.getTime()
  const abs = Math.abs(diffMs)
  const future = diffMs < 0
  const minutes = Math.round(abs / 60_000)
  if (minutes < 1) {
    return future ? '即将' : '刚刚'
  }
  if (minutes < 60) {
    return future ? `${minutes} 分钟后` : `${minutes} 分钟前`
  }
  const hours = Math.round(minutes / 60)
  if (hours < 48) {
    return future ? `${hours} 小时后` : `${hours} 小时前`
  }
  const days = Math.round(hours / 24)
  return future ? `${days} 天后` : `${days} 天前`
}

export function formatRemainingSeconds(seconds: number | null | undefined): string {
  if (seconds == null || Number.isNaN(seconds)) {
    return '—'
  }
  if (seconds < 0) {
    const overdue = Math.abs(seconds)
    if (overdue < 60) {
      return `已超时 ${overdue} 秒`
    }
    if (overdue < 3600) {
      return `已超时 ${Math.round(overdue / 60)} 分钟`
    }
    return `已超时 ${Math.round(overdue / 3600)} 小时`
  }
  if (seconds < 60) {
    return `剩余 ${seconds} 秒`
  }
  if (seconds < 3600) {
    return `剩余 ${Math.round(seconds / 60)} 分钟`
  }
  return `剩余 ${Math.round(seconds / 3600)} 小时`
}
