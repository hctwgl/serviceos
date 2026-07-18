<script setup lang="ts">
import { computed, ref } from 'vue'

export type WorkflowNode = {
  nodeId: string
  nodeType: string
  name?: string
  stageCode?: string | null
  taskType?: string | null
}

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
}>()

const NODE_W = 160
const NODE_H = 72
const surfaceRef = ref<HTMLElement | null>(null)
const draggingId = ref<string | null>(null)
const dragOffset = ref({ x: 0, y: 0 })
const mode = ref<'move' | 'connect'>('move')
const connectFrom = ref<string | null>(null)
const selectedEdgeKey = ref<string | null>(null)
const conditionDraft = ref('')

type ParsedWorkflow = {
  nodes: WorkflowNode[]
  transitions: WorkflowTransition[]
  layout: Record<string, NodeLayout>
  raw: Record<string, unknown>
}

const parsed = computed<ParsedWorkflow | null>(() => {
  try {
    const raw = JSON.parse(props.definitionJson) as Record<string, unknown>
    const nodes = (Array.isArray(raw.nodes) ? raw.nodes : []) as WorkflowNode[]
    const transitions = (Array.isArray(raw.transitions) ? raw.transitions : []) as WorkflowTransition[]
    const metadata = (raw.metadata && typeof raw.metadata === 'object'
      ? (raw.metadata as Record<string, unknown>)
      : {}) as Record<string, unknown>
    const layoutSource = (metadata.layout && typeof metadata.layout === 'object'
      ? (metadata.layout as Record<string, NodeLayout>)
      : {}) as Record<string, NodeLayout>
    const layout: Record<string, NodeLayout> = { ...layoutSource }
    nodes.forEach((node, index) => {
      if (!layout[node.nodeId]) {
        layout[node.nodeId] = { x: 40 + (index % 3) * 200, y: 40 + Math.floor(index / 3) * 120 }
      }
    })
    return { nodes, transitions, layout, raw }
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

function emitWorkflow(patch: {
  layout?: Record<string, NodeLayout>
  transitions?: WorkflowTransition[]
}) {
  if (!parsed.value) return
  const next = {
    ...parsed.value.raw,
    transitions: patch.transitions ?? parsed.value.transitions,
    metadata: {
      ...((parsed.value.raw.metadata as Record<string, unknown> | undefined) ?? {}),
      layout: patch.layout ?? parsed.value.layout,
    },
  }
  emit('update:definitionJson', JSON.stringify(next, null, 2))
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
  const bounds = surfaceRef.value.getBoundingClientRect()
  draggingId.value = nodeId
  selectedEdgeKey.value = null
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
  emitWorkflow({
    layout: {
      ...parsed.value.layout,
      [draggingId.value]: { x: Math.round(nextX), y: Math.round(nextY) },
    },
  })
}

function onPointerUp() {
  draggingId.value = null
}

function addEdge(from: string, to: string) {
  if (!parsed.value) return
  const exists = parsed.value.transitions.some((edge) => edge.from === from && edge.to === to)
  if (exists) return
  const transitionId = `t_${from}_${to}_${Date.now().toString(36)}`
  const next = [
    ...parsed.value.transitions,
    { transitionId, from, to },
  ]
  emitWorkflow({ transitions: next })
  selectedEdgeKey.value = transitionId
  conditionDraft.value = ''
}

function selectEdge(key: string, index: number) {
  selectedEdgeKey.value = key
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
    layout[node.nodeId] = { x: 40 + (index % 3) * 200, y: 40 + Math.floor(index / 3) * 120 }
  })
  emitWorkflow({ layout })
}

function startConnect() {
  mode.value = 'connect'
  connectFrom.value = null
  selectedEdgeKey.value = null
  draggingId.value = null
}

function cancelConnect() {
  mode.value = 'move'
  connectFrom.value = null
}
</script>

<template>
  <section class="canvas-wrap" data-testid="workflow-canvas">
    <header class="canvas-toolbar">
      <strong>Workflow 画布</strong>
      <div class="actions">
        <button
          type="button"
          data-testid="connect-mode"
          :class="{ active: mode === 'connect' }"
          @click="mode === 'connect' ? cancelConnect() : startConnect()"
        >
          {{ mode === 'connect' ? (connectFrom ? `选择目标（已选 ${connectFrom}）` : '连接：选择源节点') : '连接边' }}
        </button>
        <button type="button" data-testid="auto-layout" @click="autoLayout">自动排布</button>
      </div>
    </header>

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
          <marker id="arrow-active" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
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

    <aside v-if="selectedEdge" class="edge-panel" data-testid="edge-editor">
      <h3>边 {{ selectedEdge.from }} → {{ selectedEdge.to }}</h3>
      <p v-if="selectedFromNode?.nodeType === 'EXCLUSIVE_GATEWAY'">
        EXCLUSIVE_GATEWAY 出边可编辑条件（SERVICEOS_EXPR_V1）。
      </p>
      <p v-else class="muted">非排他网关出边通常无条件；保存时将忽略条件。</p>
      <label>
        condition.source
        <textarea
          v-model="conditionDraft"
          rows="3"
          data-testid="edge-condition-source"
          :disabled="selectedFromNode?.nodeType !== 'EXCLUSIVE_GATEWAY'"
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
        <button type="button" data-testid="delete-edge" @click="deleteSelectedEdge">删除边</button>
      </div>
    </aside>
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
}
.actions {
  display: flex;
  gap: 0.5rem;
}
.actions button.active {
  border-color: #0969da;
  background: #ddf4ff;
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
.node.connect-source {
  outline: 2px solid #0969da;
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
.edge-panel {
  border: 1px solid #d0d7de;
  border-radius: 0.5rem;
  padding: 0.75rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.edge-panel textarea {
  width: 100%;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.8rem;
}
.edge-actions {
  display: flex;
  gap: 0.5rem;
}
.muted {
  color: #656d76;
}
</style>
