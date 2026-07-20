/**
 * 将 allowed-actions / 命令码映射为业务动词。
 * 正式按钮禁止直接显示英文命令名。
 */

const ACTION_LABELS: Record<string, string> = {
  'assign-candidates': '分配候选',
  'revise-scope-relations': '保存服务范围调整',
  CLAIM: '领取任务',
  RELEASE: '释放任务',
  START: '开始任务',
  COMPLETE: '完成任务',
  DECIDE: '提交审核决定',
  APPROVE: '审核通过',
  REJECT: '驳回整改',
  FORCE_APPROVE: '强制通过',
  REOPEN: '重开审核',
  ACKNOWLEDGE: '确认异常',
  RESUBMIT: '重新提交整改',
  WAIVE: '豁免整改',
  REPLAY: '重新投递',
  ASSIGN_NETWORK: '分配服务网点',
  ASSIGN_TECHNICIAN: '指派服务师傅',
  MANUAL_ASSIGN: '确认分配',
}

export function labelAction(
  code: string | null | undefined,
  serverLabel?: string | null,
): string {
  if (serverLabel && !/^[a-z0-9._-]+$/i.test(serverLabel) && !serverLabel.includes('-')) {
    // 服务端已给中文或可读标签
    return serverLabel
  }
  if (serverLabel && /[\u4e00-\u9fff]/.test(serverLabel)) {
    return serverLabel
  }
  if (code == null || code === '') return '执行操作'
  const normalized = code.trim()
  return ACTION_LABELS[normalized] ?? ACTION_LABELS[normalized.toUpperCase()] ?? '执行操作'
}
