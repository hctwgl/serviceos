<script setup lang="ts">
import type { WorkOrderTimelineItem } from '@serviceos/api-client'
import { ClockCircleOutlined } from '@serviceos/design-system'
import { computed } from 'vue'
import { formatDateTime, timelineEventLabel } from '../../presenters/work-order'

const props = withDefaults(defineProps<{
  items?: WorkOrderTimelineItem[] | null
  title?: string
  description?: string
  freshnessStatus?: string | null
  emptyLabel?: string
}>(), {
  items: undefined,
  title: '最近业务事件',
  description: '按服务端时间线投影排序',
  freshnessStatus: null,
  emptyLabel: '当前还没有可展示的业务事件',
})

const freshnessLabel = computed(() => {
  if (props.freshnessStatus === 'LAGGING') return '投影稍有延迟'
  if (props.freshnessStatus === 'REBUILDING') return '投影重建中'
  if (props.freshnessStatus === 'UNKNOWN') return '更新时间未知'
  return '已同步'
})
</script>

<template>
  <section class="sos-workorder-timeline">
    <header class="sos-panel-heading">
      <div>
        <span class="sos-eyebrow">ACTIVITY STREAM</span>
        <h2>{{ title }}</h2>
        <p>{{ description }}</p>
      </div>
      <span v-if="freshnessStatus" class="sos-freshness"><span />{{ freshnessLabel }}</span>
    </header>
    <div v-if="items === null" class="sos-inline-unavailable">
      <strong>业务事件暂时无法获取</strong>
      <span>数据源恢复后会自动刷新，当前不把未知状态显示为空。</span>
    </div>
    <div v-else-if="!items?.length" class="sos-inline-empty">
      <ClockCircleOutlined />
      <strong>{{ emptyLabel }}</strong>
    </div>
    <ol v-else class="sos-timeline-list">
      <li v-for="item in items" :key="item.id">
        <span class="sos-timeline-list__rail"><span /></span>
        <div class="sos-timeline-list__body">
          <div class="sos-timeline-list__topline">
            <strong>{{ timelineEventLabel(item.eventType) }}</strong>
            <time>{{ formatDateTime(item.occurredAt) }}</time>
          </div>
          <p>{{ item.resourceCode || item.resourceType }}<span v-if="item.outcomeCode"> · {{ item.outcomeCode }}</span></p>
        </div>
      </li>
    </ol>
  </section>
</template>
