<script setup lang="ts">
import { RightOutlined, WarningOutlined } from '@serviceos/design-system'
import type { RiskPanelItem } from './types'

withDefaults(defineProps<{
  items: RiskPanelItem[]
  title?: string
  description?: string
  unavailable?: boolean
}>(), {
  title: '风险与待处理',
  description: '只显示当前数据范围内的业务投影',
  unavailable: false,
})
</script>

<template>
  <section class="sos-risk-panel">
    <header class="sos-panel-heading">
      <div>
        <span class="sos-eyebrow">OPERATIONS SIGNAL</span>
        <h2>{{ title }}</h2>
        <p>{{ description }}</p>
      </div>
      <WarningOutlined class="sos-risk-panel__mark" />
    </header>
    <div v-if="unavailable" class="sos-inline-unavailable">
      <strong>风险聚合暂时无法获取</strong>
      <span>进入工单中心查看最新业务事实。</span>
    </div>
    <div v-else-if="!items.length" class="sos-inline-empty">
      <strong>当前没有待处理风险</strong>
      <span>系统会在新的风险投影生成后更新这里。</span>
    </div>
    <div v-else class="sos-risk-list">
      <component
        :is="item.to ? 'RouterLink' : 'div'"
        v-for="item in items"
        :key="item.key"
        :to="item.to"
        class="sos-risk-row"
        :class="`sos-risk-row--${item.tone}`"
      >
        <span class="sos-risk-row__dot" aria-hidden="true" />
        <span class="sos-risk-row__copy">
          <strong>{{ item.label }}</strong>
          <small>{{ item.description }}</small>
        </span>
        <b>{{ item.count === null ? '—' : item.count }}</b>
        <RightOutlined v-if="item.to" />
      </component>
    </div>
  </section>
</template>
