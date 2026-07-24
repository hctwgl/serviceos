<script setup lang="ts">
import type {
  ProjectFulfillmentDocument,
  ProjectFulfillmentNodeDraft,
  ProjectFulfillmentNodeType,
  ProjectFulfillmentPhaseDraft,
  ProjectFulfillmentTransitionDraft,
  ProjectFulfillmentValidationIssue,
} from '@serviceos/api-client'

import {
  Button,
  Checkbox,
  Input,
  Select,
  Tag,
} from '@serviceos/design-system'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import {
  MarkerType,
  VueFlow,
  useVueFlow,
} from '@vue-flow/core'
import type {
  Connection,
  Edge,
  Node,
  NodeDragEvent,
  NodeMouseEvent,
} from '@vue-flow/core'
import { MiniMap } from '@vue-flow/minimap'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import WorkflowBusinessNode from './WorkflowBusinessNode.vue'
import WorkflowPhaseLane from './WorkflowPhaseLane.vue'

const props = defineProps<{
  document: ProjectFulfillmentDocument
  issues?: ProjectFulfillmentValidationIssue[]
  readonly?: boolean
}>()
const emit = defineEmits<{
  'update:document': [value: ProjectFulfillmentDocument]
}>()

type PaletteItem = {
  category: '基础节点' | '公共业务节点' | '执行节点' | '控制节点'
  label: string
  nodeType: ProjectFulfillmentNodeType
  templateKey?: string
}

const palette: PaletteItem[] = [
  { category: '基础节点', label: '开始', nodeType: 'START' },
  { category: '基础节点', label: '条件判断', nodeType: 'CONDITION' },
  { category: '基础节点', label: '结束', nodeType: 'END' },
  { category: '执行节点', label: '人工作业', nodeType: 'HUMAN_TASK' },
  { category: '执行节点', label: '审核', nodeType: 'REVIEW' },
  { category: '执行节点', label: '系统动作', nodeType: 'SYSTEM_ACTION' },
  { category: '控制节点', label: '事件等待', nodeType: 'EVENT_WAIT' },
  ...[
    ['联系客户', 'HUMAN_TASK'],
    ['预约上门', 'HUMAN_TASK'],
    ['现场勘测', 'HUMAN_TASK'],
    ['安装施工', 'HUMAN_TASK'],
    ['资料提交', 'HUMAN_TASK'],
    ['客户确认', 'HUMAN_TASK'],
    ['勘测审核', 'REVIEW'],
    ['安装资料审核', 'REVIEW'],
    ['验收审核', 'REVIEW'],
    ['发送客户通知', 'SYSTEM_ACTION'],
    ['调用车企接口', 'SYSTEM_ACTION'],
    ['生成资料包', 'SYSTEM_ACTION'],
    ['自动派单', 'SYSTEM_ACTION'],
    ['更新外部状态', 'SYSTEM_ACTION'],
    ['等待客户确认', 'EVENT_WAIT'],
    ['等待外部审批', 'EVENT_WAIT'],
    ['等待接口回调', 'EVENT_WAIT'],
    ['等待约定时间', 'EVENT_WAIT'],
  ].map(([label, nodeType]) => ({
    category: '公共业务节点' as const,
    label: String(label),
    nodeType: nodeType as ProjectFulfillmentNodeType,
    templateKey: String(label),
  })),
]

const search = ref('')
const selectedNodeId = ref<string>()
const selectedTransitionId = ref<string>()
const selectedPhaseId = ref<string>()
const activeInspector = ref('base')
const past = ref<ProjectFulfillmentDocument[]>([])
const future = ref<ProjectFulfillmentDocument[]>([])
const { fitView, screenToFlowCoordinate } = useVueFlow()

const normalizedDocument = computed<ProjectFulfillmentDocument>(() => ({
  ...props.document,
  nodes: props.document.nodes ?? [],
  phases: props.document.phases ?? [],
  transitions: props.document.transitions ?? [],
}))
const orderedPhases = computed(() => (
  [...normalizedDocument.value.phases].sort((a, b) => a.sequence - b.sequence)
))
const filteredPalette = computed(() => palette.filter((item) => (
  !search.value || item.label.includes(search.value)
)))
const selectedNode = computed(() => normalizedDocument.value.nodes.find(
  (node) => node.nodeId === selectedNodeId.value,
))
const selectedTransition = computed(() => normalizedDocument.value.transitions.find(
  (transition) => transition.transitionId === selectedTransitionId.value,
))
const selectedPhase = computed(() => normalizedDocument.value.phases.find(
  (phase) => phase.phaseId === selectedPhaseId.value,
))

const phaseRanges = computed(() => orderedPhases.value.map((phase, index) => {
  const phaseNodes = normalizedDocument.value.nodes.filter((node) => node.phaseId === phase.phaseId)
  const fallbackY = 150 + index * 320
  const minimum = phaseNodes.length
    ? Math.min(...phaseNodes.map((node) => node.positionY)) - 70
    : fallbackY
  const maximum = phaseNodes.length
    ? Math.max(...phaseNodes.map((node) => node.positionY)) + 130
    : fallbackY + 220
  return {
    phase,
    x: 30,
    y: minimum,
    width: 910,
    height: Math.max(220, maximum - minimum),
  }
}))

