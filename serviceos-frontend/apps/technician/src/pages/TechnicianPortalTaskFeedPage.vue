<script setup lang="ts">
import { statusLabel } from '../product/labels'
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import {
  listTechnicianCorrections,
  listTechnicianTaskFeed,
  type TechnicianCorrection,
  type TechnicianPortalFeedItem,
} from '../api/technicianPortal'
import { userFacingError } from '../api/client'
import { formatDateTime } from '../product/labels'

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

const assignmentItems = computed(() => items.value.filter((item) => item.itemType === 'ASSIGNMENT'))
const urgentCount = computed(
  () =>
    assignmentItems.value.filter((item) =>
      ['WAITING_CORRECTION', 'READY', 'ASSIGNED'].includes(String(item.taskStatus || '')),
    ).length,
)

function nextActionLabel(item: TechnicianPortalFeedItem) {
  if (item.clientCapabilityUnsupportedDetail) return '更换客户端'
  if (item.itemType !== 'ASSIGNMENT') return '查看说明'
  const status = String(item.taskStatus || '')
  if (status === 'WAITING_CORRECTION') return '处理整改'
  if (status === 'IN_PROGRESS' || status === 'CHECKED_IN') return '继续作业'
  if (status === 'READY' || status === 'ASSIGNED' || status === 'PENDING') return '开始处理'
  return '打开任务'
}

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
    const page = await listTechnicianTaskFeed(props.technicianContextId, options?.sinceCursor)
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
watch(
  () => props.technicianContextId,
  () => {
    void load()
    void loadCorrections()
  },
)
</script>

<template>
  <section
    data-testid="technician-portal-task-feed"
    data-page-id="TECHNICIAN.TASK.LIST"
    class="feed-page"
  >
    <header class="top">
      <div>
        <p class="eyebrow">今日任务</p>
        <h2>我的作业</h2>
        <p class="hint">当前任务、紧急事项和下一步动作。</p>
      </div>
      <button type="button" class="ghost" data-testid="technician-feed-refresh" @click="load()">
        刷新
      </button>
    </header>

    <section class="summary" data-testid="technician-feed-summary" aria-label="今日概览">
      <article>
        <span>今日任务</span>
        <strong data-testid="technician-feed-count-today">{{ assignmentItems.length }}</strong>
      </article>
      <article data-tone="warning">
        <span>待处理 / 紧急</span>
        <strong data-testid="technician-feed-count-urgent">{{ urgentCount }}</strong>
      </article>
      <article data-tone="critical">
        <span>待整改</span>
        <strong data-testid="technician-feed-count-corrections">{{ corrections.length }}</strong>
      </article>
    </section>

    <section class="corrections" data-testid="technician-corrections">
      <div class="correction-heading">
        <h3>待整改任务</h3>
        <button type="button" class="ghost" data-testid="technician-corrections-refresh" @click="loadCorrections">
          刷新整改
        </button>
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
          <p
            v-if="correction.clientCapabilityUnsupportedDetail"
            class="capability-warn"
            :data-testid="`technician-correction-capability-${correction.correctionCaseId}`"
          >
            {{ correction.clientCapabilityUnsupportedDetail }}
          </p>
        </div>
        <RouterLink
          v-if="!correction.clientCapabilityUnsupportedDetail"
          class="primary-link"
          :to="`/technician-portal/corrections/${correction.correctionCaseId}`"
        >
          查看整改要求
        </RouterLink>
        <span
          v-else
          class="capability-blocked"
          :data-testid="`technician-correction-blocked-${correction.correctionCaseId}`"
        >
          当前客户端无法履约，请更换兼容端
        </span>
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

      <div class="card-list" data-testid="technician-feed-table">
        <article
          v-for="item in items"
          :key="item.cursor"
          class="task-card"
          :data-testid="`technician-feed-row-${item.taskId}`"
        >
          <header>
            <span class="badge">{{ item.itemType === 'ASSIGNMENT' ? '责任任务' : statusLabel(item.itemType) }}</span>
            <span class="status">{{ statusLabel(item.taskStatus) }}</span>
          </header>
          <h3>
            {{ item.taskType ? statusLabel(item.taskType) : '现场任务' }}
            <small data-testid="technician-feed-task-type">{{ item.taskType || '—' }}</small>
          </h3>
          <p class="line">
            阶段
            <span data-testid="technician-feed-stage-code">{{ item.stageCode || '—' }}</span>
            · 种类
            <span data-testid="technician-feed-task-kind">{{ item.taskKind || '—' }}</span>
            · 业务
            <span data-testid="technician-feed-business-type">{{ item.businessType || '—' }}</span>
          </p>
          <p class="line">
            生效
            <span data-testid="technician-feed-effective-from">{{ formatDateTime(item.effectiveFrom) }}</span>
          </p>
          <p class="line muted">
            项目 <span data-testid="technician-feed-project-id">{{ item.projectId ?? '—' }}</span>
            · {{ item.workOrderId ? '已关联工单' : '无关联工单' }}
          </p>
          <p
            v-if="item.clientCapabilityUnsupportedDetail"
            class="capability-block"
            :data-testid="`technician-feed-capability-unsupported-${item.taskId}`"
          >
            {{ item.clientCapabilityUnsupportedDetail }}
          </p>
          <p v-else-if="item.invalidationReason" class="muted">{{ item.invalidationReason }}</p>

          <footer class="card-actions">
            <template v-if="item.itemType === 'ASSIGNMENT' && item.clientCapabilityUnsupportedDetail">
              <span class="capability-blocked" data-testid="technician-feed-capability-blocked">
                当前客户端无法履约
              </span>
              <span data-testid="technician-feed-task-detail-blocked">不可打开</span>
            </template>
            <template v-else-if="item.itemType === 'ASSIGNMENT'">
              <RouterLink
                class="primary"
                :to="`/technician-portal/tasks/${item.taskId}`"
                data-testid="technician-feed-task-detail-deeplink"
              >
                {{ nextActionLabel(item) }}
              </RouterLink>
              <RouterLink
                :to="{ path: '/technician-portal/schedule', query: { taskId: item.taskId } }"
                data-testid="technician-feed-schedule-deeplink"
              >
                查看日程
              </RouterLink>
            </template>
            <span v-else>查看</span>
          </footer>
        </article>
      </div>

      <p v-if="items.length === 0" data-testid="technician-feed-empty">
        当前没有待办任务。请等待网点指派，或刷新后重试。
      </p>
      <button
        v-if="nextCursor"
        type="button"
        class="ghost"
        :disabled="loadingMore"
        data-testid="technician-feed-load-more"
        @click="loadMore"
      >
        加载增量
      </button>
      <p class="muted gap-note">
        UI_DATA_GAP：客户脱敏姓名/电话、地址摘要、距离与 SLA 倒计时尚未由 Feed 正式读模型交付；H5 不伪造这些字段。
      </p>
    </template>
  </section>
