const stageLabels: Record<string, string> = {
  PILOT_INTAKE: '接单受理',
  PILOT_DISPATCH: '网点分配',
  PILOT_APPOINTMENT: '预约确认',
  PILOT_SURVEY: '上门勘测',
  PILOT_INSTALLATION: '上门安装',
  FINAL_REVIEW: '资料审核',
  CLIENT_CALLBACK: '车企回传',
}

const taskLabels: Record<string, string> = {
  ASSIGN_COORDINATORS: '分配责任网点',
  FIELD_SURVEY: '上门勘测',
  FIELD_INSTALL: '上门安装',
  REVIEW_TASK: '资料审核',
  PILOT_SURVEY: '上门勘测',
  PILOT_INSTALLATION: '上门安装',
  NETWORK_ASSIGNMENT: '网点分配',
  TECHNICIAN_ASSIGNMENT: '师傅分配',
}

const serviceLabels: Record<string, string> = {
  HOME_CHARGER_INSTALLATION: '充电桩安装服务',
  HOME_CHARGER_SURVEY: '家充勘测服务',
  CHARGER_REPAIR: '充电桩维修服务',
}

function requireLabel(labels: Record<string, string>, code: string | null, field: string): string {
  if (!code) return '尚未进入'
  const label = labels[code]
  if (!label) throw new Error(`页面数据不完整：${field} 缺少中文名称（${code}）`)
  return label
}

export const stageLabel = (code: string | null) => requireLabel(stageLabels, code, '当前阶段')
export const taskLabel = (code: string | null) => requireLabel(taskLabels, code, '当前任务')
export const serviceLabel = (code: string) => requireLabel(serviceLabels, code, '服务产品')

export function clientLabel(code: string): string {
  const labels: Record<string, string> = { BYD: '比亚迪', GEELY: '吉利汽车', NIO: '蔚来汽车' }
  return requireLabel(labels, code, '客户品牌')
}

const workOrderStatusLabels: Record<string, string> = {
  RECEIVED: '待受理',
  ACTIVE: '进行中',
  FULFILLED: '已完成',
  CANCELLED: '已取消',
}

export const workOrderStatusLabel = (code: string) =>
  requireLabel(workOrderStatusLabels, code, '工单状态')

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value))
}
