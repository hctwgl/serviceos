<script setup lang="ts">
import { CheckOutlined, RightOutlined } from '@serviceos/design-system'
import type { WorkflowCanvasStage } from './types'

const props = withDefaults(defineProps<{
  stages: WorkflowCanvasStage[]
  selectedCode?: string
  readonly?: boolean
}>(), {
  selectedCode: undefined,
  readonly: true,
})

const emit = defineEmits<{
  select: [stage: WorkflowCanvasStage]
}>()

function select(stage: WorkflowCanvasStage) {
  emit('select', stage)
}
</script>

<template>
  <section class="sos-workflow-canvas" aria-label="履约流程画布">
    <header class="sos-workflow-canvas__toolbar">
      <div><span class="sos-eyebrow">FULFILLMENT BLUEPRINT</span><strong>流程设计</strong><small>{{ readonly ? '只读预览 · 配置随方案版本整体发布' : '编辑草稿 · 保存后重新校验' }}</small></div>
      <span class="sos-canvas-legend"><i class="is-completed" />已完成 <i class="is-current" />当前 <i class="is-pending" />未开始</span>
    </header>
    <div v-if="!stages.length" class="sos-workflow-canvas__empty">
      <strong>流程画布暂时没有阶段</strong>
      <span>当前方案未返回可展示的履约阶段，请进入草稿或等待运行资产绑定。</span>
    </div>
    <div v-else class="sos-workflow-canvas__scroll">
      <div class="sos-workflow-canvas__start"><span>START</span><strong>开始</strong></div>
      <RightOutlined class="sos-workflow-canvas__arrow" />
      <ol class="sos-workflow-nodes">
        <li v-for="(stage, index) in stages" :key="stage.code">
          <button
            type="button"
            class="sos-workflow-node"
            :class="[
              `sos-workflow-node--${stage.status ?? 'pending'}`,
              { 'is-selected': stage.code === props.selectedCode },
            ]"
            :aria-pressed="stage.code === props.selectedCode"
            @click="select(stage)"
          >
            <span class="sos-workflow-node__index">
              <CheckOutlined v-if="stage.status === 'completed'" />
              <span v-else>{{ String(index + 1).padStart(2, '0') }}</span>
            </span>
            <span class="sos-workflow-node__copy"><strong>{{ stage.name }}</strong><small>{{ stage.taskLabel }} · {{ stage.ownerLabel }}</small></span>
            <span class="sos-workflow-node__meta">{{ stage.typeLabel }}</span>
          </button>
          <RightOutlined v-if="index < stages.length - 1" class="sos-workflow-node__arrow" />
        </li>
      </ol>
      <RightOutlined class="sos-workflow-canvas__arrow" />
      <div class="sos-workflow-canvas__finish"><span>END</span><strong>完成</strong></div>
    </div>
  </section>
</template>