const flowNodes = computed<Node[]>(() => [
  ...phaseRanges.value.map(({ phase, x, y, width, height }) => ({
    id: `PHASE__${phase.phaseId}`,
    type: 'phase',
    position: { x, y },
    data: {
      color: phase.displayColor,
      name: phase.phaseName,
      nodeCount: normalizedDocument.value.nodes.filter((node) => node.phaseId === phase.phaseId).length,
    },
    selectable: false,
    draggable: false,
    connectable: false,
    focusable: false,
    style: { width: `${width}px`, height: `${height}px`, zIndex: -2 },
  })),
  ...normalizedDocument.value.nodes.map((node) => ({
    id: node.nodeId,
    type: 'business',
    position: { x: node.positionX, y: node.positionY },
    data: node,
    selected: node.nodeId === selectedNodeId.value,
    draggable: !props.readonly,
    connectable: !props.readonly,
    style: { zIndex: 2 },
  })),
])
const flowEdges = computed<Edge[]>(() => normalizedDocument.value.transitions.map((transition) => ({
  id: transition.transitionId,
  source: transition.fromNodeId,
  target: transition.toNodeId,
  label: transition.branchName || transition.resultCode || undefined,
  type: 'smoothstep',
  animated: transition.fromNodeId === selectedNodeId.value,
  markerEnd: MarkerType.ArrowClosed,
  style: {
    stroke: transition.resultCode === 'REJECT' || transition.resultCode === 'FAILED'
      ? 'hsl(var(--destructive))'
      : transition.resultCode === 'PASS' || transition.resultCode === 'SUCCESS'
        ? 'hsl(var(--success))'
        : 'hsl(var(--muted-foreground))',
    strokeWidth: transition.transitionId === selectedTransitionId.value ? 2.4 : 1.5,
  },
})))

const nodeIssues = computed(() => (props.issues ?? []).filter(
  (issue) => issue.nodeId === selectedNodeId.value,
))
const completeness = computed(() => {
  const businessNodes = normalizedDocument.value.nodes.filter(
    (node) => !['START', 'END', 'CONDITION'].includes(node.nodeType),
  )
  const responsible = businessNodes.filter(
    (node) => !['SYSTEM_ACTION', 'EVENT_WAIT'].includes(node.nodeType) && node.responsibilityRole,
  )
  const taskNodes = businessNodes.filter(
    (node) => ['HUMAN_TASK', 'REVIEW'].includes(node.nodeType),
  )
  return {
    evidence: taskNodes.filter((node) => node.evidence.length).length,
    forms: taskNodes.filter((node) => Object.keys(node.form).length).length,
    responsibility: responsible.length,
    sla: taskNodes.filter((node) => Object.keys(node.sla).length).length,
    tasks: taskNodes.filter((node) => Object.keys(node.task).length).length,
    total: taskNodes.length,
  }
})

function clone(document: ProjectFulfillmentDocument): ProjectFulfillmentDocument {
  // props/computed 在 Vue 内部是 Proxy；履约文档为纯 JSON，使用 JSON 往返生成可编辑快照。
  return JSON.parse(JSON.stringify(document)) as ProjectFulfillmentDocument
}

function commit(mutator: (draft: ProjectFulfillmentDocument) => void) {
  if (props.readonly) return
  past.value.push(clone(normalizedDocument.value))
  if (past.value.length > 60) past.value.shift()
  future.value = []
  const draft = clone(normalizedDocument.value)
  mutator(draft)
  emit('update:document', draft)
}

function undo() {
  const previous = past.value.pop()
  if (!previous) return
  future.value.push(clone(normalizedDocument.value))
  emit('update:document', previous)
}

function redo() {
  const next = future.value.pop()
  if (!next) return
  past.value.push(clone(normalizedDocument.value))
  emit('update:document', next)
}

function uniqueCode(prefix: string, existing: string[]) {
  let index = existing.length + 1
  let value = `${prefix}_${String(index).padStart(2, '0')}`
  while (existing.includes(value)) {
    index += 1
    value = `${prefix}_${String(index).padStart(2, '0')}`
  }
  return value
}

function defaultNode(item: PaletteItem, position?: { x: number; y: number }): ProjectFulfillmentNodeDraft {
  const phaseId = ['START', 'END'].includes(item.nodeType)
    ? null
    : selectedPhaseId.value || orderedPhases.value[0]?.phaseId || null
  const nodeId = uniqueCode(
    item.nodeType,
    normalizedDocument.value.nodes.map((node) => node.nodeId),
  )
  const base: ProjectFulfillmentNodeDraft = {
    completionResults: item.nodeType === 'REVIEW' ? ['PASS', 'REJECT'] : [],
    condition: {},
    description: null,
    eventWait: {},
    evidence: [],
    exceptionStrategy: null,
    executionSubjectRule: null,
    form: {},
    nodeId,
    nodeName: item.templateKey || `新建${item.label}节点`,
    nodeType: item.nodeType,
    notificationRules: [],
    phaseId,
    positionX: position?.x ?? 420,
    positionY: position?.y ?? 160 + normalizedDocument.value.nodes.length * 120,
    reassignable: false,
    responsibilityRole: null,
    sla: {},
    systemAction: {},
    task: {},
  }
  if (!item.templateKey) return base
  if (item.nodeType === 'HUMAN_TASK') {
    base.task = { taskName: `${item.label}任务`, taskType: nodeId }
    base.responsibilityRole = item.label.includes('客户') ? '项目客服' : '现场工程师'
    base.executionSubjectRule = '按项目责任范围匹配'
    base.completionResults = ['COMPLETED']
  } else if (item.nodeType === 'REVIEW') {
    base.task = { taskName: `${item.label}任务`, taskType: nodeId }
    base.responsibilityRole = '项目运营'
    base.executionSubjectRule = '按项目审核责任匹配'
  } else if (item.nodeType === 'SYSTEM_ACTION') {
    base.systemAction = {
      actionType: nodeId,
      failurePolicy: 'RETRY_THEN_MANUAL',
      failureResult: 'FAILED',
      idempotencyStrategy: 'WORK_ORDER_AND_NODE_EXECUTION',
      retryPolicy: { initialDelaySeconds: 30, maxAttempts: 3, maxDelaySeconds: 300, multiplier: 2 },
      successResult: 'SUCCESS',
      target: item.label,
    }
    base.completionResults = ['SUCCESS', 'FAILED']
  } else if (item.nodeType === 'EVENT_WAIT') {
    base.eventWait = {
      correlationKeyTemplate: 'work-order:{workOrderId}',
      eventType: 'external.event.received',
      maxWaitSeconds: 86400,
      reminderTask: false,
      timeoutStrategy: 'ROUTE_TIMEOUT',
    }
    base.completionResults = ['SUCCESS', 'TIMEOUT']
  }
  return base
}

