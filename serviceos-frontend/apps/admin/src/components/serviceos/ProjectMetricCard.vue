<script setup lang="ts">
withDefaults(defineProps<{
  label: string
  value: string
  hint: string
  tone?: 'default' | 'blue' | 'success' | 'warning' | 'danger'
}>(), {
  tone: 'default',
})
</script>

<template>
  <article class="sos-project-metric-card" :class="`sos-project-metric-card--${tone}`">
    <span>{{ label }}</span>
    <strong>{{ value }}</strong>
    <small>{{ hint }}</small>
  </article>
</template>
