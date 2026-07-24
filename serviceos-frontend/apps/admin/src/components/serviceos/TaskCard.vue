<script setup lang="ts">
import type { WorkOrderWorkspaceTask } from '@serviceos/api-client'
import { ToolOutlined } from '@serviceos/design-system'

withDefaults(defineProps<{
  task: WorkOrderWorkspaceTask | null
  title: string
  stageName: string | null | undefined
  statusLabel?: string
  networkName?: string | null
  technicianName?: string | null
  address?: string | null
  terminal?: boolean
}>(), {
  statusLabel: '待处理',
  networkName: null,
  technicianName: null,
  address: null,
  terminal: false,
})
</script>

<template>
  <section class="sos-task-card">
    <header class="sos-task-card__header">
      <div class="sos-task-card__title">
        <span class="sos-task-card__icon"><ToolOutlined /></span>
        <div>
          <span class="sos-eyebrow">CURRENT TASK</span>
          <h2>{{ title }}</h2>
          <p>{{ stageName || '尚未进入流程阶段' }} · {{ terminal ? '履约已结束' : statusLabel }}</p>
        </div>
      </div>
      <slot name="status" />
    </header>
    <div class="sos-task-card__body">
      <dl class="sos-task-facts">
        <div><dt>责任网点</dt><dd>{{ networkName || (terminal ? '已释放' : '待分配') }}</dd></div>
        <div><dt>责任师傅</dt><dd>{{ technicianName || (terminal ? '已释放' : '待分配') }}</dd></div>
        <div><dt>客户地址</dt><dd>{{ address || '未提供' }}</dd></div>
        <div><dt>任务版本</dt><dd>{{ task?.version ?? '—' }}</dd></div>
      </dl>
      <aside class="sos-task-card__actions">
        <span>允许操作</span>
        <slot name="actions" />
      </aside>
    </div>
  </section>
</template>
