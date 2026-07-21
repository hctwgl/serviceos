<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import ConditionBuilder from './ConditionBuilder.vue'
import {
  WORKFLOW_NODE_TYPES,
  defaultNodeForType,
  nextNodeId,
  validateWorkflowNode,
  type WorkflowCanvasNode,
  type WorkflowNodeType,
} from '../expression/workflowCanvasModel'

export type WorkflowNode = WorkflowCanvasNode

export type TransitionCondition = {
  language: 'SERVICEOS_EXPR_V1'
  source: string
}

export type WorkflowTransition = {
  transitionId?: string
  from: string
  to: string
  priority?: number
  condition?: TransitionCondition | null
}

export type NodeLayout = { x: number; y: number }

const props = defineProps<{
  definitionJson: string
}>()

const emit = defineEmits<{
  'update:definitionJson': [value: string]
  'select-node': [node: WorkflowNode | null]
}>()

const NODE_W = 160
const NODE_H = 72
const surfaceRef = ref<HTMLElement | null>(null)
const draggingId = ref<string | null>(null)
const dragOffset = ref({ x: 0, y: 0 })
const mode = ref<'move' | 'connect'>('move')
const connectFrom = ref<string | null>(null)
const selectedEdgeKey = ref<string | null>(null)
const selectedNodeId = ref<string | null>(null)
const conditionDraft = ref('')
const paletteType = ref<WorkflowNodeType>('SERVICE_TASK')

const undoStack = ref<string[]>([])
const redoStack = ref<string[]>([])
const suppressHistory = ref(false)
const dragSnapshot = ref<string | null>(null)

type ParsedWorkflow = {
  nodes: WorkflowNode[]
  transitions: WorkflowTransition[]
  layout: Record<string, NodeLayout>
  raw: Record<string, unknown>
  startNodeId: string | null
}

const parsed = computed<ParsedWorkflow | null>(() => {
  try {
    const raw = JSON.parse(props.definitionJson) as Record<string, unknown>
    const nodes = (Array.isArray(raw.nodes) ? raw.nodes : []) as WorkflowNode[]
    const transitions = (Array.isArray(raw.transitions)
      ? raw.transitions
      : []) as WorkflowTransition[]
    const metadata = (raw.metadata && typeof raw.metadata === 'object'
      ? (raw.metadata as Record<string, unknown>)
      : {}) as Record<string, unknown>
    const layoutSource = (metadata.layout && typeof metadata.layout === 'object'
      ? (metadata.layout as Record<string, NodeLayout>)
      : {}) as Record<string, NodeLayout>
    const layout: Record<string, NodeLayout> = { ...layoutSource }
    nodes.forEach((node, index) => {
      if (!layout[node.nodeId]) {
        layout[node.nodeId] = {
          x: 40 + (index % 3) * 200,
          y: 40 + Math.floor(index / 3) * 120,
        }
      }
    })
    const startNodeId = typeof raw.startNodeId === 'string' ? raw.startNodeId : null
    return { nodes, transitions, layout, raw, startNodeId }
  } catch {
    return null
  }
})

const edgeViews = computed(() => {
  if (!parsed.value) {
    return [] as Array<{
      key: string
      index: number
      from: string
      to: string
      x1: number
      y1: number
      x2: number
      y2: number
      hasCondition: boolean
    }>
  }
  const { transitions, layout } = parsed.value
  return transitions
    .map((edge, index) => {
      const from = layout[edge.from]
      const to = layout[edge.to]
      if (!from || !to) return null
      const key = edge.transitionId ?? `${edge.from}->${edge.to}-${index}`
      return {
        key,
        index,
        from: edge.from,
        to: edge.to,
        x1: from.x + NODE_W / 2,
        y1: from.y + NODE_H / 2,
        x2: to.x + NODE_W / 2,
        y2: to.y + NODE_H / 2,
        hasCondition: Boolean(edge.condition?.source),
      }
    })
    .filter((edge): edge is NonNullable<typeof edge> => edge != null)
})

const selectedEdge = computed(() => {
  if (!parsed.value || selectedEdgeKey.value == null) return null
  return edgeViews.value.find((edge) => edge.key === selectedEdgeKey.value) ?? null
})

const selectedFromNode = computed(() => {
  if (!parsed.value || !selectedEdge.value) return null
  return parsed.value.nodes.find((node) => node.nodeId === selectedEdge.value!.from) ?? null
})

