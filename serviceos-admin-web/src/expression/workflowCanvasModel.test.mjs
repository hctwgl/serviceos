import assert from 'node:assert/strict'

const WORKFLOW_NODE_TYPES = [
  'START', 'END', 'USER_TASK', 'SERVICE_TASK', 'REVIEW_TASK', 'WAIT_EVENT', 'TIMER',
  'EXCLUSIVE_GATEWAY', 'PARALLEL_GATEWAY', 'SUB_PROCESS', 'MANUAL_INTERVENTION',
]
const TASK_LIKE = new Set(['USER_TASK', 'SERVICE_TASK', 'REVIEW_TASK', 'MANUAL_INTERVENTION'])

function validateWorkflowNode(node) {
  const errors = []
  if (!node.nodeId?.trim()) errors.push('nodeId 必填')
  else if (!/^[A-Z0-9][A-Z0-9_-]*$/.test(node.nodeId)) errors.push('nodeId 格式无效')
  if (!node.name?.trim()) errors.push('name 必填')
  if (!WORKFLOW_NODE_TYPES.includes(node.nodeType)) errors.push(`未知 nodeType: ${node.nodeType}`)
  if (TASK_LIKE.has(node.nodeType)) {
    if (!node.stageCode?.trim()) errors.push('任务节点需要 stageCode')
    if (!node.taskType?.trim()) errors.push('任务节点需要 taskType')
  }
  if (node.nodeType === 'WAIT_EVENT' && !node.waitEventType?.trim()) {
    errors.push('WAIT_EVENT 需要 waitEventType')
  }
  if (node.nodeType === 'TIMER' && (node.durationSeconds == null || node.durationSeconds < 1)) {
    errors.push('TIMER 需要 durationSeconds >= 1')
  }
  if (node.nodeType === 'SUB_PROCESS' && !node.subProcessRef?.trim()) {
    errors.push('SUB_PROCESS 需要 subProcessRef')
  }
  return errors
}

function nextNodeId(existing, prefix) {
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

assert.deepEqual(
  validateWorkflowNode({ nodeId: 'TASK_A', nodeType: 'SERVICE_TASK', name: 'A' }),
  ['任务节点需要 stageCode', '任务节点需要 taskType'],
)
assert.deepEqual(
  validateWorkflowNode({
    nodeId: 'TASK_A',
    nodeType: 'SERVICE_TASK',
    name: 'A',
    stageCode: 'S1',
    taskType: 'T1',
  }),
  [],
)
assert.ok(validateWorkflowNode({ nodeId: 'W1', nodeType: 'WAIT_EVENT', name: 'w' }).includes(
  'WAIT_EVENT 需要 waitEventType',
))
assert.equal(nextNodeId(['SERVICE_TASK_1'], 'SERVICE_TASK'), 'SERVICE_TASK_2')
console.log('workflowCanvasModel.test.mjs OK')
