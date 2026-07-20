<script setup lang="ts">
import { Empty, List, Tag } from 'ant-design-vue'
import type { ProjectFulfillmentCompareImpact } from '@serviceos/core-client'

defineProps<{
  impact?: ProjectFulfillmentCompareImpact | null
  loading?: boolean
}>()

function changeTypeLabel(changeType: string) {
  if (changeType === 'ADDED') return '新增'
  if (changeType === 'REMOVED') return '移除'
  return '变更'
}

function categoryLabel(category: string) {
  const map: Record<string, string> = {
    STAGE: '阶段',
    TASK: '任务',
    FORM: '表单',
    EVIDENCE: '资料',
    ACTION: '动作',
    SLA: 'SLA',
    DISPATCH: '派单',
    NOTIFICATION: '通知',
    OTHER: '其他',
  }
  return map[category] || category
}
</script>

<template>
  <div data-testid="fulfillment-compare-impact" class="compare-panel">
    <template v-if="impact">
      <p class="baseline">
        对比基线：
        <strong v-if="impact.baselineKind === 'PUBLISHED'">
          已发布版本 {{ impact.baselineVersionLabel || '' }}
        </strong>
        <strong v-else>尚无已发布版本</strong>
      </p>
      <section>
        <h3>影响范围</h3>
        <ul>
          <li>{{ impact.impact.newWorkOrdersScope }}</li>
          <li>{{ impact.impact.existingWorkOrdersScope }}</li>
          <li v-if="impact.impact.effectiveFromHint">{{ impact.impact.effectiveFromHint }}</li>
        </ul>
      </section>
      <section>
        <h3>真实差异（{{ impact.changeCount }}）</h3>
        <List
          v-if="impact.changes.length"
          size="small"
          bordered
          :data-source="impact.changes"
        >
          <template #renderItem="{ item }">
            <List.Item>
              <div class="change-row">
                <Tag>{{ categoryLabel(item.category) }}</Tag>
                <Tag color="processing">{{ changeTypeLabel(item.changeType) }}</Tag>
                <span>{{ item.summary }}</span>
              </div>
              <div v-if="item.detail" class="detail">{{ item.detail }}</div>
            </List.Item>
          </template>
        </List>
        <Empty v-else description="相对当前发布版没有检测到配置差异" />
      </section>
      <section v-if="impact.risks.length">
        <h3>风险提示</h3>
        <ul>
          <li v-for="(risk, index) in impact.risks" :key="index">{{ risk }}</li>
        </ul>
      </section>
    </template>
    <Empty v-else-if="!loading" description="暂无差异分析结果" />
  </div>
</template>

<style scoped>
.compare-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.baseline {
  margin: 0;
  color: var(--sos-color-text-secondary);
}
h3 {
  margin: 0 0 8px;
  font-size: 14px;
  color: var(--sos-color-text-primary);
}
ul {
  margin: 0;
  padding-left: 18px;
  color: var(--sos-color-text-secondary);
}
.change-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.detail {
  margin-top: 4px;
  font-size: 12px;
  color: var(--sos-color-text-tertiary);
}
</style>
