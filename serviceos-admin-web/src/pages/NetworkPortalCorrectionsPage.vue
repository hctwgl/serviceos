<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import {
  listNetworkPortalCorrections,
  type NetworkPortalCorrectionItem,
} from '../api/networkPortal'

const props = defineProps<{ networkContextId: string | null }>()
const items = ref<NetworkPortalCorrectionItem[]>([])
const error = ref<string | null>(null)

async function load() {
  if (!props.networkContextId) {
    items.value = []
    error.value = '请选择 NETWORK 上下文'
    return
  }
  try {
    const page = await listNetworkPortalCorrections(props.networkContextId, { status: 'OPEN' })
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
watch(() => props.networkContextId, () => {
  void load()
})
</script>

<template>
  <section data-testid="network-portal-corrections" data-page-id="NETWORK.CORRECTION.QUEUE">
    <h2>本网点整改</h2>
    <p class="hint">未关闭整改案例；可深链到任务页进行资料代补。</p>
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
