<script setup lang="ts">
import { ref } from 'vue'
import { Button, Space, Card } from 'ant-design-vue'
import { DownOutlined, UpOutlined } from '@ant-design/icons-vue'

defineProps<{
  loading?: boolean
}>()

const emit = defineEmits<{ search: []; reset: [] }>()
const moreOpen = ref(false)
</script>

<template>
  <Card class="query-panel" size="small" :bordered="true" data-testid="query-panel">
    <form class="query-panel__form" @submit.prevent="emit('search')">
      <div class="query-panel__primary">
        <slot name="primary" />
        <Space>
          <Button type="primary" html-type="submit" :loading="loading">查询</Button>
          <Button :disabled="loading" @click="emit('reset')">重置</Button>
          <Button
            v-if="$slots.more"
            type="link"
            data-testid="query-more-toggle"
            @click="moreOpen = !moreOpen"
          >
            {{ moreOpen ? '收起筛选' : '更多筛选' }}
            <UpOutlined v-if="moreOpen" />
            <DownOutlined v-else />
          </Button>
        </Space>
      </div>
      <div v-if="moreOpen && $slots.more" class="query-panel__more" data-testid="query-more">
        <slot name="more" />
      </div>
    </form>
  </Card>
</template>

<style scoped>
.query-panel {
  background: var(--sos-color-surface-card, #fff);
}
.query-panel__form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.query-panel__primary {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-end;
}
.query-panel__more {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding-top: 8px;
  border-top: 1px solid var(--sos-color-divider, #edf0f2);
}
</style>
