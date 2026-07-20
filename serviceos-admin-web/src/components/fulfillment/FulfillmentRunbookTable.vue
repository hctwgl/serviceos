<script setup lang="ts">
import { computed } from 'vue'
import { Empty, Table, Tag } from 'ant-design-vue'
import type { ProjectFulfillmentRunbook } from '@serviceos/core-client'

/**
 * 渲染服务端产品化运行说明书。禁止接收或解析底层编译 JSON。
 */
const props = defineProps<{
  runbook?: ProjectFulfillmentRunbook | null
  loading?: boolean
}>()

const rows = computed(() =>
  (props.runbook?.stages ?? []).map((stage, index) => ({
    key: `${stage.sequence}-${stage.stageName}-${index}`,
    sequence: stage.sequence,
    stageName: stage.stageName,
    ownerTypeLabel: stage.ownerTypeLabel,
    taskTypeLabel: stage.taskTypeLabel || '—',
    formSummary: stage.formSummary || '未配置表单',
    evidenceSummary: stage.evidenceSummary || '未配置资料',
    actionSummary: stage.actionSummary || '未配置动作',
    nextStageSummary: stage.nextStageSummary || '—',
    exceptionSummary: stage.exceptionSummary || '—',
    slaSummary: stage.slaSummary || '未绑定 SLA',
    terminal: stage.terminal ? '结束' : '进行中',
  })),
)

const columns = [
  { title: '顺序', dataIndex: 'sequence', key: 'sequence', width: 72 },
  { title: '阶段', dataIndex: 'stageName', key: 'stageName', width: 150 },
  { title: '责任方', dataIndex: 'ownerTypeLabel', key: 'ownerTypeLabel', width: 100 },
  { title: '任务类型', dataIndex: 'taskTypeLabel', key: 'taskTypeLabel', width: 120 },
  { title: '表单', dataIndex: 'formSummary', key: 'formSummary', width: 120 },
  { title: '必传资料', dataIndex: 'evidenceSummary', key: 'evidenceSummary', width: 130 },
  { title: '允许动作', dataIndex: 'actionSummary', key: 'actionSummary', width: 120 },
  { title: '下一阶段', dataIndex: 'nextStageSummary', key: 'nextStageSummary', width: 130 },
  { title: '异常路径', dataIndex: 'exceptionSummary', key: 'exceptionSummary', width: 130 },
  { title: 'SLA', dataIndex: 'slaSummary', key: 'slaSummary', width: 120 },
  { title: '类型', dataIndex: 'terminal', key: 'terminal', width: 90 },
]
</script>

<template>
  <div data-testid="fulfillment-runbook-table">
    <div v-if="runbook" class="runbook-meta">
      <Tag color="blue">{{ runbook.serviceProductLabel }}</Tag>
      <span>{{ runbook.profileName }}</span>
      <span v-if="runbook.clientSupportSummary" class="muted">{{ runbook.clientSupportSummary }}</span>
    </div>
    <Table
      v-if="rows.length"
      size="middle"
      row-key="key"
      :loading="loading"
      :pagination="false"
      :data-source="rows"
      :columns="columns"
      :scroll="{ x: 1280 }"
    />
    <Empty v-else-if="!loading" description="当前草稿还没有可预览的流程阶段" />
  </div>
</template>

<style scoped>
.runbook-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
  color: var(--sos-color-text-primary);
}
.muted {
  color: var(--sos-color-text-secondary);
  font-size: 13px;
}
</style>
