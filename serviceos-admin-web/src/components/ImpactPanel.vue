<script setup lang="ts">
defineProps<{
  title?: string
  items: string[]
  obligations?: string[]
}>()
</script>

<template>
  <aside class="impact" data-testid="impact-panel">
    <h4>{{ title ?? '影响范围' }}</h4>
    <ul v-if="items.length">
      <li v-for="(item, index) in items" :key="`impact-${index}`">{{ item }}</li>
    </ul>
    <p v-else class="muted">提交前请确认影响与未完成工作。</p>
    <template v-if="obligations?.length">
      <h5>Obligations</h5>
      <ul>
        <li v-for="(item, index) in obligations" :key="`obl-${index}`">{{ item }}</li>
      </ul>
    </template>
  </aside>
</template>

<style scoped>
.impact {
  border: 1px solid #f0b429;
  background: #fffbeb;
  border-radius: 8px;
  padding: 0.75rem 0.9rem;
}
h4,
h5 {
  margin: 0 0 0.4rem;
  font-size: 0.95rem;
}
ul {
  margin: 0;
  padding-left: 1.1rem;
  font-size: 0.85rem;
  color: #513c06;
}
.muted {
  margin: 0;
  color: #8d6e1b;
  font-size: 0.85rem;
}
</style>
