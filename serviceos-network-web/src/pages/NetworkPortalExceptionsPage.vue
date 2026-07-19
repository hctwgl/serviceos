<script setup lang="ts">
import { statusLabel } from '../product/labels'
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import {
  listNetworkPortalExceptions,
  type NetworkPortalExceptionItem,
} from '../api/networkPortal'

const props = defineProps<{ networkContextId: string | null }>()
const route = useRoute()
const filterTaskId = computed(() => {
  const raw = route.query.taskId
  return typeof raw === 'string' && raw.trim() ? raw.trim() : null
})
const items = ref<NetworkPortalExceptionItem[]>([])
const error = ref<string | null>(null)

async function load() {
  if (!props.networkContextId) {
    items.value = []
    error.value = '请选择 NETWORK 上下文'
    return
  }
  try {
    const page = await listNetworkPortalExceptions(props.networkContextId, {
      status: 'OPEN',
      taskId: filterTaskId.value ?? undefined,
    })
    items.value = page.items
    error.value = null
  } catch (err) {
    items.value = []
    error.value = err instanceof Error ? err.message : '异常队列加载失败'
  }
}

onMounted(() => {
  void load()
})
watch(
  () => [props.networkContextId, filterTaskId.value] as const,
  () => {
    void load()
  },
)
</script>

<template>
  <section data-testid="network-portal-exceptions" data-page-id="NETWORK.EXCEPTION.QUEUE">
    <h2>本网点异常</h2>
    <p class="hint">未关闭运营异常；M220 展示 Accepted 字段；Portal 不提供一键 ACK。</p>
    <p
      v-if="filterTaskId"
      class="filter"
      data-testid="exceptions-task-filter"
    >
      已按 taskId 过滤：{{ filterTaskId }}
      <RouterLink to="/network-portal/exceptions" data-testid="exceptions-clear-task-filter">
        清除
      </RouterLink>
    </p>
    <p v-if="error" data-testid="network-portal-error">{{ error }}</p>
    <table v-else data-testid="network-exceptions-table">
      <thead>
        <tr>
          <th>异常</th>
          <th>项目</th>
          <th>工单</th>
          <th>任务</th>
          <th>处理任务</th>
          <th>来源/类别</th>
          <th>严重度</th>
          <th>状态</th>
          <th>错误码</th>
          <th>次数</th>
          <th>打开/最近</th>
          <th>任务</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="item in items"
          :key="item.exceptionId"
          :data-testid="`exception-row-${item.exceptionId}`"
        >
          <td>
            <RouterLink
              :to="`/network-portal/exceptions/${item.exceptionId}`"
              data-testid="exception-case-deeplink"
            >
              {{ item.exceptionId }}
            </RouterLink>
          </td>
          <td data-testid="exception-project-id">{{ item.projectId ?? '—' }}</td>
          <td>
            <RouterLink
              v-if="item.workOrderId"
              :to="`/network-portal/work-orders/${item.workOrderId}`"
              data-testid="exception-work-order-deeplink"
            >
              {{ item.workOrderId }}
            </RouterLink>
            <span v-else>—</span>
          </td>
          <td>{{ item.taskId || '—' }}</td>
          <td>
            <RouterLink
              v-if="item.handlingTaskId"
              :to="{ path: '/network-portal/tasks', query: { taskId: item.handlingTaskId } }"
              data-testid="exception-handling-task-deeplink"
            >
              {{ item.handlingTaskId }}
            </RouterLink>
            <span v-else>—</span>
          </td>
          <td data-testid="exception-source-category">
            {{ item.sourceType }} / {{ item.category }}
          </td>
          <td>{{ item.severity ? statusLabel(item.severity) : '—' }}</td>
          <td>{{ item.status ? statusLabel(item.status) : '—' }}</td>
          <td>{{ item.errorCode }}</td>
          <td data-testid="exception-occurrence-count">{{ item.occurrenceCount }}</td>
          <td data-testid="exception-opened-last">
            {{ item.openedAt }} / {{ item.lastDetectedAt }}
          </td>
          <td>
            <RouterLink
              v-if="item.taskId"
              :to="{ path: '/network-portal/tasks', query: { taskId: item.taskId } }"
              data-testid="exception-task-deeplink"
            >
              打开任务
            </RouterLink>
            <span v-else>—</span>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-if="!error && items.length === 0">暂无 OPEN 异常</p>
  </section>
</template>

<style scoped>
.hint,
.filter {
  color: #5b6573;
  font-size: 0.9rem;
}
.filter a {
  margin-left: 0.5rem;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th,
td {
  text-align: left;
  padding: 0.4rem 0.5rem;
  border-bottom: 1px solid #e5e9ef;
  font-size: 0.9rem;
}
</style>
