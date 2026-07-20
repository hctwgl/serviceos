<script setup lang="ts">
import { computed } from 'vue'
import { Alert, Empty, Table, Tag } from 'ant-design-vue'

/**
 * M385 过渡展示适配器。
 *
 * 当前服务端 compile-preview 仍只返回 manifestJson。这里集中完成一次只读转换，
 * 避免多个页面各自解析和直接暴露 JSON。M385 的产品化 Preview DTO 合入后，
 * 本组件必须删除 parseManifest 分支，改为只接收服务端结构化 rows。
 */
const props = defineProps<{
  manifestJson?: string | null
  loading?: boolean
}>()

type RunbookRow = {
  key: string
  sequence: number
  stageName: string
  ownerLabel: string
  formsLabel: string
  evidenceLabel: string
  actionLabels: string[]
  nextStageLabel: string
  exceptionLabel: string
  slaLabel: string
}

type StageDocument = {
  stageCode?: string
  stageName?: string
  sequence?: number
  ownerType?: string
  formRefs?: unknown[]
  evidenceRefs?: unknown[]
  actions?: Array<{ actionLabel?: string }>
  transitions?: Array<{ targetStage?: string }>
  exceptionPaths?: unknown[]
  slaRef?: unknown
}

function ownerLabel(ownerType?: string): string {
  if (ownerType === 'PLATFORM') return '平台运营'
  if (ownerType === 'NETWORK') return '合作网点'
  if (ownerType === 'TECHNICIAN') return '服务师傅'
  return '责任方待配置'
}

function countLabel(items: unknown[] | undefined, emptyLabel = '未配置'): string {
  const count = items?.length ?? 0
  return count > 0 ? `${count} 项` : emptyLabel
}

const parseError = computed(() => {
  if (!props.manifestJson) return null
  try {
    JSON.parse(props.manifestJson)
    return null
  } catch {
    return '运行说明数据暂时无法解析，请返回配置页重新校验。'
  }
})

const rows = computed<RunbookRow[]>(() => {
  if (!props.manifestJson || parseError.value) return []
  const document = JSON.parse(props.manifestJson) as { stages?: StageDocument[] }
  const stages = [...(document.stages ?? [])].sort(
    (left, right) => Number(left.sequence ?? 0) - Number(right.sequence ?? 0),
  )
  const stageNames = new Map(
    stages.map((stage) => [String(stage.stageCode ?? ''), String(stage.stageName ?? '未命名阶段')]),
  )

  return stages.map((stage, index) => {
    const stageCode = String(stage.stageCode ?? `stage-${index + 1}`)
    const nextStageCode = stage.transitions?.[0]?.targetStage
    return {
      key: stageCode,
      sequence: Number(stage.sequence ?? index + 1),
      stageName: String(stage.stageName ?? '未命名阶段'),
      ownerLabel: ownerLabel(stage.ownerType),
      formsLabel: countLabel(stage.formRefs),
      evidenceLabel: countLabel(stage.evidenceRefs),
      actionLabels: (stage.actions ?? [])
        .map((action) => action.actionLabel?.trim())
        .filter((label): label is string => !!label),
      nextStageLabel: nextStageCode
        ? (stageNames.get(nextStageCode) ?? '下一阶段待确认')
        : '流程结束或待配置',
      exceptionLabel: countLabel(stage.exceptionPaths, '无单独异常路径'),
      slaLabel: stage.slaRef ? '已绑定' : '未绑定',
    }
  })
})

const columns = [
  { title: '顺序', dataIndex: 'sequence', key: 'sequence', width: 72 },
  { title: '阶段', dataIndex: 'stageName', key: 'stageName', width: 150 },
  { title: '责任方', dataIndex: 'ownerLabel', key: 'ownerLabel', width: 120 },
  { title: '表单', dataIndex: 'formsLabel', key: 'formsLabel', width: 100 },
  { title: '必传资料', dataIndex: 'evidenceLabel', key: 'evidenceLabel', width: 110 },
  { title: '可执行动作', key: 'actions', minWidth: 180 },
  { title: '正常下一阶段', dataIndex: 'nextStageLabel', key: 'nextStageLabel', width: 150 },
  { title: '异常路径', dataIndex: 'exceptionLabel', key: 'exceptionLabel', width: 130 },
  { title: 'SLA', dataIndex: 'slaLabel', key: 'slaLabel', width: 100 },
]
</script>

<template>
  <Alert v-if="parseError" type="error" show-icon :message="parseError" />
  <Table
    v-else-if="rows.length"
    size="middle"
    row-key="key"
    :loading="loading"
    :pagination="false"
    :data-source="rows"
    :columns="columns"
    :scroll="{ x: 1180 }"
  >
    <template #bodyCell="{ column, record }">
      <template v-if="column.key === 'actions'">
        <span v-if="record.actionLabels.length === 0">未配置</span>
        <Tag v-for="label in record.actionLabels" v-else :key="label">{{ label }}</Tag>
      </template>
    </template>
  </Table>
  <Empty v-else-if="!loading" description="当前草稿还没有可预览的流程阶段" />
</template>
