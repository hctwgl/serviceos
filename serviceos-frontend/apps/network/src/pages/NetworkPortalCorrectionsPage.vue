<script setup lang="ts">
import { statusLabel } from '../product/labels'
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { formatDateTime, safeProblemMessage } from '../product/labels'

import {
  listNetworkPortalCorrections,
  type NetworkPortalCorrectionItem,
} from '../api/networkPortal'
import SummaryStrip, { type SummaryStripItem } from '../components/SummaryStrip.vue'
import PageState from '../components/PageState.vue'

const props = defineProps<{ networkContextId: string | null }>()
const route = useRoute()
const filterTaskId = computed(() => {
  const raw = route.query.taskId
  return typeof raw === 'string' && raw.trim() ? raw.trim() : null
})
const items = ref<NetworkPortalCorrectionItem[]>([])
const error = ref<string | null>(null)
const loading = ref(false)

const summaryItems = computed<SummaryStripItem[]>(() => [
  {
    key: 'open',
    label: '待处理整改',
    value: items.value.length,
    hint: '仅 OPEN 案例',
    testId: 'corrections-summary-open',
    tone: items.value.length > 0 ? 'warning' : 'default',
  },
  {
    key: 'resubmit',
    label: '已补传轮次合计',
    value: items.value.reduce((sum, item) => sum + Number(item.resubmissionCount || 0), 0),
    testId: 'corrections-summary-resubmit',
  },
  {
    key: 'tasks',
    label: '可代补入口',
    value: '任务页',
    hint: '代补不伪装成师傅本人操作',
    to: '/network-portal/tasks',
    testId: 'corrections-summary-tasks',
  },
])

function reasonLabel(codes: string[]) {
  if (!codes.length) return '—'
  return codes.map((code) => statusLabel(code)).join('、')
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
    const page = await listNetworkPortalCorrections(props.networkContextId, {
      status: 'OPEN',
      taskId: filterTaskId.value ?? undefined,
    })
    items.value = page.items
    error.value = null
  } catch (err) {
    items.value = []
    error.value = safeProblemMessage(err) || '整改队列加载失败'
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
    data-testid="network-portal-corrections"
    data-page-id="NETWORK.CORRECTION.QUEUE"
    class="queue-page"
  >
    <header class="hero">
      <div>
        <p class="eyebrow">资料整改</p>
        <h2>本网点整改</h2>
        <p class="hint">
          只处理被驳回项；代补须走服务端 on-behalf 流程。不提供无业务效果的“标记已处理”。
        </p>
      </div>
      <button type="button" data-testid="corrections-reload" @click="load">刷新</button>
    </header>

    <p
      v-if="filterTaskId"
      class="filter"
      data-testid="corrections-task-filter"
    >
      已按任务过滤
      <RouterLink to="/network-portal/corrections" data-testid="corrections-clear-task-filter">
        清除
      </RouterLink>
    </p>

    <PageState v-if="loading && !items.length && !error" kind="loading" />
    <p v-else-if="error" data-testid="network-portal-error">{{ error }}</p>
    <template v-else>
      <SummaryStrip :items="summaryItems" />

      <div class="panel">
        <table data-testid="network-corrections-table">
          <thead>
            <tr>
              <th>整改</th>
              <th>项目</th>
              <th>驳回原因</th>
              <th>状态</th>
              <th>补传次数</th>
              <th>创建时间</th>
              <th>关闭/豁免</th>
              <th>下一步</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in items"
              :key="item.correctionCaseId"
              :data-testid="`correction-row-${item.correctionCaseId}`"
            >
              <td>
                <RouterLink
                  :to="`/network-portal/corrections/${item.correctionCaseId}`"
                  data-testid="correction-case-deeplink"
                >
                  打开整改详情
                </RouterLink>
                <div class="muted">轮次关联任务可代补</div>
              </td>
              <td data-testid="correction-project-id">{{ item.projectId }}</td>
              <td>
                <span data-testid="correction-reason-codes">{{ reasonLabel(item.reasonCodes) }}</span>
                <div class="muted">来源审核
                  <span data-testid="correction-source-review">
                    {{ item.sourceReviewCaseId }} / {{ item.sourceReviewDecisionId }}
                  </span>
                </div>
              </td>
              <td>{{ item.status ? statusLabel(item.status) : '—' }}</td>
              <td data-testid="correction-resubmission-count">{{ item.resubmissionCount }}</td>
              <td>{{ formatDateTime(item.createdAt) }}</td>
              <td data-testid="correction-closed-waived">
                {{ item.closedAt ? formatDateTime(item.closedAt) : '—' }} /
                {{ item.waivedAt ? formatDateTime(item.waivedAt) : '—' }}
              </td>
              <td class="actions">
                <RouterLink
                  v-if="item.correctionTaskId"
                  :to="{ path: '/network-portal/tasks', query: { taskId: item.correctionTaskId } }"
                  data-testid="correction-correction-task-deeplink"
                >
                  整改任务
                </RouterLink>
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
        <PageState
          v-if="!items.length"
          kind="empty"
          guide="当前没有待处理整改。审核驳回后将出现在此，并可进入任务页代补资料。"
        />
      </div>
      <p class="muted gap-note">
        UI_DATA_GAP：正确示例图、截止 SLA、是否允许代补的结构化字段尚未由专用读模型完整交付；当前以任务代补入口承接。
      </p>
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
.muted,
.gap-note {
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
