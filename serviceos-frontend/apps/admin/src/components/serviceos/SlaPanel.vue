<script setup lang="ts">
import { Input, Select, Tag } from '@serviceos/design-system'
import { computed, ref, watch } from 'vue'

export type SlaRuleItem = {
  id: string
  task: string
  target: string
  warning: string
  escalation: string
  basis: string
}

const props = withDefaults(defineProps<{
  rules: SlaRuleItem[]
  editable?: boolean
}>(), {
  editable: false,
})

const emit = defineEmits<{
  'update:rules': [rules: SlaRuleItem[]]
}>()

const selectedId = ref<string>()
const selectedRule = computed(() => props.rules.find((rule) => rule.id === selectedId.value))

watch(
  () => props.rules,
  (rules) => {
    if (!rules.some((rule) => rule.id === selectedId.value)) selectedId.value = rules[0]?.id
  },
  { immediate: true },
)

function patchSelected(patch: Partial<SlaRuleItem>) {
  if (!selectedRule.value) return
  emit('update:rules', props.rules.map((rule) => (
    rule.id === selectedRule.value?.id ? { ...rule, ...patch } : rule
  )))
}

const basisOptions = [
  { value: '工单受理', label: '工单受理' },
  { value: '预约确认', label: '预约确认' },
  { value: '客户响应', label: '客户响应' },
]
</script>

<template>
  <section class="sos-sla-panel">
    <header class="sos-section-heading">
      <div>
        <span class="sos-eyebrow">SLA RULES</span>
        <h3>SLA 设计</h3>
        <span>以任务为单位定义目标、预警与升级规则，所有时间承诺随方案版本发布。</span>
      </div>
      <Tag color="processing">{{ rules.length }} 条规则</Tag>
    </header>
    <div class="sos-sla-panel__workspace">
      <div class="sos-sla-panel__table-wrap">
        <table class="sos-sla-panel__table">
          <thead><tr><th>任务</th><th>目标时间</th><th>预警时间</th><th>升级规则</th></tr></thead>
          <tbody>
            <tr v-for="rule in rules" :key="rule.id" :class="{ active: rule.id === selectedId }" @click="selectedId = rule.id">
              <td><strong>{{ rule.task }}</strong><small>起算：{{ rule.basis }}</small></td>
              <td>{{ rule.target }}</td>
              <td>{{ rule.warning }}</td>
              <td>{{ rule.escalation }}</td>
            </tr>
          </tbody>
        </table>
        <div v-if="!rules.length" class="sos-inline-empty"><strong>尚未配置 SLA</strong><span>流程节点配置后，在这里维护时效承诺。</span></div>
      </div>
      <aside v-if="selectedRule" class="sos-sla-panel__inspector">
        <header><span>规则属性</span><strong>{{ selectedRule.task }}</strong></header>
        <label><span>任务名称</span><Input :value="selectedRule.task" :disabled="!editable" @update:value="patchSelected({ task: String($event) })" /></label>
        <label><span>起算点</span><Select :value="selectedRule.basis" :options="basisOptions" :disabled="!editable" @update:value="patchSelected({ basis: String($event) })" /></label>
        <label><span>目标时间</span><Input :value="selectedRule.target" :disabled="!editable" @update:value="patchSelected({ target: String($event) })" /></label>
        <label><span>预警时间</span><Input :value="selectedRule.warning" :disabled="!editable" @update:value="patchSelected({ warning: String($event) })" /></label>
        <label><span>升级规则</span><Input.TextArea :value="selectedRule.escalation" :rows="3" :disabled="!editable" @update:value="patchSelected({ escalation: String($event) })" /></label>
        <p class="sos-editor-note">{{ editable ? '修改暂留在当前草稿编辑态，保存草稿时与流程和表单一起提交。' : '当前页面展示方案版本中的 SLA 摘要；正式运行规则仍以服务端发布快照为准。' }}</p>
      </aside>
    </div>
  </section>
</template>