</template>

<style scoped>
.feed-page {
  display: grid;
  gap: 12px;
}
.top {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: flex-start;
}
.eyebrow {
  margin: 0 0 4px;
  color: var(--sos-primary-600);
  font-size: 12px;
  letter-spacing: 0.08em;
}
.top h2 {
  margin: 0 0 4px;
  font-size: 22px;
}
.hint,
.meta,
.muted,
.gap-note,
.line {
  color: var(--sos-color-text-tertiary);
  font-size: 0.9rem;
}
.summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}
.summary article {
  border: 1px solid var(--sos-color-border-default);
  border-radius: 12px;
  background: #fff;
  padding: 10px;
  display: grid;
  gap: 4px;
}
.summary article span {
  font-size: 12px;
  color: var(--sos-color-text-secondary);
}
.summary article strong {
  font-size: 22px;
}
.summary article[data-tone='warning'] {
  border-color: var(--sos-color-status-warning-border);
  background: var(--sos-color-status-warning-bg);
}
.summary article[data-tone='critical'] {
  border-color: var(--sos-color-status-critical-border);
  background: var(--sos-color-status-critical-bg);
}
.corrections {
  margin: 0;
  padding: 0.9rem;
  border: 1px solid var(--sos-color-status-warning-border);
  border-radius: 0.75rem;
  background: var(--sos-color-status-warning-bg);
}
.correction-heading,
.correction-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}
.correction-heading h3,
.correction-card p {
  margin: 0;
}
.correction-card {
  padding: 0.7rem 0;
  border-top: 1px solid #f3dfbd;
}
.capability-warn,
.capability-block,
.capability-blocked {
  color: #9a3412;
  font-size: 0.88rem;
}
.meta {
  display: grid;
  gap: 0.25rem;
  margin: 0;
}
.meta dd {
  margin: 0 0 0.25rem;
  font-size: 0.85rem;
  word-break: break-all;
}
.card-list {
  display: grid;
  gap: 10px;
}
.task-card {
  border: 1px solid var(--sos-color-border-default);
  border-radius: 12px;
  background: #fff;
  padding: 12px;
  display: grid;
  gap: 6px;
}
.task-card header {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}
.badge,
.status {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--sos-primary-100);
  color: var(--sos-primary-800);
}
.task-card h3 {
  margin: 0;
  font-size: 17px;
}
.task-card h3 small {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: var(--sos-color-text-tertiary);
  font-weight: 500;
}
.card-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-top: 6px;
}
.card-actions a,
.primary-link {
  min-height: var(--sos-touch-min);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  text-decoration: none;
  border: 1px solid var(--sos-color-border-default);
  color: var(--sos-color-text-primary);
  font-weight: 600;
}
.card-actions a.primary {
  background: var(--sos-primary-600);
  border-color: var(--sos-primary-600);
  color: #fff;
}
button.ghost {
  border: 1px solid var(--sos-color-border-default);
  background: #fff;
  border-radius: 10px;
  min-height: 40px;
  padding: 0 12px;
  cursor: pointer;
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  border: 0;
}
</style>