function addNode(item: PaletteItem, position?: { x: number; y: number }) {
  if (!['START', 'END'].includes(item.nodeType) && !normalizedDocument.value.phases.length) {
    addPhase()
  }
  const node = defaultNode(item, position)
  commit((draft) => draft.nodes.push(node))
  selectedNodeId.value = node.nodeId
  selectedTransitionId.value = undefined
}

function onDragStart(event: globalThis.DragEvent, item: PaletteItem) {
  event.dataTransfer?.setData('application/serviceos-workflow-node', JSON.stringify(item))
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'copy'
}

function onDrop(event: globalThis.DragEvent) {
  const raw = event.dataTransfer?.getData('application/serviceos-workflow-node')
  if (!raw) return
  const item = JSON.parse(raw) as PaletteItem
  addNode(item, screenToFlowCoordinate({ x: event.clientX, y: event.clientY }))
}

function addPhase() {
  const id = uniqueCode('PHASE', normalizedDocument.value.phases.map((phase) => phase.phaseId))
  const phase: ProjectFulfillmentPhaseDraft = {
    description: null,
    displayColor: [
      'hsl(var(--success))',
      'hsl(var(--primary))',
      'hsl(var(--warning))',
      'hsl(var(--accent-foreground))',
    ][normalizedDocument.value.phases.length % 4]!,
    phaseId: id,
    phaseName: `新履约阶段 ${normalizedDocument.value.phases.length + 1}`,
    sequence: normalizedDocument.value.phases.length + 1,
  }
  commit((draft) => draft.phases.push(phase))
  selectedPhaseId.value = id
  return id
}

function createHomeChargingExample() {
  const phases: ProjectFulfillmentPhaseDraft[] = [
    { description: '客户联系与预约', displayColor: 'hsl(var(--success))', phaseId: 'CUSTOMER_CONFIRM', phaseName: '客户确认', sequence: 1 },
    { description: '现场条件采集', displayColor: 'hsl(var(--primary))', phaseId: 'SITE_SURVEY', phaseName: '现场勘测', sequence: 2 },
    { description: '施工与资料提交', displayColor: 'hsl(var(--warning))', phaseId: 'INSTALLATION', phaseName: '施工安装', sequence: 3 },
    { description: '审核与外部交付', displayColor: 'hsl(var(--accent-foreground))', phaseId: 'ACCEPTANCE', phaseName: '验收交付', sequence: 4 },
  ]
  const items = ['开始', '联系客户', '现场勘测', '安装施工', '结束'].map(
    (label) => palette.find((item) => item.label === label)!,
  )
  const nodes = items.map((item, index) => {
    const node = defaultNode(item, { x: 420, y: 60 + index * 180 })
    node.phaseId = index === 1 ? 'CUSTOMER_CONFIRM'
      : index === 2 ? 'SITE_SURVEY'
        : index === 3 ? 'INSTALLATION'
          : null
    return node
  })
  const transitions = nodes.slice(0, -1).map((node, index) => ({
    branchName: null,
    condition: {},
    defaultBranch: false,
    fromNodeId: node.nodeId,
    resultCode: null,
    toNodeId: nodes[index + 1]!.nodeId,
    transitionId: `TRANSITION_${String(index + 1).padStart(2, '0')}`,
  }))
  commit((draft) => {
    draft.schemaVersion = '2.0.0'
    draft.phases = phases
    draft.nodes = nodes
    draft.transitions = transitions
  })
  nextTick(() => fitView({ duration: 300, padding: 0.14 }))
}

function patchPhase(patch: Partial<ProjectFulfillmentPhaseDraft>) {
  if (!selectedPhase.value) return
  commit((draft) => {
    const phase = draft.phases.find((item) => item.phaseId === selectedPhase.value?.phaseId)
    if (phase) Object.assign(phase, patch)
  })
}

