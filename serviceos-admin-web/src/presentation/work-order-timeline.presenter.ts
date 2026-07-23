/**
 * 工单时间线事件面向运营人员的中文表达。
 *
 * <p>事件类型是稳定的机器契约，不能直接显示在正式产品界面。未知事件明确标记为
 * “业务动态暂不可识别”，原始事件码仅保留在技术诊断中，避免把契约缺口伪装成正常状态。</p>
 */
const TIMELINE_EVENT_LABELS: Record<string, string> = {
  'workorder.received': '工单已接收',
  'workorder.activated': '工单已进入履约',
  'workorder.fulfilled': '工单已完成',
  'workorder.cancelled': '工单已取消',
  'task.created': '已创建履约任务',
  'task.claimed': '任务已领取',
  'task.started': '任务已开始',
  'task.completed': '任务已完成',
  'task.released': '任务已释放',
  'sla.started': '已开始时效计时',
  'sla.breached': '履约时效已超时',
  'sla.met': '履约时效已达标',
  'service.assignment.activation-completed': '服务责任已生效',
  'service.assignment.reassignment-completed': '服务责任已调整',
  'appointment.confirmed': '预约已确认',
  'visit.checked-in': '师傅已到场',
  'visit.checked-out': '现场服务已结束',
  'review.approved': '资料审核已通过',
  'review.rejected': '资料审核已驳回',
  'correction.created': '已发起整改',
  'correction.resubmitted': '整改资料已重新提交',
  'outbound.delivered': '工单结果已回传',
}

export type WorkOrderTimelineEventPresentation = {
  label: string
  recognized: boolean
}

export function presentWorkOrderTimelineEvent(
  eventType: string | null | undefined,
): WorkOrderTimelineEventPresentation {
  const normalized = eventType?.trim().toLowerCase()
  if (!normalized) {
    return { label: '业务动态信息不完整', recognized: false }
  }
  const label = TIMELINE_EVENT_LABELS[normalized]
  return label
    ? { label, recognized: true }
    : { label: '业务动态暂不可识别', recognized: false }
}
