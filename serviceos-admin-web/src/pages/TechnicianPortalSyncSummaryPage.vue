<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import {
  getTechnicianSyncSummary,
  type TechnicianPortalSyncSummary,
} from '../api/technicianPortal'

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
    <p class="hint">M218：展示 asOf/networkId；计数深链 Feed/日程（不含离线命令 runtime）。</p>
    <p v-if="error" data-testid="technician-portal-error">{{ error }}</p>
    <template v-else-if="summary">
      <dl data-testid="technician-sync-summary-meta" class="meta">
        <div>
          <dt>networkId</dt>
          <dd data-testid="technician-sync-network-id">{{ summary.networkId }}</dd>
        </div>
        <div>
          <dt>asOf</dt>
          <dd data-testid="technician-sync-as-of">{{ summary.asOf }}</dd>
        </div>
      </dl>
      <dl data-testid="technician-sync-summary-counts">
        <div>
          <dt>待处理 Feed</dt>
          <dd>
            <RouterLink
              to="/technician-portal/task-feed"
              data-testid="technician-sync-feed-deeplink"
            >
              {{ summary.pendingFeedItemCount }}
            </RouterLink>
          </dd>
        </div>
        <div>
          <dt>预约窗口</dt>
          <dd>
            <RouterLink
              to="/technician-portal/schedule"
              data-testid="technician-sync-schedule-deeplink"
            >
              {{ summary.appointmentWindowCount }}
            </RouterLink>
          </dd>
        </div>
        <div>
          <dt>Tombstone</dt>
          <dd>
            <RouterLink
              to="/technician-portal/task-feed"
              data-testid="technician-sync-tombstone-deeplink"
            >
              {{ summary.tombstoneCount }}
            </RouterLink>
          </dd>
        </div>
      </dl>
    </template>
  </section>
</template>

<style scoped>
.hint,
.meta {
  color: #5b6573;
  font-size: 0.9rem;
}
.meta {
  margin-bottom: 1rem;
}
.meta dd {
  margin: 0 0 0.35rem;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.85rem;
  word-break: break-all;
}
dl[data-testid='technician-sync-summary-counts'] {
  display: grid;
  gap: 0.75rem;
  max-width: 22rem;
}
dl[data-testid='technician-sync-summary-counts'] > div {
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