const selectedNode = computed(() => {
  if (!parsed.value || !selectedNodeId.value) return null
  return parsed.value.nodes.find((node) => node.nodeId === selectedNodeId.value) ?? null
})

const selectedNodeErrors = computed(() =>
  selectedNode.value ? validateWorkflowNode(selectedNode.value) : [],
)

const nodeErrorsById = computed(() => {
  const map = new Map<string, string[]>()
  if (!parsed.value) return map
  for (const node of parsed.value.nodes) {
    const errors = validateWorkflowNode(node)
    if (errors.length) map.set(node.nodeId, errors)
  }
  return map
})

const canUndo = computed(() => undoStack.value.length > 0)
const canRedo = computed(() => redoStack.value.length > 0)

const minimapNodes = computed(() => {
  if (!parsed.value) return []
  const maxX = Math.max(...Object.values(parsed.value.layout).map((p) => p.x + NODE_W), 400)
  const maxY = Math.max(...Object.values(parsed.value.layout).map((p) => p.y + NODE_H), 300)
  return parsed.value.nodes.map((node) => {
    const pos = parsed.value!.layout[node.nodeId] ?? { x: 0, y: 0 }
    return {
      id: node.nodeId,
      invalid: nodeErrorsById.value.has(node.nodeId),
      left: `${(pos.x / maxX) * 100}%`,
      top: `${(pos.y / maxY) * 100}%`,
      width: `${(NODE_W / maxX) * 100}%`,
      height: `${(NODE_H / maxY) * 100}%`,
    }
  })
})

watch(
  () => props.definitionJson,
  () => {
    // 外部 JSON 变更时，若选中节点已不存在则清空选择
    if (selectedNodeId.value && parsed.value) {
      if (!parsed.value.nodes.some((n) => n.nodeId === selectedNodeId.value)) {
        selectedNodeId.value = null
      }
    }
  },
)

watch(
  selectedNode,
  (node) => {
    emit('select-node', node)
  },
  { immediate: true },
)

function pushHistory(previousJson: string) {
  if (suppressHistory.value) return
  if (undoStack.value[undoStack.value.length - 1] === previousJson) return
  undoStack.value = [...undoStack.value.slice(-49), previousJson]
  redoStack.value = []
}

function emitJson(next: Record<string, unknown>, options?: { recordHistory?: boolean }) {
  const serialized = JSON.stringify(next, null, 2)
  if (options?.recordHistory !== false) {
    pushHistory(props.definitionJson)
  }
  emit('update:definitionJson', serialized)
}

function emitWorkflow(
  patch: {
    nodes?: WorkflowNode[]
    layout?: Record<string, NodeLayout>
    transitions?: WorkflowTransition[]
  },
  options?: { recordHistory?: boolean },
) {
  if (!parsed.value) return
  const next = {
    ...parsed.value.raw,
    nodes: patch.nodes ?? parsed.value.nodes,
    transitions: patch.transitions ?? parsed.value.transitions,
    metadata: {
      ...((parsed.value.raw.metadata as Record<string, unknown> | undefined) ?? {}),
      layout: patch.layout ?? parsed.value.layout,
    },
  }
  emitJson(next, options)
}

function undo() {
  if (!canUndo.value) return
  const previous = undoStack.value[undoStack.value.length - 1]
  undoStack.value = undoStack.value.slice(0, -1)
  redoStack.value = [...redoStack.value, props.definitionJson]
  suppressHistory.value = true
  emit('update:definitionJson', previous)
  suppressHistory.value = false
}

function redo() {
  if (!canRedo.value) return
  const next = redoStack.value[redoStack.value.length - 1]
  redoStack.value = redoStack.value.slice(0, -1)
  undoStack.value = [...undoStack.value, props.definitionJson]
  suppressHistory.value = true
  emit('update:definitionJson', next)
  suppressHistory.value = false
}

