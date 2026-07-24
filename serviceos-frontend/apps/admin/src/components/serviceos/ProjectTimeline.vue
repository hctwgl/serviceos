<script setup lang="ts">
export type ProjectTimelineItem = {
  id: string
  title: string
  time: string
  description: string
  tone?: 'blue' | 'green' | 'orange' | 'gray'
}

withDefaults(defineProps<{
  items: ProjectTimelineItem[]
  unavailable?: boolean
}>(), {
  unavailable: false,
})
</script>

<template>
  <section class="sos-project-timeline" aria-label="项目业务时间线">
    <header class="sos-panel-heading">
      <div>
        <span class="sos-eyebrow">项目进展</span>
        <h2>业务时间线</h2>
      </div>
      <slot name="extra" />
    </header>

    <div v-if="unavailable" class="sos-inline-unavailable">
      <strong>业务事件暂不可获取</strong>
      <span>工单投影恢复后会自动刷新。</span>
    </div>
    <div v-else-if="!items.length" class="sos-inline-empty">
      <strong>当前项目还没有业务事件</strong>
      <span>客户联系、预约和履约节点完成后会出现在这里。</span>
    </div>
    <ol v-else class="sos-project-timeline__list">
      <li v-for="item in items" :key="item.id" :class="`tone-${item.tone ?? 'blue'}`">
        <time>{{ item.time }}</time>
        <span class="sos-project-timeline__rail"><i aria-hidden="true" /></span>
        <div>
          <strong>{{ item.title }}</strong>
          <p>{{ item.description }}</p>
        </div>
      </li>
    </ol>
  </section>
</template>
