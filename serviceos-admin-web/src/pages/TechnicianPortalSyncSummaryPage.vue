<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { getTechnicianSyncSummary, type TechnicianPortalSyncSummary } from '../api/technicianPortal'

const props = defineProps<{ technicianContextId: string | null }>()
const summary = ref<TechnicianPortalSyncSummary | null>(null)
const error = ref<string | null>(null)

async function load() {
  if (!props.technicianContextId) {
    summary.value = null
    error.value = '请选择 TECHNICIAN 上下文'
    return
  }
  try {
    summary.value = await getTechnicianSyncSummary(props.technicianContextId)
    error.value = null
  } catch (err) {
    summary.value = null
    error.value = err instanceof Error ? err.message : '同步摘要加载失败'
  }
}

onMounted(() => {
  void load()
})
watch(() => props.technicianContextId, () => {
  void load()
})
</script>

<template>
  <section data-testid="technician-portal-sync-summary">
    <h2>同步摘要</h2>
    <p v-if="error" data-testid="technician-portal-error">{{ error }}</p>
    <dl v-else-if="summary" data-testid="technician-sync-summary-counts">
      <div>
        <dt>待处理 Feed</dt>
        <dd>{{ summary.pendingFeedItemCount }}</dd>
      </div>
      <div>
        <dt>预约窗口</dt>
        <dd>{{ summary.appointmentWindowCount }}</dd>
      </div>
      <div>
        <dt>Tombstone</dt>
        <dd>{{ summary.tombstoneCount }}</dd>
      </div>
    </dl>
  </section>
</template>

<style scoped>
dl {
  display: grid;
  gap: 0.75rem;
  max-width: 20rem;
}
div {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
}
dt {
  color: #5b6573;
}
dd {
  margin: 0;
  font-weight: 600;
}
</style>