function onPointerDown(event: PointerEvent, nodeId: string) {
  if (mode.value === 'connect') {
    event.stopPropagation()
    if (!connectFrom.value) {
      connectFrom.value = nodeId
      return
    }
    if (connectFrom.value === nodeId) {
      connectFrom.value = null
      return
    }
    addEdge(connectFrom.value, nodeId)
    connectFrom.value = null
    mode.value = 'move'
    return
  }
  if (!parsed.value || !surfaceRef.value) return
  const layout = parsed.value.layout[nodeId]
  if (!layout) return
  selectedNodeId.value = nodeId
  selectedEdgeKey.value = null
  const bounds = surfaceRef.value.getBoundingClientRect()
  draggingId.value = nodeId
  dragSnapshot.value = props.definitionJson
  dragOffset.value = {
    x: event.clientX - bounds.left - layout.x,
    y: event.clientY - bounds.top - layout.y,
  }
  ;(event.target as HTMLElement).setPointerCapture?.(event.pointerId)
}

function onPointerMove(event: PointerEvent) {
  if (!draggingId.value || !parsed.value || !surfaceRef.value) return
  const bounds = surfaceRef.value.getBoundingClientRect()
  const nextX = Math.max(0, event.clientX - bounds.left - dragOffset.value.x)
  const nextY = Math.max(0, event.clientY - bounds.top - dragOffset.value.y)
  emitWorkflow(
    {
      layout: {
        ...parsed.value.layout,
        [draggingId.value]: { x: Math.round(nextX), y: Math.round(nextY) },
      },
    },
    { recordHistory: false },
  )
}

function onPointerUp() {
  if (draggingId.value && dragSnapshot.value && dragSnapshot.value !== props.definitionJson) {
    pushHistory(dragSnapshot.value)
  }
  draggingId.value = null
  dragSnapshot.value = null
}

function addEdge(from: string, to: string) {
  if (!parsed.value) return
  const exists = parsed.value.transitions.some((edge) => edge.from === from && edge.to === to)
  if (exists) return
  const transitionId = `t_${from}_${to}_${Date.now().toString(36)}`
  const next = [...parsed.value.transitions, { transitionId, from, to }]
  emitWorkflow({ transitions: next })
  selectedEdgeKey.value = transitionId
  selectedNodeId.value = null
  conditionDraft.value = ''
}

function selectEdge(key: string, index: number) {
  selectedEdgeKey.value = key
  selectedNodeId.value = null
  const edge = parsed.value?.transitions[index]
  conditionDraft.value = edge?.condition?.source ?? ''
}

function deleteSelectedEdge() {
  if (!parsed.value || !selectedEdge.value) return
  const next = parsed.value.transitions.filter((_, index) => index !== selectedEdge.value!.index)
  selectedEdgeKey.value = null
  conditionDraft.value = ''
  emitWorkflow({ transitions: next })
}

function saveCondition() {
  if (!parsed.value || !selectedEdge.value) return
  const fromType = selectedFromNode.value?.nodeType
  if (fromType !== 'EXCLUSIVE_GATEWAY') {
    return
  }
  const source = conditionDraft.value.trim()
  const next = parsed.value.transitions.map((edge, index) => {
    if (index !== selectedEdge.value!.index) return edge
    if (!source) {
      const { condition: _ignored, ...rest } = edge
      return rest
    }
    return {
      ...edge,
      condition: { language: 'SERVICEOS_EXPR_V1' as const, source },
    }
  })
  emitWorkflow({ transitions: next })
}

function autoLayout() {
  if (!parsed.value) return
  const layout: Record<string, NodeLayout> = {}
  parsed.value.nodes.forEach((node, index) => {
    layout[node.nodeId] = {
      x: 40 + (index % 3) * 200,
      y: 40 + Math.floor(index / 3) * 120,
    }
  })
  emitWorkflow({ layout })
}

function startConnect() {
  mode.value = 'connect'
  connectFrom.value = null
  selectedEdgeKey.value = null
  selectedNodeId.value = null
  draggingId.value = null
}

function cancelConnect() {
  mode.value = 'move'
  connectFrom.value = null
}

function addNodeFromPalette() {
  if (!parsed.value) return
  const nodeType = paletteType.value
  const nodeId = nextNodeId(
    parsed.value.nodes.map((n) => n.nodeId),
    nodeType,
  )
  const node = defaultNodeForType(nodeType, nodeId)
  const count = parsed.value.nodes.length
  const layout = {
    ...parsed.value.layout,
    [nodeId]: { x: 60 + (count % 3) * 200, y: 60 + Math.floor(count / 3) * 120 },
  }
  emitWorkflow({ nodes: [...parsed.value.nodes, node], layout })
  selectedNodeId.value = nodeId
  selectedEdgeKey.value = null
}

