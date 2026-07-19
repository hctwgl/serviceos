/**
 * Workflow 画布模型辅助：节点类型、即时校验（与 workflow-v1.schema 对齐的前端门禁）。
 */

export const WORKFLOW_NODE_TYPES = [
  'START',
  'END',
  'USER_TASK',
  'SERVICE_TASK',
  'REVIEW_TASK',
  'WAIT_EVENT',
  'TIMER',
  'EXCLUSIVE_GATEWAY',
  'PARALLEL_GATEWAY',
  'SUB_PROCESS',
  'MANUAL_INTERVENTION',
] as const

export type WorkflowNodeType = (typeof WORKFLOW_NODE_TYPES)[number]

export type WorkflowCanvasNode = {
  nodeId: string
  nodeType: WorkflowNodeType | string
  name?: string
  taskType?: string | null
  stageCode?: string | null
  formRef?: string | null
  evidenceRef?: string | null
  slaRef?: string | null
  assigneePolicyRef?: string | null
  integrationRef?: string | null
  waitEventType?: string | null
  correlationKeyTemplate?: string | null
  durationSeconds?: number | null
  subProcessRef?: string | null
  multiInstance?: { cardinality: number } | null
}

const TASK_LIKE = new Set(['USER_TASK', 'SERVICE_TASK', 'REVIEW_TASK', 'MANUAL_INTERVENTION'])

/** 返回节点即时校验错误；空数组表示通过。 */
export function validateWorkflowNode(node: WorkflowCanvasNode): string[] {
  const errors: string[] = []
  if (!node.nodeId?.trim()) {
    errors.push('nodeId 必填')
  } else if (!/^[A-Z0-9][A-Z0-9_-]*$/.test(node.nodeId)) {
    errors.push('nodeId 格式无效')
  }
  if (!node.name?.trim()) {
    errors.push('name 必填')
  }
  if (!WORKFLOW_NODE_TYPES.includes(node.nodeType as WorkflowNodeType)) {
    errors.push(`未知 nodeType: ${node.nodeType}`)
  }
  if (TASK_LIKE.has(node.nodeType)) {
    if (!node.stageCode?.trim()) {
      errors.push('任务节点需要 stageCode')
    }
    if (!node.taskType?.trim()) {
      errors.push('任务节点需要 taskType')
    }
  }
  if (node.nodeType === 'WAIT_EVENT' && !node.waitEventType?.trim()) {
    errors.push('WAIT_EVENT 需要 waitEventType')
  }
  if (node.nodeType === 'TIMER') {
    if (node.durationSeconds == null || node.durationSeconds < 1) {
      errors.push('TIMER 需要 durationSeconds >= 1')
    }
  }
  if (node.nodeType === 'SUB_PROCESS' && !node.subProcessRef?.trim()) {
    errors.push('SUB_PROCESS 需要 subProcessRef')
  }
  return errors
}

export function nextNodeId(existing: string[], prefix: string): string {
  const base = prefix.replace(/[^A-Z0-9_]/gi, '_').toUpperCase() || 'NODE'
  let i = 1
  let candidate = `${base}_${i}`
  const set = new Set(existing)
  while (set.has(candidate)) {
    i += 1
    candidate = `${base}_${i}`
  }
  return candidate
}

export function defaultNodeForType(nodeType: WorkflowNodeType, nodeId: string): WorkflowCanvasNode {
  const base: WorkflowCanvasNode = {
    nodeId,
    nodeType,
    name: nodeType,
  }
  if (TASK_LIKE.has(nodeType)) {
    return { ...base, stageCode: 'STAGE_A', taskType: 'DESIGNER_DEMO' }
  }
  if (nodeType === 'WAIT_EVENT') {
    return { ...base, waitEventType: 'workorder.assigned', correlationKeyTemplate: '{{workOrderId}}' }
  }
  if (nodeType === 'TIMER') {
    return { ...base, durationSeconds: 3600 }
  }
  if (nodeType === 'SUB_PROCESS') {
    return { ...base, subProcessRef: 'child.workflow' }
  }
  return base
}
