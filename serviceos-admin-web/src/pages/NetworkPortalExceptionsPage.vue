<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import {
  listNetworkPortalExceptions,
  type NetworkPortalExceptionItem,
} from '../api/networkPortal'

const props = defineProps<{ networkContextId: string | null }>()
const items = ref<NetworkPortalExceptionItem[]>([])
const error = ref<string | null>(null)

async function load() {
  if (!props.networkContextId) {
    items.value = []
    error.value = '请选择 NETWORK 上下文'
    return
  }
  try {
    const page = await listNetworkPortalExceptions(props.networkContextId, { status: 'OPEN' })
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
watch(() => props.networkContextId, () => {
  void load()
})
</script>

<template>
  <section data-testid="network-portal-exceptions" data-page-id="NETWORK.EXCEPTION.QUEUE">
    <h2>本网点异常</h2>
    <p class="hint">未关闭运营异常；可深链到任务页处理，Portal 不提供一键 ACK。</p>
    <p v-if="error" data-testid="network-portal-error">{{ error }}</p>
    <table v-else data-testid="network-exceptions-table">
      <thead>
        <tr>
          <th>异常</th>
          <th>任务</th>
          <th>严重度</th>
          <th>状态</th>
          <th>错误码</th>
          <th>打开时间</th>
          <th>任务</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in items" :key="item.exceptionId">
          <td>
            <RouterLink
              :to="`/network-portal/exceptions/${item.exceptionId}`"
              data-testid="exception-case-deeplink"
            >
              {{ item.exceptionId }}
            </RouterLink>
          </td>
          <td>{{ item.taskId || '—' }}</td>
          <td>{{ item.severity }}</td>
          <td>{{ item.status }}</td>
          <td>{{ item.errorCode }}</td>
          <td>{{ item.openedAt }}</td>
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
.hint {
  color: #5b6573;
  font-size: 0.9rem;
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
