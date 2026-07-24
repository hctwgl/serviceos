<script setup lang="ts">
defineProps<{
  data: {
    color: string
    name: string
    nodeCount: number
  }
}>()
</script>

<template>
  <div class="workflow-phase-lane" :style="{ '--phase-color': data.color }">
    <div>
      <strong>{{ data.name }}</strong>
      <span>{{ data.nodeCount }} 个节点</span>
    </div>
  </div>
</template>
