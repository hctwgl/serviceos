<script setup lang="ts">
import type { ProjectFulfillmentStageDraft } from '@serviceos/api-client'

import {
  Input,
  Select,
  Tag,
} from '@serviceos/design-system'
import { computed, ref, watch } from 'vue'

const props = defineProps<{
  stages: ProjectFulfillmentStageDraft[]
}>()
const emit = defineEmits<{
  'update:stages': [value: ProjectFulfillmentStageDraft[]]
}>()

const selectedStageCode = ref<string>()

const ownerOptions = [
  { value: 'PLATFORM', label: '平台运营' },
  { value: 'NETWORK', label: '责任网点' },
  { value: 'TECHNICIAN', label: '责任师傅' },
  { value: 'SYSTEM', label: '系统自动执行' },
]

const orderedStages = computed(() => (
  [...props.stages].sort((left, right) => left.sequence - right.sequence)
))
const selectedStage = computed(() => (
  props.stages.find((stage) => stage.stageCode === selectedStageCode.value)
))

watch(
  orderedStages,
  (stages) => {
    if (!stages.length) {
      selectedStageCode.value = undefined
      return
    }
    if (!stages.some((stage) => stage.stageCode === selectedStageCode.value)) {
      selectedStageCode.value = stages[0]?.stageCode
    }
  },
  { immediate: true },
)

function ownerLabel(ownerType: ProjectFulfillmentStageDraft['ownerType']) {
  return ownerOptions.find((option) => option.value === ownerType)?.label ?? '责任未明确'
}

function taskTypeLabel(taskType: string | null) {
  const labels: Record<string, string> = {
    ASSIGN_COORDINATORS: '责任分配',
    DISPATCH: '派单协调',
    FIELD_INSTALL: '现场安装',
    FIELD_SURVEY: '现场勘测',
    INSTALL: '现场安装',
    REVIEW: '资料审核',
    SURVEY: '现场勘测',
  }
  return taskType ? labels[taskType] ?? '业务任务' : '等待业务事件'
}

function stageTypeLabel(stage: ProjectFulfillmentStageDraft) {
  if (stage.terminal) return '完成阶段'
  const labels: Record<string, string> = {
    REVIEW_TASK: '审核任务',
    USER_TASK: '人工任务',
    WAIT_EVENT: '等待事件',
  }
  return stage.stageType ? labels[stage.stageType] ?? '履约阶段' : '履约阶段'
}

function patchSelected(patch: Partial<ProjectFulfillmentStageDraft>) {
  if (!selectedStage.value) return
  emit('update:stages', props.stages.map((stage) => (
    stage.stageCode === selectedStage.value?.stageCode
      ? { ...stage, ...patch }
      : stage
  )))
}

function updateOwner(value: unknown) {
  if (
    typeof value !== 'string'
    || !ownerOptions.some((option) => option.value === value)
  ) return
  patchSelected({
    ownerType: value as ProjectFulfillmentStageDraft['ownerType'],
  })
}
</script>

<template>
  <section class="fulfillment-stage-designer">
    <header class="stage-designer-heading">
      <div>
        <p>服务流程</p>
        <h2>履约阶段与任务责任</h2>
        <span>阶段必须与绑定的运行流程保持一致；发布校验会阻止缺失或多出的阶段。</span>
      </div>
      <Tag color="processing">{{ orderedStages.length }} 个运行阶段</Tag>
    </header>

    <div v-if="orderedStages.length" class="stage-designer-workspace">
      <div class="stage-flow-canvas" role="list" aria-label="履约阶段">
        <div class="stage-flow-list">
          <template v-for="(stage, index) in orderedStages" :key="stage.stageCode">
            <button
              type="button"
              role="listitem"
              class="stage-flow-node"
              :class="{ active: stage.stageCode === selectedStageCode }"
              :aria-current="stage.stageCode === selectedStageCode ? 'step' : undefined"
              @click="selectedStageCode = stage.stageCode"
            >
              <span class="stage-flow-sequence">{{ String(stage.sequence).padStart(2, '0') }}</span>
              <span class="stage-flow-copy">
                <strong>{{ stage.stageName }}</strong>
                <small>{{ taskTypeLabel(stage.taskType) }} · {{ ownerLabel(stage.ownerType) }}</small>
              </span>
              <Tag :color="stage.terminal ? 'success' : 'default'">
                {{ stageTypeLabel(stage) }}
              </Tag>
            </button>
            <span
              v-if="index < orderedStages.length - 1"
              class="stage-flow-connector"
              aria-hidden="true"
            />
          </template>
        </div>
      </div>

      <aside v-if="selectedStage" class="stage-node-inspector">
        <header>
          <div>
            <span>当前阶段</span>
            <h3>{{ selectedStage.stageName }}</h3>
          </div>
          <Tag v-if="selectedStage.terminal" color="success">履约出口</Tag>
        </header>

        <div class="stage-inspector-form">
          <label>
            <span>阶段名称</span>
            <Input
              :value="selectedStage.stageName"
              :maxlength="120"
              @update:value="(value) => patchSelected({ stageName: String(value) })"
            />
          </label>
          <label>
            <span>业务责任</span>
            <Select
              :value="selectedStage.ownerType"
              :options="ownerOptions"
              @update:value="updateOwner"
            />
          </label>
          <label>
            <span>执行说明</span>
            <Input.TextArea
              :value="selectedStage.description ?? ''"
              :rows="3"
              :maxlength="500"
              placeholder="说明完成标准、协作边界和业务结果"
              @update:value="(value) => patchSelected({ description: String(value) })"
            />
          </label>
        </div>

        <section class="stage-binding-summary">
          <header><strong>运行要求</strong><span>随方案版本原子冻结</span></header>
          <dl>
            <div>
              <dt>任务类型</dt>
              <dd>{{ taskTypeLabel(selectedStage.taskType) }}</dd>
            </div>
            <div>
              <dt>时效规则</dt>
              <dd>{{ selectedStage.slaRef ? '已绑定 SLA' : '未绑定 SLA' }}</dd>
            </div>
            <div>
              <dt>业务表单</dt>
              <dd>{{ selectedStage.formRefs?.length ?? 0 }} 份</dd>
            </div>
            <div>
              <dt>资料要求</dt>
              <dd>{{ selectedStage.evidenceRefs?.length ?? 0 }} 项</dd>
            </div>
          </dl>
          <div v-if="selectedStage.formRefs?.length" class="stage-reference-list">
            <span>已关联业务表单</span>
            <Tag v-for="(_, index) in selectedStage.formRefs" :key="index">
              表单 {{ index + 1 }}
            </Tag>
          </div>
          <div v-if="selectedStage.evidenceRefs?.length" class="stage-reference-list">
            <span>已关联资料要求</span>
            <Tag v-for="(_, index) in selectedStage.evidenceRefs" :key="index">
              资料规范 {{ index + 1 }}
            </Tag>
          </div>
        </section>
      </aside>
    </div>

    <div v-else class="stage-designer-empty">
      <strong>尚未配置履约流程</strong>
      <span>请先从已发布模板建立运行流程，再回到这里完善任务责任与业务说明。</span>
    </div>
  </section>
</template>
