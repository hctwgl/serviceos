<script setup lang="ts">
withDefaults(
  defineProps<{
    kind: 'loading' | 'empty' | 'error' | 'forbidden' | 'unauthorized'
    title?: string
    description?: string
    guide?: string
    errorCode?: string
  }>(),
  {
    title: undefined,
    description: undefined,
    guide: undefined,
    errorCode: undefined,
  },
)

const emit = defineEmits<{ reload: [] }>()
</script>

<template>
  <section class="page-state" :data-testid="`page-state-${kind}`">
    <h3 v-if="kind === 'loading'">{{ title ?? '正在加载数据，请稍候……' }}</h3>
    <template v-else-if="kind === 'empty'">
      <h3>{{ title ?? '暂无相关数据' }}</h3>
      <p>{{ guide ?? description ?? '当前没有可展示的记录。' }}</p>
    </template>
    <template v-else-if="kind === 'error'">
      <h3>{{ title ?? '数据加载失败' }}</h3>
      <p>{{ description ?? '请稍后重试。' }}</p>
      <p v-if="errorCode" class="meta">错误编号：{{ errorCode }}</p>
      <button type="button" data-testid="page-state-reload" @click="emit('reload')">重新加载</button>
    </template>
    <template v-else-if="kind === 'forbidden'">
      <h3>{{ title ?? '您当前没有访问该功能的权限' }}</h3>
      <p>{{ description ?? '请联系管理员开通后重试。' }}</p>
    </template>
    <template v-else>
      <h3>{{ title ?? '登录状态已失效，请重新登录' }}</h3>
      <p>{{ description ?? '重新登录后可继续操作。' }}</p>
    </template>
  </section>
</template>

<style scoped>
.page-state {
  padding: 0.75rem 0;
}
.meta {
  color: #5b6573;
  font-size: 0.85rem;
}
button {
  margin-top: 0.5rem;
  border: 1px solid #bcccdc;
  background: #f0f4f8;
  border-radius: 6px;
  padding: 0.4rem 0.75rem;
  cursor: pointer;
}
</style>
