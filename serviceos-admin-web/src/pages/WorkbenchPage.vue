<script setup lang="ts">
/**
 * ADMIN.WORKBENCH — 平台运营工作台。
 * 每张卡片独立请求：单卡失败不影响整页，禁止整体白屏。
 */
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import PageState from '../components/PageState.vue'
import StatusBadge from '../components/StatusBadge.vue'
import WorkbenchPageLayout from '../patterns/templates/WorkbenchPageLayout.vue'
import SummaryStrip, { type SummaryStripItem } from '../patterns/SummaryStrip.vue'
import { Button } from 'ant-design-vue'
import { listReviewCases, listCorrectionCases, listOperationalExceptions } from '../api/queues'
import { listAuthorizedWorkOrders } from '../api/workOrders'
import { listSlaInstances } from '../api/sla'
import {
  formatFollowedBadgeCount,
  listFollowedProjects,
  unfollowProject,
  type FollowedProjectItem,
} from '../api/followedProjects'
import { toUserFacingError } from '../product/errorMessages'
import { formatDateTime, formatRemainingSeconds } from '../product/formatTime'
import { statusLabel } from '../product/statusLabels'
import { labelClientCode } from '../presentation/enum-labels'

type CardState<T> = {
  loading: boolean
  error: string | null
  errorCode: string | null
  data: T | null
}

function emptyCard<T>(): CardState<T> {
  return { loading: true, error: null, errorCode: null, data: null }
}

type TodoItem = {
  id: string
  workOrderLabel: string
  projectLabel: string
  stage: string
  status: string
  remaining: string
  nextAction: string
  to: { name: string; params?: Record<string, string>; query?: Record<string, string> }
}

const pageBootError = ref<string | null>(null)
const reviews = ref(emptyCard<{ count: number; items: TodoItem[] }>())
const workOrders = ref(emptyCard<{ countLabel: string }>())
const corrections = ref(emptyCard<{ count: number; items: TodoItem[] }>())
const slaRisk = ref(emptyCard<{ running: number; breached: number; items: TodoItem[] }>())
const exceptions = ref(emptyCard<{ count: number }>())
const recent = ref(emptyCard<{ items: TodoItem[] }>())
const followed = ref(emptyCard<{ items: FollowedProjectItem[] }>())
const unfollowBusyId = ref<string | null>(null)

async function loadCard<T>(
  target: { value: CardState<T> },
  loader: () => Promise<T>,
) {
  target.value = { loading: true, error: null, errorCode: null, data: null }
  try {
    const data = await loader()
    target.value = { loading: false, error: null, errorCode: null, data }
  } catch (err) {
    const facing = toUserFacingError(err)
    target.value = {
      loading: false,
      error: facing.message,
      errorCode: facing.errorCode,
      data: null,
    }
  }
}

async function loadReviews() {
  await loadCard(reviews, async () => {
    const page = await listReviewCases({ status: 'OPEN', limit: '5' })
    const items: TodoItem[] = page.items.map((item) => ({
      id: item.reviewCaseId,
      workOrderLabel: `审核单 ${item.reviewCaseId.slice(0, 8)}…`,
      projectLabel: item.projectId.slice(0, 8) + '…',
      stage: '待审核资料',
      status: item.status,
      remaining: '—',
      nextAction: '进入审核',
      to: { name: 'ADMIN.REVIEW.DETAIL', params: { id: item.reviewCaseId } },
    }))
    return { count: page.items.length, items }
  })
}

async function loadWorkOrders() {
  await loadCard(workOrders, async () => {
    const page = await listAuthorizedWorkOrders({ status: 'ACTIVE', limit: '20' })
    // 游标分页无 total：有下一页时显示「N+」提示仍有更多
    const countLabel = page.nextCursor
      ? `${page.items.length}+`
      : String(page.items.length)
    return { countLabel }
  })
}

async function loadCorrections() {
  await loadCard(corrections, async () => {
    const page = await listCorrectionCases({ status: 'IN_PROGRESS', limit: '5' })
    const items: TodoItem[] = page.items.map((item) => ({
      id: item.correctionCaseId,
      workOrderLabel: `整改单 ${item.correctionCaseId.slice(0, 8)}…`,
      projectLabel: item.projectId.slice(0, 8) + '…',
      stage: '整改处理',
      status: item.status,
      remaining: '—',
      nextAction: '查看整改',
      to: { name: 'ADMIN.CORRECTION.DETAIL', params: { id: item.correctionCaseId } },
    }))
    return { count: page.items.length, items }
  })
}

