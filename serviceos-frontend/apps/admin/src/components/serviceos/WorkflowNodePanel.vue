<script setup lang="ts">
import { Tag } from '@serviceos/design-system'
import type { WorkflowCanvasStage } from './types'

withDefaults(defineProps<{
  stage?: WorkflowCanvasStage
  stageCount?: number
  activeSection?: string
}>(), {
  stage: undefined,
  stageCount: 0,
  activeSection: 'flow',
})
</script>

<template>
  <aside class="sos-workflow-node-panel" aria-label="节点属性">
    <header>
      <div><span class="sos-eyebrow">节点属性</span><h2>{{ stage?.name ?? '方案属性' }}</h2></div>
      <span v-if="stage" class="sos-workflow-node-panel__number">{{ String(stage.sequence).padStart(2, '0') }}</span>
    </header>

    <template v-if="stage">
      <div class="sos-workflow-node-panel__identity"><Tag color="processing">{{ stage.typeLabel }}</Tag><span>{{ stage.terminal ? '结束节点' : '流程节点' }}</span></div>
      <dl>
        <div><dt>责任角色</dt><dd>{{ stage.ownerLabel }}</dd></div>
        <div><dt>任务模板</dt><dd>{{ stage.taskLabel }}</dd></div>
        <div><dt>SLA</dt><dd>{{ stage.slaLabel }}</dd></div>
        <div><dt>关联表单</dt><dd>{{ stage.formCount }} 份</dd></div>
        <div><dt>证据要求</dt><dd>{{ stage.evidenceCount }} 项</dd></div>
      </dl>
      <p v-if="stage.description" class="sos-workflow-node-panel__description">{{ stage.description }}</p>
      <div class="sos-workflow-node-panel__hint"><strong>版本边界</strong><span>节点修改进入活动草稿，发布前必须重新校验。</span></div>
    </template>
    <template v-else>
      <p class="sos-workflow-node-panel__description">选择中间设计区域的节点，查看责任、任务、表单、证据和 SLA 引用。</p>
      <div class="sos-workflow-node-panel__overview"><span>当前设计区</span><strong>{{ activeSection === 'flow' ? '流程设计' : activeSection }}</strong><small>{{ stageCount }} 个阶段</small></div>
    </template>
  </aside>
</template>
