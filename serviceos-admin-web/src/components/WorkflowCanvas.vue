<script setup lang="ts">
import { computed, ref, watch } from 'vue'

export type WorkflowNode = {
  nodeId: string
  nodeType: string
  name?: string
  stageCode?: string | null
  taskType?: string | null
}

export type WorkflowTransition = {
  transitionId?: string
  from: string
  to: string
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

const edges = computed(() => {
  if (!parsed.value) return [] as Array<{ key: string; x1: number; y1: number; x2: number; y2: number }>
  const { transitions, layout } = parsed.value
  return transitions
    .map((edge, index) => {
      const from = layout[edge.from]
      const to = layout[edge.to]
      if (!from || !to) return null
      return {
        key: edge.transitionId ?? `${edge.from}->${edge.to}-${index}`,
        x1: from.x + NODE_W / 2,
        y1: from.y + NODE_H / 2,
        x2: to.x + NODE_W / 2,
        y2: to.y + NODE_H / 2,
      }
    })
    .filter((edge): edge is NonNullable<typeof edge> => edge != null)
})

function onPointerDown(event: PointerEvent, nodeId: string) {
  if (!parsed.value || !surfaceRef.value) return
  const layout = parsed.value.layout[nodeId]
  if (!layout) return
  const bounds = surfaceRef.value.getBoundingClientRect()
  draggingId.value = nodeId
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
  const nextLayout = {
    ...parsed.value.layout,
    [draggingId.value]: { x: Math.round(nextX), y: Math.round(nextY) },
  }
  emitDefinition(nextLayout)
}

function onPointerUp() {
  draggingId.value = null
}

function emitDefinition(layout: Record<string, NodeLayout>) {
  if (!parsed.value) return
  const next = {
    ...parsed.value.raw,
    metadata: {
      ...((parsed.value.raw.metadata as Record<string, unknown> | undefined) ?? {}),
      layout,
    },
  }
  emit('update:definitionJson', JSON.stringify(next, null, 2))
}

function autoLayout() {
  if (!parsed.value) return
  const layout: Record<string, NodeLayout> = {}
  parsed.value.nodes.forEach((node, index) => {
    layout[node.nodeId] = { x: 40 + (index % 3) * 200, y: 40 + Math.floor(index / 3) * 120 }
  })
  emitDefinition(layout)
}

watch(
  () => props.definitionJson,
  () => {
    /* parsed recomputes; keep drag state if same nodes */
  },
)
</script>

<template>
  <section class="canvas-wrap" data-testid="workflow-canvas">
    <header class="canvas-toolbar">
      <strong>Workflow 画布</strong>
      <button type="button" data-testid="auto-layout" @click="autoLayout">自动排布</button>
    </header>
    <div
      v-if="parsed"
      ref="surfaceRef"
      class="canvas"
      data-testid="workflow-canvas-surface"
      @pointermove="onPointerMove"
      @pointerup="onPointerUp"
      @pointerleave="onPointerUp"
    >
      <svg class="edges" aria-hidden="true">
        <defs>
          <marker id="arrow" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
            <path d="M0,0 L6,3 L0,6 Z" fill="#57606a" />
          </marker>
        </defs>
        <line
          v-for="edge in edges"
          :key="edge.key"
          :x1="edge.x1"
          :y1="edge.y1"
          :x2="edge.x2"
          :y2="edge.y2"
          stroke="#57606a"
          stroke-width="2"
          marker-end="url(#arrow)"
        />
      </svg>
      <article
        v-for="node in parsed.nodes"
        :key="node.nodeId"
        class="node"
        :class="[`type-${node.nodeType}`, { dragging: draggingId === node.nodeId }]"
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
.edges {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
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
.muted {
  color: #656d76;
}
</style>