async function loadSla() {
  await loadCard(slaRisk, async () => {
    const [runningPage, breachedPage] = await Promise.all([
      listSlaInstances({ status: 'RUNNING', limit: '5' }),
      listSlaInstances({ status: 'BREACHED', limit: '5' }),
    ])
    const items: TodoItem[] = [
      ...breachedPage.items.map((item) => ({
        id: item.slaInstanceId,
        workOrderLabel: `工单 ${item.workOrderId.slice(0, 8)}…`,
        projectLabel: item.projectId.slice(0, 8) + '…',
        stage: '服务时效',
        status: item.status,
        remaining: formatRemainingSeconds(item.remainingSeconds),
        nextAction: '查看时效',
        to: { name: 'ADMIN.SLA.DETAIL', params: { id: item.slaInstanceId } },
      })),
      ...runningPage.items.map((item) => ({
        id: item.slaInstanceId,
        workOrderLabel: `工单 ${item.workOrderId.slice(0, 8)}…`,
        projectLabel: item.projectId.slice(0, 8) + '…',
        stage: '服务时效',
        status: item.status,
        remaining: formatRemainingSeconds(item.remainingSeconds),
        nextAction: '查看时效',
        to: { name: 'ADMIN.SLA.DETAIL', params: { id: item.slaInstanceId } },
      })),
    ].slice(0, 8)
    return {
      running: runningPage.items.length,
      breached: breachedPage.items.length,
      items,
    }
  })
}

async function loadExceptions() {
  await loadCard(exceptions, async () => {
    const page = await listOperationalExceptions({ status: 'OPEN', limit: '1' })
    return { count: page.items.length + (page.nextCursor ? 1 : 0) }
  })
}

async function loadRecent() {
  await loadCard(recent, async () => {
    const page = await listAuthorizedWorkOrders({ limit: '5' })
    const items: TodoItem[] = page.items.map((item) => ({
      id: item.id,
      workOrderLabel: item.externalOrderCode || `工单 ${item.id.slice(0, 8)}…`,
      projectLabel: item.clientCode || '—',
      stage: '工单履约',
      status: item.status,
      remaining: formatDateTime(item.receivedAt),
      nextAction: '打开工单',
      to: { name: 'ADMIN.WORKORDER.WORKSPACE', params: { id: item.id } },
    }))
    return { items }
  })
}

async function loadFollowed() {
  await loadCard(followed, async () => {
    const page = await listFollowedProjects(10)
    return { items: page.items }
  })
}

async function removeFollow(projectId: string) {
  unfollowBusyId.value = projectId
  try {
    await unfollowProject(projectId)
    await loadFollowed()
  } catch (err) {
    const facing = toUserFacingError(err)
    followed.value = {
      ...followed.value,
      error: facing.message,
      errorCode: facing.errorCode,
    }
  } finally {
    unfollowBusyId.value = null
  }
}

function followedBadgeAria(item: FollowedProjectItem): string {
  const parts: string[] = []
  const todo = formatFollowedBadgeCount(item.openTodoCount, null)
  if (todo != null) {
    parts.push(`待办 ${todo}`)
  }
  const sla = formatFollowedBadgeCount(item.slaBreachedCount, item.slaBreachedCountTruncated)
  if (sla != null) {
    parts.push(`SLA 超时 ${sla}`)
  }
  const wo = formatFollowedBadgeCount(item.activeWorkOrderCount, item.activeWorkOrderCountTruncated)
  if (wo != null) {
    parts.push(`进行中工单 ${wo}`)
  }
  return parts.length > 0 ? parts.join('，') : '暂无角标'
}

async function loadAll() {
  pageBootError.value = null
  await Promise.allSettled([
    loadReviews(),
    loadWorkOrders(),
    loadCorrections(),
    loadSla(),
    loadExceptions(),
    loadRecent(),
    loadFollowed(),
  ])
}

