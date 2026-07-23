<script setup lang="ts">
import type { WorkOrderWorkspaceTimelineSectionData } from '@serviceos/api-client'
import { Empty } from '@serviceos/design-system'
import { computed } from 'vue'
import { formatDateTime, timelineEventLabel } from '../presenters/work-order'

/** 工单时间线条目列表；事件类型为可扩展枚举，未知事件原样显示英文码。 */
const props = defineProps<{ data: WorkOrderWorkspaceTimelineSectionData | null }>()

const items = computed(() => props.data?.items ?? [])
</script>

<template>
  <Empty
    v-if="!items.length"
    description="暂无操作日志"
  />
  <ol
    v-else
    class="timeline-list"
  >
    <li
      v-for="item in items"
      :key="item.id"
    >
      <span class="timeline-dot" />
      <div class="timeline-body">
        <strong>{{ timelineEventLabel(item.eventType) }}</strong>
        <small>{{ formatDateTime(item.occurredAt) }}<template v-if="item.outcomeCode"> · {{ item.outcomeCode }}</template></small>
      </div>
    </li>
  </ol>
</template>

<style scoped>
.timeline-list {
  display: grid;
  gap: 14px;
  margin: 0;
  padding: 4px 0;
  list-style: none;
}

.timeline-list li {
  display: flex;
  gap: 10px;
}

.timeline-dot {
  flex: 0 0 auto;
  width: 7px;
  height: 7px;
  margin-top: 5px;
  border-radius: 50%;
  background: var(--sos-brand);
}

.timeline-body {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.timeline-body strong {
  color: var(--sos-text-strong);
  font-size: 12px;
}

.timeline-body small {
  color: var(--sos-text-muted);
  font-size: 11px;
}
</style>
