<script setup lang="ts">
import { Tag } from '@serviceos/design-system'

const props = withDefaults(defineProps<{
  status: string | null | undefined
  version?: string | number | null
  compact?: boolean
}>(), {
  version: null,
  compact: false,
})

const statusPresentation: Record<string, { label: string; color: string }> = {
  ACTIVE: { label: '当前生效', color: 'success' },
  PUBLISHED: { label: '已发布', color: 'success' },
  DRAFT: { label: '草稿', color: 'warning' },
  HISTORICAL: { label: '历史版本', color: 'default' },
  RETIRED: { label: '已归档', color: 'default' },
  SUSPENDED: { label: '已暂停', color: 'error' },
}

const presentation = () => statusPresentation[props.status ?? ''] ?? { label: '状态待确认', color: 'default' }
</script>

<template>
  <Tag :color="presentation().color" :class="{ 'version-badge--compact': compact }">
    <span v-if="version !== null && version !== undefined">V{{ version }} · </span>{{ presentation().label }}
  </Tag>
</template>