const summaryItems = computed<SummaryStripItem[]>(() => [
  {
    key: 'todos',
    label: '我的待办',
    value: String(
      (reviews.value.data?.count ?? 0) +
        (corrections.value.data?.count ?? 0) +
        (slaRisk.value.data?.breached ?? 0),
    ),
    hint: '审核 + 整改 + 已超时',
    tone: 'info',
  },
  {
    key: 'breached',
    label: '已超时',
    value: String(slaRisk.value.data?.breached ?? '—'),
    tone: (slaRisk.value.data?.breached ?? 0) > 0 ? 'critical' : 'default',
  },
  {
    key: 'exceptions',
    label: '重大异常',
    value: String(exceptions.value.data?.count ?? '—'),
    tone: (exceptions.value.data?.count ?? 0) > 0 ? 'warning' : 'default',
  },
  {
    key: 'active',
    label: '处理中工单',
    value: workOrders.value.data?.countLabel ?? '—',
  },
])

onMounted(() => {
  void loadAll()
})
</script>

<template>
  <div data-testid="admin-workbench">
  <WorkbenchPageLayout
    title="运营工作台"
    description="优先处理待办、即将超时与已超时事项。任一队列失败不会拖垮整页。"
  >
    <template #secondary-actions>
      <Button data-testid="workbench-reload" @click="loadAll">刷新全部</Button>
      <RouterLink class="link-btn" to="/work-orders/golden-path" data-testid="workbench-golden-path">
        工单全流程演练
      </RouterLink>
      <RouterLink class="link-btn" to="/system/demo-data" data-testid="workbench-demo-data">
        演示数据
      </RouterLink>
    </template>
    <template #feedback>
      <PageState v-if="pageBootError" kind="error" :description="pageBootError" @reload="loadAll" />
    </template>

    <template #summary>
      <SummaryStrip :items="summaryItems" />
    </template>

    <template #primary-queue>
      <section class="todo" data-testid="workbench-todos">
        <PageState
          v-if="!reviews.loading && !corrections.loading && !slaRisk.loading
            && !(reviews.data?.items.length || corrections.data?.items.length || slaRisk.data?.items.length)"
          kind="empty"
          compact
          guide="当前没有待办。可到「演示数据」初始化演示工单，或打开「工单全流程演练」按步骤操作。"
        />
        <table v-else-if="reviews.data || corrections.data || slaRisk.data">
          <thead>
            <tr>
              <th>业务编号</th>
              <th>项目/车企</th>
              <th>当前环节</th>
              <th>状态</th>
              <th>剩余处理时间</th>
              <th>下一步动作</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in [
                ...(reviews.data?.items ?? []),
                ...(corrections.data?.items ?? []),
                ...(slaRisk.data?.items ?? []),
              ]"
              :key="item.id"
            >
              <td>{{ item.workOrderLabel }}</td>
              <td>{{ item.projectLabel }}</td>
              <td>{{ item.stage }}</td>
              <td><StatusBadge :status="item.status" /></td>
              <td>{{ item.remaining }}</td>
              <td>{{ item.nextAction }}</td>
              <td>
                <RouterLink :to="item.to">处理</RouterLink>
              </td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>

    <template #risk-queue>
      <div class="cards" data-testid="workbench-cards">
      <article class="card" data-testid="workbench-card-reviews">
        <h2>待审核资料</h2>
        <PageState v-if="reviews.loading" kind="loading" compact />
        <PageState
          v-else-if="reviews.error"
          kind="error"
          compact
          :description="reviews.error"
          :error-code="reviews.errorCode ?? undefined"
          @reload="loadReviews"
        />
        <template v-else-if="reviews.data">
          <p class="metric">
            <RouterLink to="/reviews?status=OPEN">{{ reviews.data.count }}</RouterLink>
          </p>
          <p class="hint">下一步：审核师傅提交的勘测或完工资料</p>
        </template>
      </article>

      <article class="card" data-testid="workbench-card-work-orders">
        <h2>处理中工单</h2>
        <PageState v-if="workOrders.loading" kind="loading" compact />
        <PageState
          v-else-if="workOrders.error"
          kind="error"
          compact
          :description="workOrders.error"
          :error-code="workOrders.errorCode ?? undefined"
          @reload="loadWorkOrders"
        />
        <template v-else-if="workOrders.data">
          <p class="metric">
            <RouterLink to="/work-orders?status=ACTIVE">{{ workOrders.data.countLabel }}</RouterLink>
          </p>
          <p class="hint">进入工单中心继续分配、审核或跟踪</p>
        </template>
      </article>

      <article class="card" data-testid="workbench-card-corrections">
        <h2>待整改工单</h2>
        <PageState v-if="corrections.loading" kind="loading" compact />
        <PageState
          v-else-if="corrections.error"
          kind="error"
          compact
          :description="corrections.error"
          :error-code="corrections.errorCode ?? undefined"
          @reload="loadCorrections"
        />
        <template v-else-if="corrections.data">
          <p class="metric">
            <RouterLink to="/corrections">{{ corrections.data.count }}</RouterLink>
          </p>
          <p class="hint">下一步：跟踪整改进度或重新审核</p>
        </template>
      </article>

      <article class="card" data-testid="workbench-card-sla">
        <h2>服务时效风险</h2>
        <PageState v-if="slaRisk.loading" kind="loading" compact />
        <PageState
          v-else-if="slaRisk.error"
          kind="error"
          compact
          :description="slaRisk.error"
          :error-code="slaRisk.errorCode ?? undefined"
          @reload="loadSla"
        />
        <template v-else-if="slaRisk.data">
          <p class="metric">
            <RouterLink to="/sla?status=BREACHED">
              已超时 {{ slaRisk.data.breached }}
            </RouterLink>
            <span class="muted"> / 计时中 {{ slaRisk.data.running }}</span>
          </p>
          <p class="hint">优先处理已超时任务，避免客户投诉</p>
        </template>
      </article>

      <article class="card" data-testid="workbench-card-exceptions">
        <h2>运营异常</h2>
        <PageState v-if="exceptions.loading" kind="loading" compact />
        <PageState
          v-else-if="exceptions.error"
          kind="error"
          compact
          :description="exceptions.error"
          :error-code="exceptions.errorCode ?? undefined"
          @reload="loadExceptions"
        />
        <template v-else-if="exceptions.data">
          <p class="metric">
            <RouterLink to="/exceptions?status=OPEN">{{ exceptions.data.count }}</RouterLink>
          </p>
          <p class="hint">查看并确认需要人工介入的异常</p>
        </template>
      </article>
      </div>
    </template>

    <template #today-queue>
      <article class="card" data-testid="workbench-card-work-orders-today">
        <p class="metric">
          <RouterLink to="/work-orders?status=ACTIVE">
            {{ workOrders.data?.countLabel ?? '—' }}
          </RouterLink>
        </p>
        <p class="hint">今日继续跟进的处理中工单</p>
      </article>
    </template>

    <template #recent-activity>
      <section class="recent" data-testid="workbench-recent-list">
        <PageState v-if="recent.loading" kind="loading" compact />
        <PageState
          v-else-if="recent.error"
          kind="error"
          compact
          :description="recent.error"
          :error-code="recent.errorCode ?? undefined"
          @reload="loadRecent"
        />
        <PageState
          v-else-if="recent.data && recent.data.items.length === 0"
          kind="empty"
          compact
          guide="还没有工单。请先初始化演示数据，或等待车企入站创建工单。"
        />
        <ul v-else-if="recent.data">
          <li v-for="item in recent.data.items" :key="item.id">
            <RouterLink :to="item.to">{{ item.workOrderLabel }}</RouterLink>
            <StatusBadge :status="item.status" />
            <span class="muted">{{ statusLabel(item.status) }} · {{ item.remaining }}</span>
          </li>
        </ul>
      </section>
    </template>

    <template #followed-projects>
      <section class="followed" data-testid="workbench-followed-list">
        <PageState v-if="followed.loading" kind="loading" compact />
        <PageState
          v-else-if="followed.error"
          kind="error"
          compact
          :description="followed.error"
          :error-code="followed.errorCode ?? undefined"
          @reload="loadFollowed"
        />
        <PageState
          v-else-if="followed.data && followed.data.items.length === 0"
          kind="empty"
          compact
          guide="尚未关注项目。打开项目详情后可点击「关注项目」。"
        />
        <ul v-else-if="followed.data">
          <li v-for="item in followed.data.items" :key="item.projectId">
            <RouterLink
              :to="{ name: 'ADMIN.PROJECT.DETAIL', params: { id: item.projectId } }"
              data-testid="workbench-followed-link"
            >
              {{ item.displayRef }}
            </RouterLink>
            <StatusBadge v-if="item.status" :status="item.status" />
            <span class="muted">
              {{ item.projectCode || '—' }}
              · {{ labelClientCode(item.clientId) }}
            </span>
            <span
              class="followed-badges"
              data-testid="workbench-followed-badges"
              :aria-label="followedBadgeAria(item)"
            >
              <span
                v-if="formatFollowedBadgeCount(item.openTodoCount, null) != null"
                class="badge badge-todo"
                data-testid="workbench-followed-todo"
              >待办 {{ formatFollowedBadgeCount(item.openTodoCount, null) }}</span>
              <span
                v-if="formatFollowedBadgeCount(item.slaBreachedCount, item.slaBreachedCountTruncated) != null"
                class="badge badge-sla"
                data-testid="workbench-followed-sla"
              >SLA {{ formatFollowedBadgeCount(item.slaBreachedCount, item.slaBreachedCountTruncated) }}</span>
              <span
                v-if="formatFollowedBadgeCount(item.activeWorkOrderCount, item.activeWorkOrderCountTruncated) != null"
                class="badge badge-wo"
                data-testid="workbench-followed-work-orders"
              >工单 {{ formatFollowedBadgeCount(item.activeWorkOrderCount, item.activeWorkOrderCountTruncated) }}</span>
            </span>
            <Button
              size="small"
              type="link"
              :loading="unfollowBusyId === item.projectId"
              data-testid="workbench-followed-unfollow"
              @click="removeFollow(item.projectId)"
            >
              取消关注
            </Button>
          </li>
        </ul>
      </section>
    </template>
  </WorkbenchPageLayout>
  </div>
