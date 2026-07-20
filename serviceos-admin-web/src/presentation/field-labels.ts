/**
 * 字段中文标签。正式表头/表单禁止直接展示后端字段名。
 */

export const FIELD_LABELS: Record<string, string> = {
  status: '状态',
  projectId: '所属项目',
  taskId: '关联任务',
  workOrderId: '关联工单',
  id: '业务标识',
  externalOrderCode: '工单编号',
  clientCode: '车企',
  brandCode: '品牌',
  serviceProductCode: '服务产品',
  sourceReviewCaseId: '来源审核单',
  reviewCaseId: '审核单号',
  correctionCaseId: '整改单号',
  correctionTaskId: '整改任务',
  createdAt: '创建时间',
  updatedAt: '更新时间',
  receivedAt: '接收时间',
  resubmissionCount: '重新提交次数',
  organizationId: '组织',
  networkId: '服务网点',
  technicianId: '服务师傅',
  tenantId: '租户',
  capabilityCode: '操作权限',
  asOf: '数据更新时间',
  origin: '来源',
  latestDecision: '最近审核结论',
  reasonCodes: '原因',
  severity: '严重程度',
  category: '异常类别',
  errorCode: '错误码',
  openedAt: '打开时间',
  deadlineAt: '截止时间',
  remainingSeconds: '剩余时间',
  overdueSeconds: '超时时长',
  startsOn: '生效日期',
  endsOn: '失效日期',
  regionCodes: '服务区域',
  networkIds: '合作网点',
  clientId: '所属车企',
  code: '编码',
  name: '名称',
  version: '当前版本',
  stageCode: '当前阶段',
  taskType: '任务类型',
  businessType: '业务类型',
}

export function fieldLabel(key: string): string {
  return FIELD_LABELS[key] ?? '未命名字段'
}
