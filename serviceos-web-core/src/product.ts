/**
 * 三门户共享的业务展示辅助：状态中文、时间格式、安全错误文案补充。
 * Admin 亦可直接依赖本模块；当前 Admin 另有同源副本以便独立构建。
 */

const STATUS_LABELS: Record<string, string> = {
  RECEIVED: '已接收',
  ACTIVE: '处理中',
  FULFILLED: '已完成',
  CANCELLED: '已取消',
  PENDING: '待处理',
  READY: '待开始',
  IN_PROGRESS: '处理中',
  COMPLETED: '已完成',
  REJECTED: '已驳回',
  ASSIGNED: '已分配',
  UNASSIGNED: '待分配',
  WAITING_REVIEW: '待审核',
  WAITING_CORRECTION: '待整改',
  RESUBMITTED: '已重新提交',
  OPEN: '待处理',
  WAIVED: '已豁免',
  CLOSED: '已关闭',
  APPROVED: '已通过',
  RUNNING: '计时中',
  BREACHED: '已超时',
  MET: '已达标',
  MET_LATE: '逾期达标',
  CONFIRMED: '已确认',
  PROPOSED: '已提出',
  FAILED: '失败',
  SUCCEEDED: '成功',
}

export function statusLabel(code: string | null | undefined): string {
  if (code == null || code === '') {
    return '—'
  }
  const normalized = code.trim().toUpperCase()
  return STATUS_LABELS[normalized] ?? `未知状态（${code}）`
}

export function formatDateTime(value: string | number | Date | null | undefined): string {
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

export const FIELD_LABELS: Record<string, string> = {
  status: '状态',
  projectId: '所属项目',
  taskId: '关联任务',
  workOrderId: '关联工单',
  createdAt: '创建时间',
  updatedAt: '更新时间',
  asOf: '统计时间',
}

export function fieldLabel(key: string): string {
  return FIELD_LABELS[key] ?? key
}