</template>

<style scoped>
.workbench {
  display: grid;
  gap: 1.25rem;
}
.hero {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
  align-items: flex-start;
}
h1 {
  margin: 0;
  font-size: 1.5rem;
}
.subtitle,
.hint,
.muted {
  color: var(--sos-color-text-secondary, #4b5563);
  font-size: 0.92rem;
}
.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}
button,
.link-btn {
  border: 1px solid #bcccdc;
  background: #f0f4f8;
  border-radius: 6px;
  padding: 0.45rem 0.85rem;
  cursor: pointer;
  color: inherit;
  text-decoration: none;
  font-size: 0.9rem;
}
.cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 0.85rem;
}
.card {
  background: #fff;
  border-radius: 12px;
  padding: 1rem;
  box-shadow: 0 1px 3px rgb(16 42 67 / 8%);
  min-height: 8rem;
}
.card h2 {
  margin: 0 0 0.5rem;
  font-size: 1rem;
}
.metric {
  font-size: 1.75rem;
  font-weight: 700;
  margin: 0.25rem 0;
}
.metric a {
  color: #0b69a3;
  text-decoration: none;
}
.todo,
.recent,
.followed {
  background: transparent;
  border-radius: 0;
  padding: 0;
  box-shadow: none;
}
.followed-badges {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  align-items: center;
}
.followed-badges .badge {
  display: inline-block;
  border-radius: 999px;
  padding: 0.1rem 0.45rem;
  font-size: 0.75rem;
  line-height: 1.3;
  border: 1px solid #d0d7de;
  background: #f6f8fa;
  color: #24292f;
}
.followed-badges .badge-todo {
  border-color: #9ec5fe;
  background: #eef5ff;
  color: #0b69a3;
}
.followed-badges .badge-sla {
  border-color: #f1aeb5;
  background: #fff5f5;
  color: #b42318;
}
.followed-badges .badge-wo {
  border-color: #c4cdd5;
  background: #f8fafc;
  color: #334155;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th,
td {
  text-align: left;
  padding: 0.5rem 0.35rem;
  border-bottom: 1px solid #e2e8f0;
  font-size: 0.9rem;
}
ul {
  list-style: none;
  padding: 0;
  margin: 0;
}
li {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: center;
  padding: 0.45rem 0;
  border-bottom: 1px solid #e2e8f0;
}
</style>