function movePhase(delta: number) {
  if (!selectedPhase.value) return
  const index = orderedPhases.value.findIndex((phase) => phase.phaseId === selectedPhase.value?.phaseId)
  const target = index + delta
  if (target < 0 || target >= orderedPhases.value.length) return
  const other = orderedPhases.value[target]
  if (!other) return
  commit((draft) => {
    const current = draft.phases.find((phase) => phase.phaseId === selectedPhase.value?.phaseId)
    const swap = draft.phases.find((phase) => phase.phaseId === other.phaseId)
    if (!current || !swap) return
    const sequence = current.sequence
    current.sequence = swap.sequence
    swap.sequence = sequence
  })
}

function deletePhase() {
  if (!selectedPhase.value) return
  const hasNodes = normalizedDocument.value.nodes.some((node) => node.phaseId === selectedPhase.value?.phaseId)
  if (hasNodes) return
  commit((draft) => {
    draft.phases = draft.phases.filter((phase) => phase.phaseId !== selectedPhase.value?.phaseId)
  })
  selectedPhaseId.value = undefined
}

function patchNode(patch: Partial<ProjectFulfillmentNodeDraft>) {
  if (!selectedNode.value) return
  commit((draft) => {
    const node = draft.nodes.find((item) => item.nodeId === selectedNode.value?.nodeId)
    if (node) Object.assign(node, patch)
  })
}

function patchNodeMap(
  field: 'condition' | 'eventWait' | 'form' | 'sla' | 'systemAction' | 'task',
  patch: Record<string, unknown>,
) {
  if (!selectedNode.value) return
  patchNode({ [field]: { ...selectedNode.value[field], ...patch } })
}

function patchTransition(patch: Partial<ProjectFulfillmentTransitionDraft>) {
  if (!selectedTransition.value) return
  commit((draft) => {
    const transition = draft.transitions.find(
      (item) => item.transitionId === selectedTransition.value?.transitionId,
    )
    if (transition) Object.assign(transition, patch)
  })
}

function onConnect(connection: Connection) {
  if (!connection.source || !connection.target || connection.source === connection.target) return
  const source = normalizedDocument.value.nodes.find((node) => node.nodeId === connection.source)
  const target = normalizedDocument.value.nodes.find((node) => node.nodeId === connection.target)
  if (!source || !target || source.nodeType === 'END' || target.nodeType === 'START') return
  if (normalizedDocument.value.transitions.some(
    (edge) => edge.fromNodeId === connection.source && edge.toNodeId === connection.target,
  )) return
  const transition: ProjectFulfillmentTransitionDraft = {
    branchName: null,
    condition: {},
    defaultBranch: false,
    fromNodeId: connection.source,
    resultCode: null,
    toNodeId: connection.target,
    transitionId: uniqueCode(
      'TRANSITION',
      normalizedDocument.value.transitions.map((item) => item.transitionId),
    ),
  }
  commit((draft) => draft.transitions.push(transition))
  selectedTransitionId.value = transition.transitionId
  selectedNodeId.value = undefined
}

function onNodeClick(event: NodeMouseEvent) {
  if (event.node.id.startsWith('PHASE__')) return
  selectedNodeId.value = event.node.id
  selectedTransitionId.value = undefined
}

function onEdgeClick({ edge }: { edge: Edge }) {
  selectedTransitionId.value = edge.id
  selectedNodeId.value = undefined
}

function onNodeDragStop(event: NodeDragEvent) {
  if (event.node.id.startsWith('PHASE__')) return
  const { x, y } = event.node.position
  patchNodePosition(event.node.id, x, y)
}

function patchNodePosition(nodeId: string, x: number, y: number) {
  commit((draft) => {
    const node = draft.nodes.find((item) => item.nodeId === nodeId)
    if (!node) return
    node.positionX = Math.round(x)
    node.positionY = Math.round(y)
    const phase = phaseRanges.value.find((range) => (
      y >= range.y && y <= range.y + range.height
    ))
    if (phase && !['START', 'END'].includes(node.nodeType)) {
      node.phaseId = phase.phase.phaseId
    }
  })
}

function deleteSelection() {
  if (props.readonly) return
  if (selectedTransitionId.value) {
    commit((draft) => {
      draft.transitions = draft.transitions.filter(
        (transition) => transition.transitionId !== selectedTransitionId.value,
      )
    })
    selectedTransitionId.value = undefined
    return
  }
  if (!selectedNodeId.value) return
  const nodeId = selectedNodeId.value
  commit((draft) => {
    draft.nodes = draft.nodes.filter((node) => node.nodeId !== nodeId)
    draft.transitions = draft.transitions.filter(
      (transition) => transition.fromNodeId !== nodeId && transition.toNodeId !== nodeId,
    )
  })
  selectedNodeId.value = undefined
}

function copySelection() {
  if (!selectedNode.value || props.readonly || ['START'].includes(selectedNode.value.nodeType)) return
  const source = clone(normalizedDocument.value).nodes.find(
    (node) => node.nodeId === selectedNode.value?.nodeId,
  )
  if (!source) return
  source.nodeId = uniqueCode(source.nodeType, normalizedDocument.value.nodes.map((node) => node.nodeId))
  source.nodeName = `${source.nodeName} 副本`
  source.positionX += 220
  source.positionY += 40
  commit((draft) => draft.nodes.push(source))
  selectedNodeId.value = source.nodeId
}

