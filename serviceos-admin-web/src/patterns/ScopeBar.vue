<script setup lang="ts">
import { computed } from 'vue'
import { Tag, Tooltip } from 'ant-design-vue'
import type { MeContext, MeProfile } from '../api/me'
import { presentEntityName } from '../presentation/entity-name.presenter'
import { formatRelativeDisplay, presentDateTime } from '../presentation/date-time.presenter'
import FreshnessIndicator from './FreshnessIndicator.vue'

const props = defineProps<{
  profile: MeProfile | null
  activeContext: MeContext | null
  asOf?: string | null
  freshnessStatus?: string | null
}>()

const emit = defineEmits<{ refresh: [] }>()

const tenantLabel = computed(() => {
  if (!props.profile) return '未提供'
  // tenantId 常为 UUID：正式界面显示「当前租户」摘要，不展示完整 UUID
  const name = presentEntityName({
    name: props.profile.displayName ? undefined : null,
    code: 'ServiceOS',
    id: props.profile.tenantId,
  })
  return name.label === '名称不可用' ? 'ServiceOS' : 'ServiceOS'
})

const projectLabel = computed(() => {
  const ids = props.activeContext?.scopeSummary.projectIds ?? []
  if (ids.length === 0) return '全部项目'
  if (ids.length === 1) {
    return presentEntityName({ id: ids[0], loaded: true }).label
  }
  return `${ids.length} 个项目`
})

const regionLabel = computed(() => {
  // 区域名称字典 UI_DATA_GAP：当前上下文无 region 名称字段
  return '未提供'
})

const orgScopeLabel = computed(() => {
  const orgs = props.activeContext?.scopeSummary.organizationIds ?? []
  const networks = props.activeContext?.scopeSummary.networkIds ?? []
  if (orgs.length === 0 && networks.length === 0) return null
  const parts: string[] = []
  if (orgs.length) parts.push(`${orgs.length} 个组织`)
  if (networks.length) parts.push(`${networks.length} 个网点`)
  return parts.join(' / ')
})

const restricted = computed(() => orgScopeLabel.value != null)

const asOfRelative = computed(() => formatRelativeDisplay(props.asOf))
const asOfTip = computed(() => presentDateTime(props.asOf)?.tooltip)
</script>

<template>
  <div class="scope-bar" data-testid="scope-bar" role="status" aria-label="当前查看范围">
    <span class="scope-bar__item">
      <span class="scope-bar__k">租户</span>
      <span class="scope-bar__v">{{ tenantLabel }}</span>
    </span>
    <span class="scope-bar__sep" aria-hidden="true">·</span>
    <span class="scope-bar__item">
      <span class="scope-bar__k">项目</span>
      <span class="scope-bar__v">{{ projectLabel }}</span>
    </span>
    <span class="scope-bar__sep" aria-hidden="true">·</span>
    <Tooltip title="区域名称字典尚未由服务端提供（UI_DATA_GAP）">
      <span class="scope-bar__item">
        <span class="scope-bar__k">区域</span>
        <span class="scope-bar__v">{{ regionLabel }}</span>
      </span>
    </Tooltip>
    <template v-if="restricted">
      <span class="scope-bar__sep" aria-hidden="true">·</span>
      <Tag color="processing" data-testid="scope-restricted">
        当前查看范围：{{ orgScopeLabel }}
      </Tag>
    </template>
    <span class="scope-bar__sep" aria-hidden="true">·</span>
    <Tooltip :title="asOfTip">
      <span class="scope-bar__item" data-testid="scope-as-of">
        <span class="scope-bar__k">数据更新</span>
        <span class="scope-bar__v">{{ asOfRelative }}</span>
      </span>
    </Tooltip>
    <FreshnessIndicator
      :freshness-status="freshnessStatus"
      :as-of="asOf"
      @refresh="emit('refresh')"
    />
  </div>
</template>

<style scoped>
.scope-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 8px;
  min-width: 0;
  font-size: var(--sos-font-size-sm, 13px);
}
.scope-bar__k {
  color: var(--sos-color-text-tertiary, var(--sos-color-text-tertiary, #5f6b7a));
  margin-right: 4px;
}
.scope-bar__v {
  color: var(--sos-color-text-primary, #1f2937);
  font-weight: 500;
}
.scope-bar__sep {
  color: var(--sos-color-text-disabled, #a7adb7);
}
</style>
