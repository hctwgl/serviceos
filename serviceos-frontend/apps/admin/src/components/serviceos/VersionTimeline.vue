<script setup lang="ts">
export type VersionTimelineItem = {
  id: string
  version: string
  status: 'active' | 'historical' | 'draft'
  statusLabel: string
  date: string
  author: string
  summary: string
  active?: boolean
}

withDefaults(defineProps<{
  items: VersionTimelineItem[]
  editable?: boolean
}>(), {
  editable: false,
})

const emit = defineEmits<{
  view: [item: VersionTimelineItem]
  copy: [item: VersionTimelineItem]
}>()
</script>

<template>
  <section class="sos-version-timeline" aria-label="履约方案版本时间线">
    <header class="sos-section-heading">
      <div>
        <span class="sos-eyebrow">REVISION HISTORY</span>
        <h3>版本时间线</h3>
        <span>每个版本冻结完整流程、任务、表单、证据与 SLA；历史版本只读。</span>
      </div>
      <slot name="extra" />
    </header>
    <div v-if="!items.length" class="sos-inline-empty">
      <strong>尚无可展示的版本</strong>
      <span>完成一次履约方案发布后，版本会按生效顺序出现在这里。</span>
    </div>
    <ol v-else class="sos-version-timeline__list">
      <li v-for="item in items" :key="item.id" :class="[{ 'is-active': item.active }, `is-${item.status}`]">
        <span class="sos-version-timeline__dot" aria-hidden="true" />
        <div class="sos-version-timeline__body">
          <header>
            <div class="sos-version-timeline__title">
              <strong>V{{ item.version }}</strong>
              <span class="sos-version-status" :class="`tone-${item.status}`">{{ item.statusLabel }}</span>
            </div>
            <time>{{ item.date }}</time>
          </header>
          <p>{{ item.summary }}</p>
          <footer>
            <span>修改人：{{ item.author }}</span>
            <div>
              <button type="button" class="sos-inline-action" @click="emit('view', item)">查看</button>
              <button v-if="editable" type="button" class="sos-inline-action" @click="emit('copy', item)">复制生成新版本</button>
            </div>
          </footer>
        </div>
      </li>
    </ol>
  </section>
</template>
