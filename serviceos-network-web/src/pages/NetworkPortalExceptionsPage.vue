<script setup lang="ts">
import { statusLabel } from '../product/labels'
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { formatDateTime, safeProblemMessage } from '@serviceos/web-core'

import {
  listNetworkPortalExceptions,
  type NetworkPortalExceptionItem,
} from '../api/networkPortal'
import SummaryStrip, { type SummaryStripItem } from '../components/SummaryStrip.vue'
import PageState from '../components/PageState.vue'

const props = defineProps<{ networkContextId: string | null }>()
const route = useRoute()
const filterTaskId = computed(() => {
  const raw = route.query.taskId
  return typeof raw === 'string' && raw.trim() ? raw.trim() : null
})
const items = ref<NetworkPortalExceptionItem[]>([])
const error = ref<string | null>(null)
const loading = ref(false)

const summaryItems = computed<SummaryStripItem[]>(() => {
  const high = items.value.filter((item) => item.severity === 'HIGH' || item.severity === 'CRITICAL').length
  return [
    {
      key: 'open',
      label: '待处理异常',
      value: items.value.length,
      testId: 'exceptions-summary-open',
      tone: items.value.length > 0 ? 'warning' : 'default',
    },
    {
      key: 'high',
      label: '高危 / 严重',
      value: high,
      testId: 'exceptions-summary-high',
      tone: high > 0 ? 'critical' : 'default',
    },
    {
      key: 'note',
      label: '处理原则',
      value: '深链原动作',
      hint: '不提供空 ACK',
      testId: 'exceptions-summary-policy',
    },
  ]
})

/** 仅根据服务端 category/errorCode 选择既有领域入口，不发明“已处理”动作。 */
function suggestedAction(item: NetworkPortalExceptionItem): {
  label: string
  to: string | { path: string; query?: Record<string, string> }
} {
  const haystack = `${item.category} ${item.errorCode} ${item.sourceType}`.toUpperCase()
  if (haystack.includes('QUALIFICATION')) {
    return { label: '更新资质', to: '/network-portal/qualifications' }
  }
  if (haystack.includes('CORRECTION') || haystack.includes('EVIDENCE') || haystack.includes('PHOTO')) {
    return { label: '处理整改', to: '/network-portal/corrections' }
  }
  if (haystack.includes('APPOINT') || haystack.includes('CONTACT')) {
    return {
      label: '联系 / 预约',
      to: item.taskId
        ? { path: '/network-portal/tasks', query: { taskId: item.taskId } }
        : '/network-portal/tasks',
    }
  }
  if (
    haystack.includes('ASSIGN') ||
    haystack.includes('DISPATCH') ||
    haystack.includes('TECHNICIAN')
  ) {
    return {
      label: '分配师傅',
      to: item.taskId
        ? { path: '/network-portal/tasks', query: { taskId: item.taskId } }
        : '/network-portal/tasks',
    }
  }
  if (item.workOrderId) {
    return {
      label: '打开工单工作区',
      to: `/network-portal/work-orders/${item.workOrderId}`,
    }
  }
  return { label: '打开任务队列', to: '/network-portal/tasks' }
}

