const statusLabels: Record<string, string> = {
  ACTIVE: '进行中',
  READY: '待开始',
  RUNNING: '进行中',
  COMPLETED: '已完成',
  FULFILLED: '已完成',
  CANCELLED: '已取消',
  BREACHED: '已超时',
  OPEN: '待处理',
  PENDING: '待处理',
}

export function statusLabel(code: string | null | undefined): string {
  if (!code) return '未提供'
  const label = statusLabels[code]
  if (!label) throw new Error(`缺少状态中文定义：${code}`)
  return label
}
