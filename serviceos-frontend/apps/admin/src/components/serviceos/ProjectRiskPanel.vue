<script setup lang="ts">
import { RightOutlined, WarningOutlined } from '@serviceos/design-system'
import type { RiskPanelItem } from './types'

withDefaults(defineProps<{
  items: RiskPanelItem[]
  unavailable?: boolean
}>(), {
  unavailable: false,
})
</script>

<template>
  <section class="sos-project-risk-panel" aria-label="项目风险">
    <header class="sos-panel-heading">
      <div>
        <span class="sos-eyebrow">项目风险</span>
        <h2>需要项目经理处理</h2>
      </div>
      <WarningOutlined class="sos-project-risk-panel__mark" />
    </header>

    <div v-if="unavailable" class="sos-inline-unavailable">
      <strong>风险暂不可判断</strong>
      <span>工单投影恢复后再显示项目级风险。</span>
    </div>
    <div v-else-if="!items.length" class="sos-inline-empty">
      <strong>当前没有待处理风险</strong>
      <span>新的时效、异常或责任问题会出现在这里。</span>
    </div>
    <div v-else class="sos-project-risk-list">
      <component
        :is="item.to ? 'RouterLink' : 'div'"
        v-for="item in items"
        :key="item.key"
        :to="item.to"
        class="sos-project-risk-row"
        :class="`sos-project-risk-row--${item.tone}`"
      >
        <span class="sos-project-risk-row__dot" aria-hidden="true" />
        <span class="sos-project-risk-row__copy">
          <strong>{{ item.label }}</strong>
          <small>{{ item.description }}</small>
        </span>
        <b>{{ item.count === null ? '—' : item.count }}</b>
        <RightOutlined v-if="item.to" />
      </component>
    </div>
  </section>
</template>