async function load() {
  if (!props.networkContextId) {
    items.value = []
    error.value = '请选择 NETWORK 上下文'
    loading.value = false
    return
  }
  loading.value = true
  try {
    const page = await listNetworkPortalExceptions(props.networkContextId, {
      status: 'OPEN',
      taskId: filterTaskId.value ?? undefined,
    })
    items.value = page.items
    error.value = null
  } catch (err) {
    items.value = []
    error.value = safeProblemMessage(err) || '异常队列加载失败'
  } finally {
    loading.value = false
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
  <section
    data-testid="network-portal-exceptions"
    data-page-id="NETWORK.EXCEPTION.QUEUE"
    class="queue-page"
  >
    <header class="hero">
      <div>
        <p class="eyebrow">异常中心</p>
        <h2>本网点异常</h2>
        <p class="hint">
          围绕可执行的业务动作处理异常；Portal 不提供没有业务效果的“标记已处理”。
        </p>
      </div>
      <button type="button" data-testid="exceptions-reload" @click="load">刷新</button>
    </header>

    <p
      v-if="filterTaskId"
      class="filter"
      data-testid="exceptions-task-filter"
    >
      已按任务过滤
      <RouterLink to="/network-portal/exceptions" data-testid="exceptions-clear-task-filter">
        清除
      </RouterLink>
    </p>

    <PageState v-if="loading && !items.length && !error" kind="loading" />
    <p v-else-if="error" data-testid="network-portal-error">{{ error }}</p>
    <template v-else>
      <SummaryStrip :items="summaryItems" />

      <div class="panel">
        <table data-testid="network-exceptions-table">
          <thead>
            <tr>
              <th>异常</th>
              <th>项目</th>
              <th>工单</th>
              <th>类别</th>
              <th>严重度</th>
              <th>状态</th>
              <th>次数</th>
              <th>打开 / 最近</th>
              <th>建议动作</th>
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
                  打开异常详情
                </RouterLink>
                <div class="muted">{{ statusLabel(item.errorCode) }}</div>
              </td>
              <td data-testid="exception-project-id">{{ item.projectId ?? '—' }}</td>
              <td>
                <RouterLink
                  v-if="item.workOrderId"
                  :to="`/network-portal/work-orders/${item.workOrderId}`"
                  data-testid="exception-work-order-deeplink"
                >
                  打开工作区
                </RouterLink>
                <span v-else>—</span>
              </td>
              <td data-testid="exception-source-category">
                {{ statusLabel(item.sourceType) }} / {{ item.category }}
              </td>
              <td>{{ item.severity ? statusLabel(item.severity) : '—' }}</td>
              <td>{{ item.status ? statusLabel(item.status) : '—' }}</td>
              <td data-testid="exception-occurrence-count">{{ item.occurrenceCount }}</td>
              <td data-testid="exception-opened-last">
                {{ formatDateTime(item.openedAt) }} / {{ formatDateTime(item.lastDetectedAt) }}
              </td>
              <td class="actions">
                <RouterLink :to="suggestedAction(item).to" data-testid="exception-suggested-action">
                  {{ suggestedAction(item).label }}
                </RouterLink>
                <RouterLink
                  v-if="item.handlingTaskId"
                  :to="{ path: '/network-portal/tasks', query: { taskId: item.handlingTaskId } }"
                  data-testid="exception-handling-task-deeplink"
                >
                  处理任务
                </RouterLink>
                <RouterLink
                  v-if="item.taskId"
                  :to="{ path: '/network-portal/tasks', query: { taskId: item.taskId } }"
                  data-testid="exception-task-deeplink"
                >
                  打开任务
                </RouterLink>
              </td>
            </tr>
          </tbody>
        </table>
        <PageState
          v-if="!items.length"
          kind="empty"
          guide="当前没有待处理运营异常。出现派单、预约、联系或资料风险时将汇聚到此。"
        />
      </div>
    </template>
  </section>
</template>

<style scoped>
.queue-page {
  display: grid;
  gap: 14px;
}
.hero {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}
.eyebrow {
  margin: 0 0 4px;
  color: var(--sos-primary-600);
  font-size: 12px;
  letter-spacing: 0.08em;
}
.hero h2 {
  margin: 0 0 6px;
  font-size: 22px;
}
.hint,
.filter,
.muted {
  color: var(--sos-color-text-tertiary);
  font-size: 13px;
}
.filter a {
  margin-left: 0.5rem;
}
.panel {
  border: 1px solid var(--sos-color-border-default);
  border-radius: var(--sos-radius-md);
  background: var(--sos-color-surface-card);
  padding: 8px 12px 12px;
  overflow: auto;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th,
td {
  text-align: left;
  padding: 0.55rem 0.45rem;
  border-bottom: 1px solid var(--sos-color-border-light);
  font-size: 13px;
  vertical-align: top;
}
.actions {
  display: grid;
  gap: 6px;
}
button {
  border: 1px solid var(--sos-color-border-default);
  background: #fff;
  border-radius: 6px;
  padding: 0.4rem 0.75rem;
  cursor: pointer;
}
</style>
