<script setup lang="ts">
defineProps<{ tone?: 'blue' | 'green' | 'orange' | 'red' | 'gray'; label: string }>()
</script>

<template><span class="status-pill" :class="`tone-${tone ?? 'gray'}`">{{ label }}</span></template>