function autoLayout() {
  const outgoing = new Map<string, ProjectFulfillmentTransitionDraft[]>()
  normalizedDocument.value.transitions.forEach((transition) => {
    const list = outgoing.get(transition.fromNodeId) ?? []
    list.push(transition)
    outgoing.set(transition.fromNodeId, list)
  })
  const start = normalizedDocument.value.nodes.find((node) => node.nodeType === 'START')
  const levels = new Map<string, number>()
  const queue = start ? [start.nodeId] : []
  if (start) levels.set(start.nodeId, 0)
  while (queue.length) {
    const current = queue.shift()!
    const level = levels.get(current) ?? 0
    for (const edge of outgoing.get(current) ?? []) {
      if (!levels.has(edge.toNodeId)) {
        levels.set(edge.toNodeId, level + 1)
        queue.push(edge.toNodeId)
      }
    }
  }
  commit((draft) => {
    const byLevel = new Map<number, ProjectFulfillmentNodeDraft[]>()
    draft.nodes.forEach((node) => {
      const level = levels.get(node.nodeId) ?? byLevel.size
      const list = byLevel.get(level) ?? []
      list.push(node)
      byLevel.set(level, list)
    })
    byLevel.forEach((nodes, level) => {
      nodes.sort((a, b) => a.nodeId.localeCompare(b.nodeId))
      nodes.forEach((node, index) => {
        node.positionX = 420 + (index - (nodes.length - 1) / 2) * 260
        node.positionY = 80 + level * 150
      })
    })
  })
  nextTick(() => fitView({ duration: 300, padding: 0.12 }))
}

function createTask() {
  if (!selectedNode.value) return
  patchNodeMap('task', {
    taskName: `${selectedNode.value.nodeName}任务`,
    taskType: selectedNode.value.nodeId,
  })
}

function createForm() {
  if (!selectedNode.value) return
  patchNodeMap('form', {
    fields: [{ fieldKey: 'result', label: '业务结果', required: true, type: 'TEXT' }],
    formKey: `${selectedNode.value.nodeId.toLowerCase()}-form`,
    formName: `${selectedNode.value.nodeName}表单`,
  })
}

function addEvidence() {
  if (!selectedNode.value) return
  patchNode({
    evidence: [
      ...selectedNode.value.evidence,
      {
        evidenceKey: `${selectedNode.value.nodeId.toLowerCase()}-photo-${selectedNode.value.evidence.length + 1}`,
        minCount: 1,
        name: '现场照片',
        required: true,
        type: 'PHOTO',
      },
    ],
  })
}

function createSla() {
  patchNodeMap('sla', {
    name: '24 小时节点时效',
    targetMinutes: 1440,
    timeoutMinutes: 1440,
    warningMinutes: 1200,
  })
}

