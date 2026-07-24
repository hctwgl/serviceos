<script setup lang="ts">
export type BusinessTimelineItem = {
  id: string
  title: string
  time: string
  description: string
  tone?: 'blue' | 'green' | 'orange' | 'gray'
}

withDefaults(defineProps<{
  items: BusinessTimelineItem[]
  title?: string
  description?: string
  unavailable?: boolean
}>(), {
  title: '业务时间线',
  description: '来自项目工单与履约事件的时间顺序投影。',
  unavailable: false,
})
</script>

<template>
  <section class="sos-business-timeline">
    <header class="sos-panel-heading">
      <div>
        <span class="sos-eyebrow">BUSINESS TIMELINE</span>
        <h2>{{ title }}</h2>
        <p>{{ description }}</p>
      </div>
      <span class="sos-timeline-freshness"><i aria-hidden="true" />已同步</span>
      <slot name="extra" />
    </header>
    <div v-if="unavailable" class="sos-inline-unavailable">
      <strong>业务动态暂时无法获取</strong>
      <span>工单投影恢复后会自动刷新，不显示伪造的空状态。</span>
    </div>
    <div v-else-if="!items.length" class="sos-inline-empty">
      <strong>当前项目还没有业务事件</strong>
      <span>新工单受理后，客户联系、预约和履约节点会出现在这里。</span>
    </div>
    <ol v-else class="sos-business-timeline__list">
      <li v-for="item in items" :key="item.id" :class="`tone-${item.tone ?? 'blue'}`">
        <time>{{ item.time }}</time>
        <span class="sos-business-timeline__rail"><i aria-hidden="true" /></span>
        <div>
          <strong>{{ item.title }}</strong>
          <p>{{ item.description }}</p>
        </div>
      </li>
    </ol>
  </section>
</template>
