<script setup lang="ts">
import { computed } from 'vue'
import { Tooltip, Button } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { presentFreshnessStatus } from '../presentation/sla-status.presenter'
import { presentDateTime } from '../presentation/date-time.presenter'
import SemanticStatusTag from '../components/business/SemanticStatusTag.vue'

const props = defineProps<{
  freshnessStatus?: string | null
  asOf?: string | null
}>()

const emit = defineEmits<{ refresh: [] }>()

const presentation = computed(() => presentFreshnessStatus(props.freshnessStatus ?? 'UNKNOWN'))
const asOfPresented = computed(() => presentDateTime(props.asOf))

const summary = computed(() => {
  if (asOfPresented.value) {
    return `数据更新：${asOfPresented.value.relative}`
  }
  return presentation.value.label
})
</script>

<template>
  <div class="freshness" data-testid="freshness-indicator">
    <Tooltip :title="asOfPresented?.tooltip || presentation.description">
      <span class="freshness__text">{{ summary }}</span>
    </Tooltip>
    <SemanticStatusTag
      v-if="presentation.semantic === 'stale' || presentation.semantic === 'warning'"
      :presentation="presentation"
    />
    <Button
      type="text"
      size="small"
      aria-label="刷新数据更新时间"
      data-testid="freshness-refresh"
      @click="emit('refresh')"
    >
      <template #icon><ReloadOutlined /></template>
    </Button>
  </div>
</template>

<style scoped>
.freshness {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.freshness__text {
  font-size: var(--sos-font-size-sm, 13px);
  color: var(--sos-color-text-secondary, #4b5563);
  white-space: nowrap;
}
</style>
