<script setup lang="ts">
import { computed } from 'vue'
import { statusLabel, statusTone } from '../product/statusLabels'

const props = defineProps<{
  status: string | null | undefined
}>()

const label = computed(() => statusLabel(props.status))
const tone = computed(() => statusTone(props.status))
</script>

<template>
  <span class="badge" :data-tone="tone" :title="status ?? undefined">{{ label }}</span>
</template>

<style scoped>
.badge {
  display: inline-block;
  padding: 0.15rem 0.5rem;
  border-radius: 999px;
  font-size: 0.82rem;
  font-weight: 600;
  background: #e2e8f0;
  color: #243b53;
}
.badge[data-tone='success'] {
  background: #d3f9d8;
  color: #0b6b2a;
}
.badge[data-tone='warning'] {
  background: #fff3bf;
  color: #8a6d00;
}
.badge[data-tone='danger'] {
  background: #ffe3e3;
  color: #c92a2a;
}
.badge[data-tone='info'] {
  background: #d0ebff;
  color: #1864ab;
}
</style>
