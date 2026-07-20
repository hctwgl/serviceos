/**
 * 日期时间展示：存储语义不由前端修改；界面按本地/业务时区格式化。
 * 禁止在正式正文展示 ISO 纳秒字符串。
 */

export type DateTimePresentation = {
  absolute: string
  relative: string
  tooltip: string
  timeZoneNote: string
}

function pad(n: number): string {
  return String(n).padStart(2, '0')
}

function formatAbsolute(date: Date): string {
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    ` ${pad(date.getHours())}:${pad(date.getMinutes())}`
  )
}

function formatAbsoluteWithSeconds(date: Date): string {
  return `${formatAbsolute(date)}:${pad(date.getSeconds())}`
}

function timeZoneOffsetLabel(date: Date): string {
  const offsetMin = -date.getTimezoneOffset()
  const sign = offsetMin >= 0 ? '+' : '-'
  const abs = Math.abs(offsetMin)
  const h = Math.floor(abs / 60)
  const m = abs % 60
  return `UTC${sign}${h}${m ? `:${pad(m)}` : ''}`
}

function relativeLabel(date: Date, now: Date): string {
  const diffMs = now.getTime() - date.getTime()
  const abs = Math.abs(diffMs)
  const future = diffMs < 0
  const minutes = Math.round(abs / 60_000)
  if (minutes < 1) return future ? '即将' : '刚刚'
  if (minutes < 60) return future ? `${minutes} 分钟后` : `${minutes} 分钟前`
  const hours = Math.round(minutes / 60)
  if (hours < 48) return future ? `${hours} 小时后` : `${hours} 小时前`
  const days = Math.round(hours / 24)
  return future ? `${days} 天后` : `${days} 天前`
}

export function presentDateTime(
  value: string | number | Date | null | undefined,
  now: Date = new Date(),
): DateTimePresentation | null {
  if (value == null || value === '') return null
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) {
    return {
      absolute: '无效时间',
      relative: '无效时间',
      tooltip: '无法解析时间值',
      timeZoneNote: '',
    }
  }
  const absolute = formatAbsolute(date)
  const tz = timeZoneOffsetLabel(date)
  const relative = relativeLabel(date, now)
  return {
    absolute,
    relative,
    tooltip: `${formatAbsoluteWithSeconds(date)}，${tz}`,
    timeZoneNote: tz,
  }
}

export function formatDateTimeDisplay(
  value: string | number | Date | null | undefined,
): string {
  const presented = presentDateTime(value)
  if (!presented) return '未提供'
  return presented.absolute
}

export function formatRelativeDisplay(
  value: string | number | Date | null | undefined,
  now: Date = new Date(),
): string {
  const presented = presentDateTime(value, now)
  if (!presented) return '未提供'
  return presented.relative
}
