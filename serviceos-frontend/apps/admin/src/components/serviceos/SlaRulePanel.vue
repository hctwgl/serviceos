<script setup lang="ts">
import { Tag } from '@serviceos/design-system'
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

const selectedId = ref<string>()
const selectedRule = computed(() => props.rules.find((rule) => rule.id === selectedId.value))

watch(
  () => props.rules,
  (rules) => {
    if (!rules.some((rule) => rule.id === selectedId.value)) selectedId.value = rules[0]?.id
  },
  { immediate: true },
)
</script>

<template>
  <section class="sos-sla-rule-panel">
    <header class="sos-section-heading">
      <div>
        <span class="sos-eyebrow">SLA 规则</span>
        <h3>任务时效</h3>
        <span>按任务查看目标时间、预警和升级边界；未返回的规则保持待配置。</span>
      </div>
      <Tag color="processing">{{ rules.length }} 个任务</Tag>
    </header>

    <div v-if="!rules.length" class="sos-inline-unavailable">
      <strong>当前没有可展示的 SLA 规则</strong>
      <span>请先在流程阶段绑定 SLA，或进入活动草稿维护规则引用。</span>
    </div>
    <div v-else class="sos-sla-rule-panel__workspace">
      <div class="sos-sla-rule-panel__table-wrap">
        <table class="sos-sla-rule-panel__table">
          <thead><tr><th>任务</th><th>目标时间</th><th>预警</th><th>升级</th></tr></thead>
          <tbody>
            <tr v-for="rule in rules" :key="rule.id" :class="{ active: rule.id === selectedId }" @click="selectedId = rule.id">
              <td><strong>{{ rule.task }}</strong><small>起算：{{ rule.basis }}</small></td>
              <td>{{ rule.target }}</td>
              <td>{{ rule.warning }}</td>
              <td>{{ rule.escalation }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <aside v-if="selectedRule" class="sos-sla-rule-panel__inspector">
        <header><span>规则属性</span><strong>{{ selectedRule.task }}</strong></header>
        <dl>
          <div><dt>起算点</dt><dd>{{ selectedRule.basis }}</dd></div>
          <div><dt>目标时间</dt><dd>{{ selectedRule.target }}</dd></div>
          <div><dt>预警</dt><dd>{{ selectedRule.warning }}</dd></div>
          <div><dt>升级</dt><dd>{{ selectedRule.escalation }}</dd></div>
        </dl>
        <p class="sos-editor-note">{{ editable ? '修改请进入活动草稿，保存后重新校验。' : '当前接口只返回 SLA 摘要，未返回目标、预警、升级的独立配置值。' }}</p>
      </aside>
    </div>
  </section>
</template>
