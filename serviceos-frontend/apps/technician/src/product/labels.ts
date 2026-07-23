// 师傅端业务展示辅助：状态中文、时间格式、字段名。自包含，无跨端共享依赖。
// 未知状态不失败关闭，原样以“未知状态（code）”展示，避免移动端因新枚举崩溃。

const STATUS_LABELS: Record<string, string> = {
  // 工单
  RECEIVED: '已接收',
  ACTIVE: '处理中',
  FULFILLED: '已完成',
  CANCELLED: '已取消',
  SUSPENDED: '已挂起',
  CLOSED: '已关闭',
  DISABLED: '已停用',
  DEACTIVATED: '已停用',
  TERMINATED: '已终止',
  EXPIRED: '已过期',
  // 通用任务/整改/审核
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
  CLOSED_OK: '已关闭',
  APPROVED: '已通过',
  FORCE_APPROVED: '强制通过',
  REOPENED: '已重开',
  CLAIMED: '已认领',
  RETRY_WAIT: '等待重试',
  MANUAL_INTERVENTION: '需人工介入',
  ACKNOWLEDGED: '已确认',
  RESOLVED: '已解决',
  RUNNING: '计时中',
  BREACHED: '已超时',
  MET: '已达标',
  MET_LATE: '逾期达标',
  PREPARING: '准备中',
  CONFIRMED: '已确认',
  CHECKED_IN: '已签到',
  CHECKED_OUT: '已签退',
  INTERRUPTED: '已中断',
  NO_SHOW: '未履约',
  PROPOSED: '已提出',
  UNKNOWN: '未知',
  MAPPED: '已映射',
  FAILED: '失败',
  SUCCEEDED: '成功',
  DELIVERED: '已送达',
  RETRYING: '重试中',
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  PENDING_APPROVAL: '待审批',
  // 任务类型 / 阶段 / 业务类型
  PILOT_SURVEY: '试点勘测',
  PILOT_COMPLETION: '试点完工',
  PILOT_INSTALL: '试点安装',
  FIELD_SURVEY: '上门勘测',
  FIELD_INSTALL: '上门安装',
  ASSIGN_COORDINATORS: '分配责任网点',
  SURVEY: '勘测',
  INSTALLATION: '安装',
  REPAIR: '维修',
  CORRECTION: '整改',
  SECOND_VISIT: '二次上门',
  HOME_CHARGING_SURVEY_INSTALL: '家充勘测安装',
  HUMAN: '人工任务',
  SYSTEM: '系统任务',
  MACHINE: '机器任务',
  // 资料槽位 / 文件
  MISSING: '缺失',
  MISSING_PHOTO: '缺少照片',
  MISSING_DOCUMENT: '缺少文档',
  PRESENT: '已上传',
  SATISFIED: '已满足',
  REQUIRED: '必需',
  OPTIONAL: '可选',
  UPLOADED: '已上传',
  FINALIZED: '已定稿',
  SCANNING: '扫描中',
  CLEAN: '已通过扫描',
  INFECTED: '扫描未通过',
  STORED: '已存储',
  VALIDATING: '校验中',
  VALIDATED: '已校验',
  VALIDATION_FAILED: '校验失败',
  QUARANTINED: '已隔离',
  INVALIDATED: '已作废',
  PHOTO: '照片',
  SIGNATURE: '签名',
  GENERATED_REPORT: '生成报告',
  WITHIN_GEOFENCE: '围栏内',
  OUTSIDE_GEOFENCE: '围栏外',
  LOCATION_UNAVAILABLE: '定位不可用',
  ACCEPTED: '已接受',
  WARNING: '警告',
  // 成员角色
  STAFF: '网点员工',
  MANAGER: '网点经理',
  PRIMARY: '主要成员',
  USER: '用户',
  // 联系结果
  PHONE: '电话',
  SMS: '短信',
  CONNECTED: '已接通',
  NO_ANSWER: '未接听',
  BUSY: '忙线',
  WRONG_NUMBER: '空号/错号',
  USER_REQUESTED_LATER: '客户要求稍后联系',
  INVALID_CONTACT: '联系方式无效',
  // 其他常见码
  IMAGE: '图片',
  VIDEO: '视频',
  PDF: 'PDF',
  DOCUMENT: '文档',
}

/** 师傅端本地补充：Portal persona 等不放进跨端共享词表。 */
const LOCAL_LABELS: Record<string, string> = {
  INTERNAL_EMPLOYEE: '内部员工',
  NETWORK_MEMBER: '网点成员',
  TECHNICIAN: '服务师傅',
  CONSUMER: '客户',
  SERVICE_ACCOUNT: '服务账号',
  ADMIN: '管理端',
  NETWORK: '网点端',
}

export function statusLabel(code: string | null | undefined): string {
  if (code == null || code === '') return '—'
  const normalized = code.trim().toUpperCase()
  return LOCAL_LABELS[normalized] ?? STATUS_LABELS[normalized] ?? `未知状态（${code}）`
}

export function formatDateTime(value: string | number | Date | null | undefined): string {
  if (value == null || value === '') return '—'
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return '无效时间'
  const pad = (n: number) => String(n).padStart(2, '0')
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    ` ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  )
}

const FIELD_LABELS: Record<string, string> = {
  status: '状态',
  projectId: '所属项目',
  taskId: '关联任务',
  workOrderId: '关联工单',
  createdAt: '创建时间',
  updatedAt: '更新时间',
  asOf: '统计时间',
  taskType: '任务类型',
  taskKind: '任务种类',
  stageCode: '当前阶段',
  businessType: '业务类型',
  networkId: '服务网点',
  technicianId: '服务师傅',
}

export function fieldLabel(key: string): string {
  return FIELD_LABELS[key] ?? key
}
