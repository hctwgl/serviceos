<script setup lang="ts">
import { Button, Space, Tag } from 'ant-design-vue'
import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  DeleteOutlined,
  PlusOutlined,
} from '@ant-design/icons-vue'

const props = defineProps<{
  stages: Array<{ stageCode: string; stageName: string; sequence: number }>
  selectedCode: string | null
  errorCounts?: Record<string, number>
  disabled?: boolean
}>()

const emit = defineEmits<{
  select: [stageCode: string]
  add: []
  move: [stageCode: string, delta: number]
  remove: [stageCode: string]
}>()

function selectedIndex(): number {
  return props.stages.findIndex((stage) => stage.stageCode === props.selectedCode)
}
</script>

<template>
  <aside class="stage-nav" aria-label="流程阶段导航">
    <div class="stage-nav__head">
      <div>
        <strong>流程阶段</strong>
        <div class="stage-nav__hint">按实际履约顺序排列</div>
      </div>
      <Button type="primary" size="small" :disabled="disabled" @click="emit('add')">
        <template #icon><PlusOutlined /></template>
        新增
      </Button>
    </div>

    <Space direction="vertical" :size="6" style="width: 100%">
      <Button
        v-for="stage in stages"
        :key="stage.stageCode"
        block
        class="stage-nav__item"
        :class="{ 'stage-nav__item--active': stage.stageCode === selectedCode }"
        :type="stage.stageCode === selectedCode ? 'primary' : 'default'"
        :ghost="stage.stageCode === selectedCode"
        :disabled="disabled"
        @click="emit('select', stage.stageCode)"
      >
        <span class="stage-nav__label">{{ stage.sequence }}. {{ stage.stageName }}</span>
        <Tag v-if="errorCounts?.[stage.stageCode]" color="error">
          {{ errorCounts[stage.stageCode] }} 项错误
        </Tag>
      </Button>
    </Space>

    <Space class="stage-nav__actions" wrap>
      <Button
        size="small"
        :disabled="disabled || selectedIndex() <= 0 || !selectedCode"
        @click="selectedCode && emit('move', selectedCode, -1)"
      >
        <template #icon><ArrowUpOutlined /></template>
        上移
      </Button>
      <Button
        size="small"
        :disabled="disabled || selectedIndex() < 0 || selectedIndex() >= stages.length - 1 || !selectedCode"
        @click="selectedCode && emit('move', selectedCode, 1)"
      >
        <template #icon><ArrowDownOutlined /></template>
        下移
      </Button>
      <Button
        size="small"
        danger
        :disabled="disabled || !selectedCode"
        @click="selectedCode && emit('remove', selectedCode)"
      >
        <template #icon><DeleteOutlined /></template>
        删除
      </Button>
    </Space>
  </aside>
</template>

<style scoped>
.stage-nav {
  border: 1px solid var(--sos-color-border-light);
  border-radius: var(--sos-radius-md);
  padding: 12px;
  background: var(--sos-color-surface-card, #fff);
}
.stage-nav__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.stage-nav__hint {
  margin-top: 2px;
  font-size: 12px;
  color: var(--sos-color-text-secondary);
}
.stage-nav__item {
  height: auto;
  min-height: 40px;
  padding: 7px 10px;
}
.stage-nav__item :deep(span) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 8px;
}
.stage-nav__label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: left;
}
.stage-nav__actions {
  margin-top: 12px;
}
</style>
