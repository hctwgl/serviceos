<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  getNetworkPortalWorkOrderWorkspace,
  type NetworkPortalWorkOrderWorkspace,
} from '../api/networkPortal'

const props = defineProps<{ networkContextId: string | null }>()
const route = useRoute()
const workOrderId = computed(() => String(route.params.id ?? ''))
const detail = ref<NetworkPortalWorkOrderWorkspace | null>(null)
const error = ref<string | null>(null)
const loading = ref(false)

async function load() {
  if (!props.networkContextId) {
    detail.value = null
    error.value = '请选择 NETWORK 上下文'
    return
  }
  if (!workOrderId.value) {
    detail.value = null
    error.value = '缺少 workOrderId'
    return
  }
  loading.value = true
  try {
    detail.value = await getNetworkPortalWorkOrderWorkspace(
      props.networkContextId,
      workOrderId.value,
    )
    error.value = null
  } catch (err) {
    detail.value = null
    error.value = err instanceof Error ? err.message : '工单工作区加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})
watch(
  () => [props.networkContextId, workOrderId.value] as const,
  () => {
    void load()
  },
)
</script>

<template>
  <section
    data-testid="network-portal-work-order-workspace"
    data-page-id="NETWORK.WORKORDER.WORKSPACE"
  >
    <header class="top">
      <div>
        <h2>限定工单工作区</h2>
        <p class="meta" data-testid="workspace-work-order-id">{{ workOrderId }}</p>
      </div>
      <div class="actions">
        <RouterLink to="/network-portal/work-orders" data-testid="workspace-back-to-list">
          返回工单列表
        </RouterLink>
        <button type="button" :disabled="loading" data-testid="workspace-refresh" @click="load">
          刷新
        </button>
      </div>
    </header>
    <p class="hint">
      只读薄快照（M213）：仅本网点 ACTIVE NETWORK 责任任务；不含 Admin 工作区 INTEGRATION/定价/PII。
    </p>
    <p v-if="error" data-testid="network-portal-error">{{ error }}</p>
    <p v-else-if="loading" data-testid="workspace-loading">加载中…</p>
    <template v-else-if="detail">
      <dl data-testid="workspace-header-fields">
        <div><dt>networkId</dt><dd>{{ detail.networkId }}</dd></div>
        <div><dt>projectId</dt><dd>{{ detail.projectId ?? '—' }}</dd></div>
        <div><dt>businessType</dt><dd data-testid="workspace-business-type">{{ detail.businessType ?? '—' }}</dd></div>
        <div><dt>technicianId</dt><dd>{{ detail.technicianId ?? '—' }}</dd></div>
        <div><dt>effectiveFrom</dt><dd>{{ detail.effectiveFrom ?? '—' }}</dd></div>
        <div><dt>asOf</dt><dd data-testid="workspace-as-of">{{ detail.asOf }}</dd></div>
      </dl>

      <h3>本网点 ACTIVE 任务</h3>
      <table data-testid="workspace-tasks-table">
        <thead>
          <tr>
            <th>任务</th>
            <th>状态</th>
            <th>阶段</th>
            <th>类型</th>
            <th>师傅</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="task in detail.tasks"
            :key="task.taskId"
            :data-testid="`workspace-task-${task.taskId}`"
          >
            <td>{{ task.taskId }}</td>
            <td>{{ task.status ?? '—' }}</td>
            <td>{{ task.stageCode ?? '—' }}</td>
            <td>{{ task.taskType ?? '—' }}</td>
            <td>{{ task.technicianId ?? '—' }}</td>
            <td>
              <RouterLink
                :to="{ path: '/network-portal/tasks', query: { taskId: task.taskId } }"
                data-testid="workspace-task-deeplink"
              >
                打开任务
              </RouterLink>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="detail.tasks.length === 0" data-testid="workspace-tasks-empty">暂无 ACTIVE 任务</p>
    </template>
  </section>
</template>

<style scoped>
.top {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: flex-start;
}
.meta,
.hint {
  color: #5b6573;
  font-size: 0.9rem;
}
.actions {
  display: flex;
  gap: 0.75rem;
  align-items: center;
}
dl {
  display: grid;
  gap: 0.35rem;
  margin: 1rem 0;
}
dt {
  font-size: 0.75rem;
  color: #5b6573;
}
dd {
  margin: 0 0 0.35rem;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.85rem;
  word-break: break-all;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th,
td {
  border-bottom: 1px solid #e5e7eb;
  padding: 0.45rem 0.35rem;
  text-align: left;
  font-size: 0.85rem;
}
</style>