function deleteSelectedNode() {
  if (!parsed.value || !selectedNode.value) return
  const nodeId = selectedNode.value.nodeId
  if (parsed.value.startNodeId === nodeId) {
    return
  }
  const nodes = parsed.value.nodes.filter((n) => n.nodeId !== nodeId)
  const transitions = parsed.value.transitions.filter(
    (t) => t.from !== nodeId && t.to !== nodeId,
  )
  const layout = { ...parsed.value.layout }
  delete layout[nodeId]
  selectedNodeId.value = null
  emitWorkflow({ nodes, transitions, layout })
}

function patchSelectedNode(patch: Partial<WorkflowNode>) {
  if (!parsed.value || !selectedNode.value) return
  const nodes = parsed.value.nodes.map((node) =>
    node.nodeId === selectedNode.value!.nodeId ? { ...node, ...patch } : node,
  )
  emitWorkflow({ nodes })
}

function blankToNull(value: string): string | null {
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}
</script>

<template>
  <section class="canvas-wrap" data-testid="workflow-canvas">
    <header class="canvas-toolbar">
      <strong>Workflow 画布</strong>
      <div class="actions">
        <label class="palette">
          添加节点
          <select v-model="paletteType" data-testid="palette-node-type">
            <option v-for="type in WORKFLOW_NODE_TYPES" :key="type" :value="type">{{ type }}</option>
          </select>
        </label>
        <button type="button" data-testid="add-node" @click="addNodeFromPalette">添加</button>
        <button
          type="button"
          data-testid="connect-mode"
          :class="{ active: mode === 'connect' }"
          @click="mode === 'connect' ? cancelConnect() : startConnect()"
        >
          {{
            mode === 'connect'
              ? connectFrom
                ? `选择目标（已选 ${connectFrom}）`
                : '连接：选择源节点'
              : '连接边'
          }}
        </button>
        <button type="button" data-testid="auto-layout" @click="autoLayout">自动排布</button>
        <button type="button" data-testid="undo-canvas" :disabled="!canUndo" @click="undo">
          撤销
        </button>
        <button type="button" data-testid="redo-canvas" :disabled="!canRedo" @click="redo">
          重做
        </button>
      </div>
    </header>

    <div class="workspace">
      <div
        v-if="parsed"
        ref="surfaceRef"
        class="canvas"
        :class="{ connecting: mode === 'connect' }"
        data-testid="workflow-canvas-surface"
        @pointermove="onPointerMove"
        @pointerup="onPointerUp"
        @pointerleave="onPointerUp"
      >
        <svg class="edges" data-testid="workflow-edges">
          <defs>
            <marker id="arrow" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
              <path d="M0,0 L6,3 L0,6 Z" fill="#57606a" />
            </marker>
            <marker
              id="arrow-active"
              markerWidth="8"
              markerHeight="8"
              refX="6"
              refY="3"
              orient="auto"
            >
              <path d="M0,0 L6,3 L0,6 Z" fill="#0969da" />
            </marker>
          </defs>
          <g v-for="edge in edgeViews" :key="edge.key">
            <line
              :x1="edge.x1"
              :y1="edge.y1"
              :x2="edge.x2"
              :y2="edge.y2"
              stroke="transparent"
              stroke-width="12"
              class="edge-hit"
              :data-testid="`canvas-edge-${edge.key}`"
              @pointerdown.stop.prevent="selectEdge(edge.key, edge.index)"
            />
            <line
              :x1="edge.x1"
              :y1="edge.y1"
              :x2="edge.x2"
              :y2="edge.y2"
              :stroke="selectedEdgeKey === edge.key ? '#0969da' : '#57606a'"
              :stroke-width="selectedEdgeKey === edge.key ? 3 : 2"
              :stroke-dasharray="edge.hasCondition ? '6 4' : undefined"
              :marker-end="selectedEdgeKey === edge.key ? 'url(#arrow-active)' : 'url(#arrow)'"
              pointer-events="none"
            />
          </g>
        </svg>
        <article
          v-for="node in parsed.nodes"
          :key="node.nodeId"
          class="node"
          :class="[
            `type-${node.nodeType}`,
            {
              dragging: draggingId === node.nodeId,
              'connect-source': connectFrom === node.nodeId,
              selected: selectedNodeId === node.nodeId,
              invalid: nodeErrorsById.has(node.nodeId),
            },
          ]"
          :style="{
            left: `${parsed.layout[node.nodeId]?.x ?? 0}px`,
            top: `${parsed.layout[node.nodeId]?.y ?? 0}px`,
            width: `${NODE_W}px`,
            height: `${NODE_H}px`,
          }"
          :data-testid="`canvas-node-${node.nodeId}`"
          @pointerdown="onPointerDown($event, node.nodeId)"
        >
          <strong>{{ node.nodeId }}</strong>
          <span>{{ node.nodeType }}</span>
          <span v-if="node.name">{{ node.name }}</span>
        </article>
      </div>
      <p v-else class="muted">无法解析 WORKFLOW JSON，画布不可用。</p>

      <aside class="side-panels">
        <div class="minimap" data-testid="workflow-minimap" aria-label="小地图">
          <strong>小地图</strong>
          <div class="minimap-surface">
            <span
              v-for="item in minimapNodes"
              :key="item.id"
              class="minimap-node"
              :class="{ invalid: item.invalid }"
              :style="{ left: item.left, top: item.top, width: item.width, height: item.height }"
            />
          </div>
        </div>

        <aside v-if="selectedNode" class="node-panel" data-testid="node-property-panel">
          <h3>节点属性 · {{ selectedNode.nodeId }}</h3>
          <label>
            name
            <input
              data-testid="node-prop-name"
              :value="selectedNode.name ?? ''"
              @change="patchSelectedNode({ name: ($event.target as HTMLInputElement).value })"
            />
          </label>
          <label>
            stageCode
            <input
              data-testid="node-prop-stage"
              :value="selectedNode.stageCode ?? ''"
              @change="
                patchSelectedNode({ stageCode: blankToNull(($event.target as HTMLInputElement).value) })
              "
            />
          </label>
          <label>
            taskType
            <input
              data-testid="node-prop-task-type"
              :value="selectedNode.taskType ?? ''"
              @change="
                patchSelectedNode({ taskType: blankToNull(($event.target as HTMLInputElement).value) })
              "
            />
          </label>
          <label>
            formRef
            <input
              data-testid="node-prop-form-ref"
              :value="selectedNode.formRef ?? ''"
              @change="
                patchSelectedNode({ formRef: blankToNull(($event.target as HTMLInputElement).value) })
              "
            />
          </label>
          <label>
            slaRef
            <input
              data-testid="node-prop-sla-ref"
              :value="selectedNode.slaRef ?? ''"
              @change="
                patchSelectedNode({ slaRef: blankToNull(($event.target as HTMLInputElement).value) })
              "
            />
          </label>
          <label v-if="selectedNode.nodeType === 'WAIT_EVENT'">
            waitEventType
            <input
              data-testid="node-prop-wait-event"
              :value="selectedNode.waitEventType ?? ''"
              @change="
                patchSelectedNode({
                  waitEventType: blankToNull(($event.target as HTMLInputElement).value),
                })
              "
            />
          </label>
          <label v-if="selectedNode.nodeType === 'TIMER'">
            durationSeconds
            <input
              type="number"
              min="1"
              data-testid="node-prop-duration"
              :value="selectedNode.durationSeconds ?? ''"
              @change="
                patchSelectedNode({
                  durationSeconds: Number(($event.target as HTMLInputElement).value) || null,
                })
              "
            />
          </label>
          <label v-if="selectedNode.nodeType === 'SUB_PROCESS'">
            subProcessRef
            <input
              data-testid="node-prop-subprocess"
              :value="selectedNode.subProcessRef ?? ''"
              @change="
                patchSelectedNode({
                  subProcessRef: blankToNull(($event.target as HTMLInputElement).value),
                })
              "
            />
          </label>
          <ul v-if="selectedNodeErrors.length" class="errors" data-testid="node-validation-errors">
            <li v-for="(err, index) in selectedNodeErrors" :key="index">{{ err }}</li>
          </ul>
          <button
            type="button"
            data-testid="delete-node"
            :disabled="parsed?.startNodeId === selectedNode.nodeId"
            @click="deleteSelectedNode"
          >
            删除节点
          </button>
        </aside>

        <aside v-if="selectedEdge" class="edge-panel" data-testid="edge-editor">
          <h3>边 {{ selectedEdge.from }} → {{ selectedEdge.to }}</h3>
          <p v-if="selectedFromNode?.nodeType === 'EXCLUSIVE_GATEWAY'">
            EXCLUSIVE_GATEWAY 出边可编辑条件（SERVICEOS_EXPR_V1）。
          </p>
          <p v-else class="muted">非排他网关出边通常无条件；保存时将忽略条件。</p>
          <div
            v-if="selectedFromNode?.nodeType === 'EXCLUSIVE_GATEWAY'"
            data-testid="edge-condition-source-wrap"
          >
            <ConditionBuilder v-model="conditionDraft" label="边条件积木" />
            <textarea
              v-model="conditionDraft"
              rows="2"
              data-testid="edge-condition-source"
              class="sr-source"
            />
          </div>
          <label v-else>
            condition.source
            <textarea
              v-model="conditionDraft"
              rows="3"
              data-testid="edge-condition-source"
              disabled
            />
          </label>
          <div class="edge-actions">
            <button
              type="button"
              data-testid="save-edge-condition"
              :disabled="selectedFromNode?.nodeType !== 'EXCLUSIVE_GATEWAY'"
              @click="saveCondition"
            >
              保存条件
            </button>
            <button type="button" data-testid="delete-edge" @click="deleteSelectedEdge">
              删除边
            </button>
          </div>
        </aside>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.canvas-wrap {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  min-height: 22rem;
}
.canvas-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.75rem;
  flex-wrap: wrap;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: center;
}
.actions button.active {
  border-color: #0969da;
  background: #ddf4ff;
}
.palette {
  display: flex;
  gap: 0.35rem;
  align-items: center;
  font-size: 0.85rem;
}
.workspace {
  display: grid;
  grid-template-columns: 1fr minmax(14rem, 18rem);
  gap: 0.75rem;
}
.canvas {
  position: relative;
  height: 28rem;
  border: 1px solid #d0d7de;
  border-radius: 0.5rem;
  background:
    linear-gradient(#f6f8fa 1px, transparent 1px) 0 0 / 20px 20px,
    linear-gradient(90deg, #f6f8fa 1px, transparent 1px) 0 0 / 20px 20px,
    #fff;
  overflow: hidden;
  touch-action: none;
}
.canvas.connecting {
  outline: 2px dashed #0969da;
}
.edges {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}
.edge-hit {
  cursor: pointer;
}
.node {
  position: absolute;
  box-sizing: border-box;
  border: 1px solid #0969da;
  border-radius: 0.5rem;
  background: #ffffffee;
  padding: 0.45rem 0.55rem;
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  cursor: grab;
  user-select: none;
  box-shadow: 0 1px 2px rgb(0 0 0 / 8%);
}
.node.dragging {
  cursor: grabbing;
  border-color: #0550ae;
  box-shadow: 0 4px 12px rgb(9 105 218 / 25%);
  z-index: 2;
}
.node.connect-source,
.node.selected {
  outline: 2px solid #0969da;
}
.node.invalid {
  border-color: #cf222e;
  background: #fff5f5;
}
.node.type-START {
  border-color: #1a7f37;
}
.node.type-END {
  border-color: #cf222e;
}
.node.type-EXCLUSIVE_GATEWAY,
.node.type-PARALLEL_GATEWAY {
  border-color: #9a6700;
}
.node span {
  font-size: 0.75rem;
  color: #656d76;
}
.side-panels {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
.minimap,
.node-panel,
.edge-panel {
  border: 1px solid #d0d7de;
  border-radius: 0.5rem;
  padding: 0.75rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  background: #f6f8fa;
}
.minimap-surface {
  position: relative;
  height: 6rem;
  background: #fff;
  border: 1px solid #d0d7de;
  border-radius: 0.25rem;
  overflow: hidden;
}
.minimap-node {
  position: absolute;
  background: #0969da88;
  border-radius: 2px;
  min-width: 4px;
  min-height: 4px;
}
.minimap-node.invalid {
  background: #cf222eaa;
}
.node-panel label,
.edge-panel label {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  font-size: 0.8rem;
}
.node-panel input,
.edge-panel textarea {
  font: inherit;
  width: 100%;
}
.edge-panel textarea,
.sr-source {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.75rem;
}
.edge-actions {
  display: flex;
  gap: 0.5rem;
}
.errors {
  margin: 0;
  padding-left: 1.1rem;
  color: #cf222e;
  font-size: 0.8rem;
}
.muted {
  color: #656d76;
}
@media (max-width: 960px) {
  .workspace {
    grid-template-columns: 1fr;
  }
}
</style>
