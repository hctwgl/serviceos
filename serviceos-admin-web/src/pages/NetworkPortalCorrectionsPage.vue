<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  listNetworkPortalCorrections,
  type NetworkPortalCorrectionItem,
} from '../api/networkPortal'

const props = defineProps<{ networkContextId: string | null }>()
const route = useRoute()
const filterTaskId = computed(() => {
  const raw = route.query.taskId
  return typeof raw === 'string' && raw.trim() ? raw.trim() : null
})
const items = ref<NetworkPortalCorrectionItem[]>([])
const error = ref<string | null>(null)

async function load() {
  if (!props.networkContextId) {
    items.value = []
    error.value = '请选择 NETWORK 上下文'
    return
  }
  try {
    const page = await listNetworkPortalCorrections(props.networkContextId, {
      status: 'OPEN',
      taskId: filterTaskId.value ?? undefined,
    })
    items.value = page.items
    error.value = null
  } catch (err) {
    items.value = []
    error.value = err instanceof Error ? err.message : '整改队列加载失败'
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
  <section data-testid="network-portal-corrections" data-page-id="NETWORK.CORRECTION.QUEUE">
    <h2>本网点整改</h2>
    <p class="hint">未关闭整改案例；可深链到任务页进行资料代补。</p>
    <p
      v-if="filterTaskId"
      class="filter"
      data-testid="corrections-task-filter"
    >
      已按 taskId 过滤：{{ filterTaskId }}
      <RouterLink to="/network-portal/corrections" data-testid="corrections-clear-task-filter">
        清除
      </RouterLink>
    </p>
    <p v-if="error" data-testid="network-portal-error">{{ error }}</p>
    <table v-else data-testid="network-corrections-table">
      <thead>
        <tr>
          <th>整改案例</th>
          <th>任务</th>
          <th>状态</th>
          <th>原因码</th>
          <th>创建时间</th>
          <th>代补</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in items" :key="item.correctionCaseId">
          <td>
            <RouterLink
              :to="`/network-portal/corrections/${item.correctionCaseId}`"
              data-testid="correction-case-deeplink"
            >
              {{ item.correctionCaseId }}
            </RouterLink>
          </td>
          <td>{{ item.taskId }}</td>
          <td>{{ item.status }}</td>
          <td>{{ item.reasonCodes.join(', ') || '—' }}</td>
          <td>{{ item.createdAt }}</td>
          <td>
            <RouterLink
              :to="{ path: '/network-portal/tasks', query: { taskId: item.taskId } }"
              data-testid="correction-task-deeplink"
            >
              打开任务代补
            </RouterLink>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-if="!error && items.length === 0">暂无 OPEN 整改</p>
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