function onKeydown(event: globalThis.KeyboardEvent) {
  const target = event.target as globalThis.HTMLElement | null
  if (target?.closest('input, textarea, [contenteditable="true"]')) return
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'z') {
    event.preventDefault()
    if (event.shiftKey) redo()
    else undo()
  } else if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'y') {
    event.preventDefault()
    redo()
  } else if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'c') {
    event.preventDefault()
    copySelection()
  } else if (event.key === 'Delete' || event.key === 'Backspace') {
    event.preventDefault()
    deleteSelection()
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

defineExpose({ autoLayout, fitView, redo, undo })
</script>

<template>
  <div class="workflow-designer-workspace" :class="{ 'is-readonly': readonly }">
    <aside class="workflow-library">
      <div class="workflow-library-tabs">
        <button class="active" type="button">节点库</button>
        <button type="button">阶段库</button>
      </div>
      <Input v-model:value="search" allow-clear placeholder="搜索节点" />
      <div class="workflow-palette">
        <section v-for="category in ['基础节点', '执行节点', '控制节点', '公共业务节点']" :key="category">
          <h3>{{ category }}</h3>
          <button
            v-for="item in filteredPalette.filter((candidate) => candidate.category === category)"
            :key="`${item.category}-${item.label}`"
            type="button"
            draggable="true"
            :disabled="readonly"
            @dragstart="onDragStart($event, item)"
            @dblclick="addNode(item)"
          >
            <span :class="`palette-icon is-${item.nodeType.toLowerCase()}`">
              {{ item.nodeType === 'START' ? '▶' : item.nodeType === 'END' ? '■' : item.nodeType === 'CONDITION' ? '◇' : item.nodeType === 'EVENT_WAIT' ? '⌛' : item.nodeType === 'SYSTEM_ACTION' ? '⚙' : item.nodeType === 'REVIEW' ? '✓' : '●' }}
            </span>
            <span><strong>{{ item.label }}</strong><small>{{ item.templateKey ? '复制为当前版本独立草稿' : '拖入画布或双击添加' }}</small></span>
          </button>
        </section>
      </div>
      <section class="workflow-phase-outline">
        <header><h3>履约阶段</h3><Button size="small" :disabled="readonly" @click="addPhase">新增</Button></header>
        <button
          v-for="phase in orderedPhases"
          :key="phase.phaseId"
          type="button"
          :class="{ active: phase.phaseId === selectedPhaseId }"
          @click="selectedPhaseId = phase.phaseId"
        >
          <i :style="{ background: phase.displayColor }" />
          <span><strong>{{ phase.phaseName }}</strong><small>{{ document.nodes.filter((node) => node.phaseId === phase.phaseId).length }} 个节点</small></span>
        </button>
        <div v-if="selectedPhase" class="phase-inline-editor">
          <Input :value="selectedPhase.phaseName" @update:value="patchPhase({ phaseName: String($event) })" />
          <Input :value="selectedPhase.description ?? ''" placeholder="阶段说明" @update:value="patchPhase({ description: String($event) })" />
          <div><Button size="small" @click="movePhase(-1)">上移</Button><Button size="small" @click="movePhase(1)">下移</Button><Button size="small" danger :disabled="document.nodes.some((node) => node.phaseId === selectedPhase?.phaseId)" @click="deletePhase">删除</Button></div>
        </div>
      </section>
    </aside>

    <main class="workflow-canvas-column">
      <div class="workflow-canvas-toolbar">
        <Button size="small" :disabled="!past.length || readonly" @click="undo">撤销</Button>
        <Button size="small" :disabled="!future.length || readonly" @click="redo">重做</Button>
        <Button size="small" :disabled="readonly" @click="copySelection">复制</Button>
        <Button size="small" :disabled="readonly" @click="deleteSelection">删除</Button>
        <span />
        <Button size="small" @click="autoLayout">纵向布局</Button>
        <Button size="small" @click="fitView({ duration: 300, padding: 0.12 })">适应画布</Button>
      </div>
      <div class="workflow-canvas-shell" @dragover.prevent @drop.prevent="onDrop">
        <VueFlow
          :nodes="flowNodes"
          :edges="flowEdges"
          :nodes-draggable="!readonly"
          :nodes-connectable="!readonly"
          :elements-selectable="true"
          :delete-key-code="null"
          :min-zoom="0.25"
          :max-zoom="1.8"
          fit-view-on-init
          @connect="onConnect"
          @node-click="onNodeClick"
          @edge-click="onEdgeClick"
          @node-drag-stop="onNodeDragStop"
          @pane-click="selectedNodeId = undefined; selectedTransitionId = undefined"
        >
          <Background :gap="18" :size="1" pattern-color="hsl(var(--border))" />
          <Controls position="bottom-left" />
          <MiniMap
            pannable
            zoomable
            position="bottom-right"
            :node-color="(node) => node.id.startsWith('PHASE__') ? 'hsl(var(--muted))' : 'hsl(var(--primary))'"
          />
          <template #node-business="slotProps">
            <WorkflowBusinessNode v-bind="slotProps" />
          </template>
          <template #node-phase="slotProps">
            <WorkflowPhaseLane v-bind="slotProps" />
          </template>
        </VueFlow>

        <div v-if="!document.nodes.length" class="workflow-empty-state">
          <strong>从第一步开始搭建履约流程</strong>
          <span>选择标准流程、创建履约阶段，或把开始节点拖入画布。</span>
          <div>
            <Button type="primary" @click="createHomeChargingExample">从标准流程模板创建</Button>
            <Button @click="addPhase">创建第一个履约阶段</Button>
            <Button @click="addNode(palette[0]!)">添加开始节点</Button>
            <Button @click="filteredPalette.find((item) => item.label === '现场勘测') && addNode(filteredPalette.find((item) => item.label === '现场勘测')!)">拖入业务节点</Button>
          </div>
        </div>
      </div>
      <div class="workflow-completeness-bar">
        <span><strong>流程结构</strong>{{ document.nodes.length }} 节点 / {{ document.transitions.length }} 连线</span>
        <span><strong>节点责任</strong>{{ completeness.responsibility }}/{{ completeness.total }}</span>
        <span><strong>任务配置</strong>{{ completeness.tasks }}/{{ completeness.total }}</span>
        <span><strong>表单配置</strong>{{ completeness.forms }}/{{ completeness.total }}</span>
        <span><strong>证据规则</strong>{{ completeness.evidence }}/{{ completeness.total }}</span>
        <span><strong>SLA</strong>{{ completeness.sla }}/{{ completeness.total }}</span>
      </div>
    </main>

    <aside class="workflow-inspector">
      <template v-if="selectedNode">
        <header>
          <div><span>当前节点</span><h2>{{ selectedNode.nodeName }}</h2></div>
          <Tag :color="nodeIssues.length ? 'warning' : 'processing'">{{ selectedNode.nodeType }}</Tag>
        </header>
        <nav>
          <button v-for="tab in ['base', 'flow', 'task', 'form', 'evidence', 'sla', 'exception']" :key="tab" type="button" :class="{ active: activeInspector === tab }" @click="activeInspector = tab">
            {{ ({ base: '基础信息', flow: '流转规则', task: '执行任务', form: '表单数据', evidence: '证据要求', sla: 'SLA规则', exception: '异常处理' } as Record<string, string>)[tab] }}
          </button>
        </nav>
        <div class="workflow-inspector-body">
          <section v-if="activeInspector === 'base'">
            <h3>基础信息</h3>
            <label><span>节点名称</span><Input :value="selectedNode.nodeName" :disabled="readonly" @update:value="patchNode({ nodeName: String($event) })" /></label>
            <label><span>所属阶段</span><Select :value="selectedNode.phaseId ?? undefined" :disabled="readonly || ['START', 'END'].includes(selectedNode.nodeType)" :options="orderedPhases.map((phase) => ({ label: phase.phaseName, value: phase.phaseId }))" @update:value="patchNode({ phaseId: String($event) })" /></label>
            <label><span>业务说明</span><Input.TextArea :value="selectedNode.description ?? ''" :rows="3" :disabled="readonly" @update:value="patchNode({ description: String($event) })" /></label>
            <template v-if="['HUMAN_TASK', 'REVIEW'].includes(selectedNode.nodeType)">
              <label><span>责任角色</span><Input :value="selectedNode.responsibilityRole ?? ''" :disabled="readonly" placeholder="例如：项目运营" @update:value="patchNode({ responsibilityRole: String($event) })" /></label>
              <label><span>执行主体规则</span><Input :value="selectedNode.executionSubjectRule ?? ''" :disabled="readonly" @update:value="patchNode({ executionSubjectRule: String($event) })" /></label>
              <label class="switch-row"><span>允许转派</span><Checkbox :checked="selectedNode.reassignable" :disabled="readonly" @change="patchNode({ reassignable: Boolean(($event.target as HTMLInputElement).checked) })" /></label>
            </template>
          </section>
          <section v-else-if="activeInspector === 'flow'">
            <h3>节点完成结果</h3>
            <Input :value="selectedNode.completionResults.join(', ')" :disabled="readonly" placeholder="PASS, REJECT" @update:value="patchNode({ completionResults: String($event).split(/[,，\\s]+/).filter(Boolean).map((value) => value.toUpperCase()) })" />
            <div class="inspector-list">
              <button v-for="edge in document.transitions.filter((item) => item.fromNodeId === selectedNode?.nodeId)" :key="edge.transitionId" type="button" @click="selectedTransitionId = edge.transitionId; selectedNodeId = undefined">
                <strong>{{ edge.resultCode || '普通完成' }}</strong><span>→ {{ document.nodes.find((node) => node.nodeId === edge.toNodeId)?.nodeName }}</span>
              </button>
            </div>
            <template v-if="selectedNode.nodeType === 'CONDITION'">
              <h3>条件数据</h3>
              <label><span>数据来源</span><Input :value="String(selectedNode.condition.dataSource ?? '')" @update:value="patchNodeMap('condition', { dataSource: String($event) })" /></label>
              <label><span>字段</span><Input :value="String(selectedNode.condition.field ?? '')" @update:value="patchNodeMap('condition', { field: String($event) })" /></label>
            </template>
          </section>
          <section v-else-if="activeInspector === 'task'">
            <template v-if="['HUMAN_TASK', 'REVIEW'].includes(selectedNode.nodeType)">
              <div v-if="!Object.keys(selectedNode.task).length" class="asset-empty"><strong>任务尚未创建</strong><span>新节点不会伪装成已有任务。</span><div><Button type="primary" @click="createTask">创建任务</Button><Button @click="createTask">从公共模板复制</Button></div></div>
              <template v-else>
                <h3>{{ selectedNode.nodeType === 'REVIEW' ? '审核任务' : '主任务' }}</h3>
                <label><span>任务名称</span><Input :value="String(selectedNode.task.taskName ?? '')" @update:value="patchNodeMap('task', { taskName: String($event) })" /></label>
                <label><span>任务类型</span><Input :value="String(selectedNode.task.taskType ?? '')" @update:value="patchNodeMap('task', { taskType: String($event).toUpperCase() })" /></label>
              </template>
            </template>
            <template v-else-if="selectedNode.nodeType === 'SYSTEM_ACTION'">
              <div v-if="!Object.keys(selectedNode.systemAction).length" class="asset-empty"><strong>系统动作尚未配置</strong><Button type="primary" @click="patchNodeMap('systemAction', { actionType: selectedNode?.nodeId, target: '', idempotencyStrategy: 'WORK_ORDER_AND_NODE_EXECUTION', retryPolicy: { maxAttempts: 3, initialDelaySeconds: 30, multiplier: 2, maxDelaySeconds: 300 }, successResult: 'SUCCESS', failureResult: 'FAILED', failurePolicy: 'RETRY_THEN_MANUAL' })">配置系统动作</Button></div>
              <template v-else>
                <h3>系统动作</h3>
                <label><span>动作类型</span><Input :value="String(selectedNode.systemAction.actionType ?? '')" @update:value="patchNodeMap('systemAction', { actionType: String($event) })" /></label>
                <label><span>调用目标</span><Input :value="String(selectedNode.systemAction.target ?? '')" @update:value="patchNodeMap('systemAction', { target: String($event) })" /></label>
                <label><span>幂等策略</span><Input :value="String(selectedNode.systemAction.idempotencyStrategy ?? '')" @update:value="patchNodeMap('systemAction', { idempotencyStrategy: String($event) })" /></label>
                <label><span>失败结果</span><Input :value="String(selectedNode.systemAction.failureResult ?? '')" @update:value="patchNodeMap('systemAction', { failureResult: String($event) })" /></label>
              </template>
            </template>
            <template v-else-if="selectedNode.nodeType === 'EVENT_WAIT'">
              <div v-if="!Object.keys(selectedNode.eventWait).length" class="asset-empty"><strong>等待规则尚未配置</strong><Button type="primary" @click="patchNodeMap('eventWait', { eventType: 'external.event.received', correlationKeyTemplate: 'work-order:{workOrderId}', maxWaitSeconds: 86400, timeoutStrategy: 'ROUTE_TIMEOUT', reminderTask: false })">配置事件等待</Button></div>
              <template v-else>
                <h3>事件等待</h3>
                <label><span>等待事件类型</span><Input :value="String(selectedNode.eventWait.eventType ?? '')" @update:value="patchNodeMap('eventWait', { eventType: String($event) })" /></label>
                <label><span>事件匹配规则</span><Input :value="String(selectedNode.eventWait.correlationKeyTemplate ?? '')" @update:value="patchNodeMap('eventWait', { correlationKeyTemplate: String($event) })" /></label>
                <label><span>最大等待秒数</span><Input :value="String(selectedNode.eventWait.maxWaitSeconds ?? '')" @update:value="patchNodeMap('eventWait', { maxWaitSeconds: Number($event) })" /></label>
                <label><span>超时策略</span><Input :value="String(selectedNode.eventWait.timeoutStrategy ?? '')" @update:value="patchNodeMap('eventWait', { timeoutStrategy: String($event) })" /></label>
              </template>
            </template>
            <div v-else class="asset-empty"><strong>该控制节点不创建任务</strong><span>Phase、开始、结束和条件节点不承担人工责任。</span></div>
          </section>
          <section v-else-if="activeInspector === 'form'">
            <div v-if="!Object.keys(selectedNode.form).length" class="asset-empty"><strong>表单尚未配置</strong><div><Button type="primary" @click="createForm">创建表单</Button><Button @click="createForm">从公共模板复制</Button></div></div>
            <template v-else><h3>业务表单</h3><label><span>表单名称</span><Input :value="String(selectedNode.form.formName ?? '')" @update:value="patchNodeMap('form', { formName: String($event) })" /></label><p>已配置 {{ Array.isArray(selectedNode.form.fields) ? selectedNode.form.fields.length : 0 }} 个字段</p></template>
          </section>
          <section v-else-if="activeInspector === 'evidence'">
            <div v-if="!selectedNode.evidence.length" class="asset-empty"><strong>证据要求尚未配置</strong><div><Button type="primary" @click="addEvidence">添加证据</Button><Button @click="addEvidence">从模板复制</Button></div></div>
            <template v-else><h3>证据要求</h3><article v-for="(item, index) in selectedNode.evidence" :key="index" class="asset-row"><strong>{{ item.name || `证据 ${index + 1}` }}</strong><span>{{ item.required ? '必传' : '选传' }} · 最少 {{ item.minCount || 1 }} 份</span></article><Button @click="addEvidence">继续添加</Button></template>
          </section>
          <section v-else-if="activeInspector === 'sla'">
            <div v-if="!Object.keys(selectedNode.sla).length" class="asset-empty"><strong>SLA 尚未配置</strong><div><Button type="primary" @click="createSla">配置 SLA</Button><Button @click="createSla">从模板复制</Button></div></div>
            <template v-else><h3>节点 SLA</h3><label><span>目标分钟</span><Input :value="String(selectedNode.sla.targetMinutes ?? '')" @update:value="patchNodeMap('sla', { targetMinutes: Number($event) })" /></label><label><span>预警分钟</span><Input :value="String(selectedNode.sla.warningMinutes ?? '')" @update:value="patchNodeMap('sla', { warningMinutes: Number($event) })" /></label><label><span>超时分钟</span><Input :value="String(selectedNode.sla.timeoutMinutes ?? '')" @update:value="patchNodeMap('sla', { timeoutMinutes: Number($event) })" /></label></template>
          </section>
          <section v-else>
            <h3>异常与通知</h3>
            <label><span>异常策略</span><Input :value="selectedNode.exceptionStrategy ?? ''" placeholder="例如：进入人工恢复" @update:value="patchNode({ exceptionStrategy: String($event) })" /></label>
          </section>
          <div v-if="nodeIssues.length" class="node-issues">
            <article v-for="issue in nodeIssues" :key="issue.errorCode"><Tag color="error">{{ issue.errorCode }}</Tag><strong>{{ issue.userMessage }}</strong><span>{{ issue.suggestion }}</span></article>
          </div>
        </div>
      </template>
      <template v-else-if="selectedTransition">
        <header><div><span>当前连线</span><h2>流转规则</h2></div><Tag>Transition</Tag></header>
        <div class="workflow-inspector-body">
          <section>
            <h3>完成结果</h3>
            <label><span>结果编码</span><Input :value="selectedTransition.resultCode ?? ''" :disabled="readonly" placeholder="PASS / REJECT / SUCCESS" @update:value="patchTransition({ resultCode: String($event).toUpperCase() || null })" /></label>
            <label><span>分支名称</span><Input :value="selectedTransition.branchName ?? ''" :disabled="readonly" @update:value="patchTransition({ branchName: String($event) || null })" /></label>
            <label class="switch-row"><span>默认分支</span><Checkbox :checked="selectedTransition.defaultBranch" :disabled="readonly" @change="patchTransition({ defaultBranch: Boolean(($event.target as HTMLInputElement).checked) })" /></label>
            <dl class="transition-summary"><div><dt>来源</dt><dd>{{ document.nodes.find((node) => node.nodeId === selectedTransition?.fromNodeId)?.nodeName }}</dd></div><div><dt>目标</dt><dd>{{ document.nodes.find((node) => node.nodeId === selectedTransition?.toNodeId)?.nodeName }}</dd></div></dl>
          </section>
        </div>
      </template>
      <div v-else class="workflow-inspector-empty"><strong>选择节点或连线</strong><span>右侧面板统一承担节点资产和流转配置。</span></div>
    </aside>
  </div>
</template>
