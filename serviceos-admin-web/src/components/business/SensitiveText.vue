<script setup lang="ts">
defineProps<{ value?: string | null; emptyText?: string }>()
</script>

<template>
  <span class="sensitive-text">{{ value || emptyText || '—' }}</span>
</template>

<style scoped>
.sensitive-text {
  color: var(--sos-text-primary);
  font-variant-numeric: tabular-nums;
}
</style>
