<script setup lang="ts">
import { statusLabel } from '../product/labels'
import { onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import {
  listTechnicianCorrections,
  listTechnicianTaskFeed,
  type TechnicianCorrection,
  type TechnicianPortalFeedItem,
} from '../api/technicianPortal'
import { userFacingError } from '../api/client'
import {formatDateTime} from '@serviceos/web-core'

const props = defineProps<{ technicianContextId: string | null }>()
const items = ref<TechnicianPortalFeedItem[]>([])
const networkId = ref<string | null>(null)
const asOf = ref<string | null>(null)
const nextCursor = ref<string | null>(null)
const error = ref<string | null>(null)
const loading = ref(false)
const loadingMore = ref(false)
const corrections = ref<TechnicianCorrection[]>([])
const correctionError = ref<string | null>(null)

async function loadCorrections() {
  if (!props.technicianContextId) {
    corrections.value = []
    return
  }
  try {
    corrections.value = await listTechnicianCorrections(props.technicianContextId)
    correctionError.value = null
  } catch (err) {
    corrections.value = []
    correctionError.value = userFacingError(err, '整改任务加载失败')
  }
}

async function load(options?: { append?: boolean; sinceCursor?: string }) {
  if (!props.technicianContextId) {
    items.value = []
    networkId.value = null
    asOf.value = null
    nextCursor.value = null
    error.value = '请选择师傅上下文'
    loading.value = false
    return
  }
  if (!options?.append) {
    loading.value = true
  }
  try {
    const page = await listTechnicianTaskFeed(
      props.technicianContextId,
      options?.sinceCursor,
    )
    items.value = options?.append ? [...items.value, ...page.items] : page.items
    networkId.value = page.networkId
    asOf.value = page.asOf
    nextCursor.value = page.nextCursor
    error.value = null
  } catch (err) {
    if (!options?.append) {
      items.value = []
      networkId.value = null
      asOf.value = null
      nextCursor.value = null
    }
    error.value = userFacingError(err, '今日任务加载失败')
  } finally {
    if (!options?.append) {
      loading.value = false
    }
  }
}

async function loadMore() {
  if (!nextCursor.value || loadingMore.value) {
    return
  }
  loadingMore.value = true
  try {
    await load({ append: true, sinceCursor: nextCursor.value })
  } finally {
    loadingMore.value = false
  }
}

onMounted(() => {
  void load()
  void loadCorrections()
})
watch(() => props.technicianContextId, () => {
  void load()
  void loadCorrections()
})
</script>

<template>
  <section
    data-testid="technician-portal-task-feed"
    data-page-id="TECHNICIAN.TASK.LIST"
  >
    <header class="top">
      <div>
        <h2>今日任务</h2>
        <p class="hint">我下一步需要做什么：联系客户、预约、上门、上传资料、处理整改。</p>
      </div>
      <button type="button" data-testid="technician-feed-refresh" @click="load()">
        刷新
      </button>
    </header>
    <section class="corrections" data-testid="technician-corrections">
      <div class="correction-heading">
        <h3>待整改任务</h3>
        <button type="button" data-testid="technician-corrections-refresh" @click="loadCorrections">刷新整改</button>
      </div>
      <p v-if="correctionError" data-testid="technician-corrections-error">{{ correctionError }}</p>
      <p v-else-if="corrections.length === 0" data-testid="technician-corrections-empty">
        当前没有整改任务，所有资料均已处理完成。
      </p>
      <article
        v-for="correction in corrections"
        v-else
        :key="correction.correctionCaseId"
        class="correction-card"
        :data-testid="`technician-correction-${correction.correctionCaseId}`"
      >
        <div>
          <strong>{{ correction.reasonCodes.map((code) => statusLabel(code)).join(' / ') }}</strong>
          <p>
            整改状态 {{ statusLabel(correction.caseStatus) }}
            · 任务 {{ statusLabel(correction.taskStatus) }}
            · 重新提交 {{ correction.resubmissionCount }} 次
          </p>
        </div>
        <RouterLink :to="`/technician-portal/corrections/${correction.correctionCaseId}`">
          查看整改要求
        </RouterLink>
      </article>
    </section>
    <p v-if="loading" data-testid="technician-feed-loading">正在加载数据，请稍候……</p>
    <p v-else-if="error" data-testid="technician-portal-error">{{ error }}</p>
    <template v-else>
      <dl v-if="asOf" data-testid="technician-feed-meta" class="meta">
        <div>
          <dt>所属网点</dt>
          <dd data-testid="technician-feed-network-id">{{ networkId }}</dd>
        </div>
        <div>
          <dt>统计时间</dt>
          <dd>
            <span data-testid="technician-feed-as-of">{{ asOf }}</span>
            <span class="muted">（{{ formatDateTime(asOf) }}）</span>
          </dd>
        </div>
      </dl>
      <table data-testid="technician-feed-table">
        <thead>
          <tr>
            <th>事项</th>
            <th>任务</th>
            <th>关联工单</th>
            <th>所属项目</th>
            <th>状态</th>
            <th>当前阶段</th>
            <th>任务类型</th>
            <th>任务种类</th>
            <th>业务类型</th>
            <th>生效时间</th>
            <th>说明</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="item in items"
            :key="item.cursor"
            :data-testid="`technician-feed-row-${item.taskId}`"
          >
            <td>{{ item.itemType === 'ASSIGNMENT' ? '责任任务' : statusLabel(item.itemType) }}</td>
            <td>
              <RouterLink
                v-if="item.itemType === 'ASSIGNMENT' && !item.clientCapabilityUnsupportedDetail"
                :to="`/technician-portal/tasks/${item.taskId}`"
                data-testid="technician-feed-task-detail-deeplink"
              >
                打开任务
              </RouterLink>
              <span
                v-else-if="item.itemType === 'ASSIGNMENT'"
                data-testid="technician-feed-task-detail-blocked"
              >不可打开</span>
              <span v-else>查看</span>
            </td>
            <td>{{ item.workOrderId ? '关联工单' : '—' }}</td>
            <td data-testid="technician-feed-project-id">{{ item.projectId ?? '—' }}</td>
            <td>{{ statusLabel(item.taskStatus) }}</td>
            <td data-testid="technician-feed-stage-code">{{ item.stageCode ? statusLabel(item.stageCode) : '—' }}</td>
            <td data-testid="technician-feed-task-type">{{ item.taskType ? statusLabel(item.taskType) : '—' }}</td>
            <td data-testid="technician-feed-task-kind">{{ item.taskKind ? statusLabel(item.taskKind) : '—' }}</td>
            <td data-testid="technician-feed-business-type">{{ item.businessType ? statusLabel(item.businessType) : '—' }}</td>
            <td data-testid="technician-feed-effective-from">
              {{ formatDateTime(item.effectiveFrom) }}
            </td>
            <td>
              <span
                v-if="item.clientCapabilityUnsupportedDetail"
                class="capability-block"
                :data-testid="`technician-feed-capability-unsupported-${item.taskId}`"
              >
                {{ item.clientCapabilityUnsupportedDetail }}
              </span>
              <template v-else>{{ item.invalidationReason ?? '—' }}</template>
            </td>
            <td>
              <template v-if="item.itemType === 'ASSIGNMENT' && item.clientCapabilityUnsupportedDetail">
                <span
                  class="capability-blocked"
                  data-testid="technician-feed-capability-blocked"
                >当前客户端无法履约</span>
              </template>
              <template v-else-if="item.itemType === 'ASSIGNMENT'">
                <RouterLink :to="`/technician-portal/tasks/${item.taskId}`">
                  开始处理
                </RouterLink>
                <RouterLink
                  :to="{ path: '/technician-portal/schedule', query: { taskId: item.taskId } }"
                  data-testid="technician-feed-schedule-deeplink"
                >
                  查看日程
                </RouterLink>
              </template>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="items.length === 0" data-testid="technician-feed-empty">
        当前没有待办任务。请等待网点指派，或刷新后重试。
      </p>
      <button
        v-if="nextCursor"
        type="button"
        :disabled="loadingMore"
        data-testid="technician-feed-load-more"
        @click="loadMore"
      >
        加载增量
      </button>
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
.hint,
.meta {
  color: #5b6573;
  font-size: 0.9rem;
}
.corrections {
  margin: 1rem 0;
  padding: 0.9rem;
  border: 1px solid #f3c780;
  border-radius: 0.75rem;
  background: #fffaf0;
}
.correction-heading,
.correction-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}
.correction-heading h3,
.correction-card p { margin: 0; }
.correction-card { padding: 0.7rem 0; border-top: 1px solid #f3dfbd; }
.meta {
  display: grid;
  gap: 0.25rem;
  margin: 0.75rem 0;
}
.meta dd {
  margin: 0 0 0.25rem;
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
  font-size: 0.8rem;
}
.capability-block {
  color: #9a3412;
  font-size: 0.78rem;
  line-height: 1.35;
}
.capability-blocked {
  color: #9a3412;
  font-size: 0.78rem;
}
</style>
