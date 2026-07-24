<script setup lang="ts">
import { DownOutlined } from '@serviceos/design-system'
import type { WorkflowCanvasStage } from './types'
import WorkflowNode from './WorkflowNode.vue'

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
</script>

<template>
  <section class="sos-workflow-designer" aria-label="履约方案流程设计器">
    <header class="sos-workflow-designer__toolbar">
      <div>
        <span class="sos-eyebrow">WORKFLOW DESIGNER</span>
        <strong>履约流程</strong>
        <small>{{ readonly ? '只读预览 · 节点随方案版本冻结' : '活动草稿 · 修改后需要重新校验' }}</small>
      </div>
      <div class="sos-workflow-designer__legend">
        <span><i class="is-completed" />已配置</span>
        <span><i class="is-current" />当前节点</span>
        <span><i class="is-pending" />待配置</span>
      </div>
    </header>

    <div v-if="!stages.length" class="sos-inline-unavailable">
      <strong>流程画布暂时没有阶段</strong>
      <span>当前方案未返回可展示的履约阶段，请进入活动草稿继续配置。</span>
    </div>
    <div v-else class="sos-workflow-designer__canvas">
      <div class="sos-workflow-designer__terminal is-start"><span>START</span><strong>开始</strong></div>
      <DownOutlined class="sos-workflow-designer__connector" />
      <ol>
        <li v-for="stage in stages" :key="stage.code">
          <WorkflowNode
            :stage="stage"
            :selected="stage.code === props.selectedCode"
            @select="emit('select', $event)"
          />
          <DownOutlined v-if="stage.code !== stages.at(-1)?.code" class="sos-workflow-designer__connector" />
        </li>
      </ol>
      <DownOutlined class="sos-workflow-designer__connector" />
      <div class="sos-workflow-designer__terminal is-finish"><span>END</span><strong>完成</strong></div>
    </div>
  </section>
</template>
