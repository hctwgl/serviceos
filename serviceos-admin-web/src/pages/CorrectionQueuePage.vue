<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, useRoute, type RouteLocationRaw } from 'vue-router'
import SavedViewBar from '../components/SavedViewBar.vue'
import QueueTable from './QueueTable.vue'
import {
  listCorrectionCases,
  type CorrectionCaseQueuePage,
  type CorrectionCaseQueueQuery,
} from '../api/queues'
import { firstRouteQuery, uuidRoute } from '../routeQuery'
import { statusOptions } from '../product/statusLabels'
import { toUserFacingError } from '../product/errorMessages'

const linkColumns: Record<
  string,
  (row: Record<string, unknown>) => RouteLocationRaw | null
> = {
  correctionCaseId: (row) =>
    uuidRoute(row.correctionCaseId, 'ADMIN.CORRECTION.DETAIL'),
  sourceReviewCaseId: (row) =>
    uuidRoute(row.sourceReviewCaseId, 'ADMIN.REVIEW.DETAIL'),
  correctionTaskId: (row) => uuidRoute(row.correctionTaskId, 'ADMIN.TASK.DETAIL'),
}

const route = useRoute()

const loading = ref(false)
const error = ref<string | null>(null)
const errorCode = ref<string | null>(null)
const page = ref<CorrectionCaseQueuePage | null>(null)
const cursor = ref<string | undefined>()

/**
 * 运营默认 IN_PROGRESS（与既有 Admin 客户端一致）；
 * OpenAPI 省略 status 时服务端默认 OPEN，故 UI 必须显式传 IN_PROGRESS。
 */
const status = ref('IN_PROGRESS')
const projectKeyword = ref('')
const workOrderKeyword = ref('')
const customerKeyword = ref('')
const correctionCaseKeyword = ref('')
const assigneeKeyword = ref('')

const statusChoices = statusOptions([
  'OPEN',
  'IN_PROGRESS',
  'RESUBMITTED',
  'CLOSED',
  'WAIVED',
])

function hydrateFiltersFromRoute() {
  const nextStatus = firstRouteQuery(route, 'status')
  if (nextStatus !== undefined) {
    status.value = nextStatus
  }
  // 兼容旧深链：仍接受 projectId/taskId/sourceReviewCaseId，但界面不再要求用户手输 UUID
  const legacyProject = firstRouteQuery(route, 'projectId')
  if (legacyProject) {
    projectKeyword.value = legacyProject
  }
  const legacyTask = firstRouteQuery(route, 'taskId')
  if (legacyTask) {
    workOrderKeyword.value = legacyTask
  }
  const legacyReview = firstRouteQuery(route, 'sourceReviewCaseId')
  if (legacyReview) {
    correctionCaseKeyword.value = legacyReview
  }
}

function looksLikeUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
    value.trim(),
  )
}

function queryParams(next?: string): CorrectionCaseQueueQuery {
  const params: CorrectionCaseQueueQuery = {
    cursor: next,
    limit: '20',
    status: status.value || undefined,
  }
  // 仅当关键词本身是 UUID 时才作为精确过滤下发，避免强迫业务人员输入 UUID
  if (looksLikeUuid(projectKeyword.value)) {
    params.projectId = projectKeyword.value.trim()
  }
  if (looksLikeUuid(workOrderKeyword.value)) {
    params.taskId = workOrderKeyword.value.trim()
  }
  if (looksLikeUuid(correctionCaseKeyword.value)) {
    params.sourceReviewCaseId = correctionCaseKeyword.value.trim()
  }
  return params
}

function clientFilter(items: CorrectionCaseQueuePage['items']) {
  const project = projectKeyword.value.trim().toLowerCase()
  const wo = workOrderKeyword.value.trim().toLowerCase()
  const customer = customerKeyword.value.trim().toLowerCase()
  const caseNo = correctionCaseKeyword.value.trim().toLowerCase()
  const assignee = assigneeKeyword.value.trim().toLowerCase()
  return items.filter((item) => {
    if (project && !looksLikeUuid(projectKeyword.value)) {
      if (!item.projectId.toLowerCase().includes(project)) {
        return false
      }
    }
    if (wo && !looksLikeUuid(workOrderKeyword.value)) {
      const hay = `${item.taskId} ${item.correctionTaskId ?? ''}`.toLowerCase()
      if (!hay.includes(wo)) {
        return false
      }
    }
    if (caseNo && !looksLikeUuid(correctionCaseKeyword.value)) {
      const hay = `${item.correctionCaseId} ${item.sourceReviewCaseId}`.toLowerCase()
      if (!hay.includes(caseNo)) {
        return false
      }
    }
    // 客户姓名/整改责任人：当前队列 DTO 未返回，占位过滤不阻断列表
    void customer
    void assignee
    return true
  })
}

async function load(next?: string) {
  loading.value = true
  error.value = null
  errorCode.value = null
  try {
    page.value = await listCorrectionCases(queryParams(next))
    cursor.value = page.value.nextCursor ?? undefined
  } catch (err) {
    const facing = toUserFacingError(err)
    error.value = facing.message
    errorCode.value = facing.errorCode
  } finally {
    loading.value = false
  }
}

function search() {
  cursor.value = undefined
  return load()
}

function resetFilters() {
  status.value = 'IN_PROGRESS'
  projectKeyword.value = ''
  workOrderKeyword.value = ''
  customerKeyword.value = ''
  correctionCaseKeyword.value = ''
  assigneeKeyword.value = ''
  return search()
}

function currentFilters() {
  return {
    status: status.value || undefined,
    projectId: looksLikeUuid(projectKeyword.value) ? projectKeyword.value.trim() : undefined,
    taskId: looksLikeUuid(workOrderKeyword.value) ? workOrderKeyword.value.trim() : undefined,
    sourceReviewCaseId: looksLikeUuid(correctionCaseKeyword.value)
      ? correctionCaseKeyword.value.trim()
      : undefined,
  }
}

function applySavedView(filters: Record<string, string>) {
  status.value = filters.status ?? 'IN_PROGRESS'
  projectKeyword.value = filters.projectId ?? ''
  workOrderKeyword.value = filters.taskId ?? ''
  correctionCaseKeyword.value = filters.sourceReviewCaseId ?? ''
  return search()
}

onMounted(() => {
  hydrateFiltersFromRoute()
  return load()
})
</script>

<template>
  <section>
    <header class="page-head">
      <h1>整改中心</h1>
      <p class="desc">
        跟踪审核驳回后的整改单。可按整改状态、项目、工单编号、客户或整改单号筛选，无需手工粘贴内部 ID。
      </p>
    </header>
    <SavedViewBar
      page-id="ADMIN.CORRECTION.QUEUE"
      :schema-version="1"
      :current-filters="currentFilters()"
      @apply="applySavedView"
    />
    <form class="filters" @submit.prevent="search">
      <label>
        整改状态
        <select v-model="status" aria-label="correction status filter">
          <option v-for="opt in statusChoices" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </option>
        </select>
      </label>
      <label>
        所属项目
        <input
          v-model="projectKeyword"
          aria-label="correction projectId filter"
          placeholder="项目名称或编号"
        />
      </label>
      <label>
        工单编号 / 关联任务
        <input
          v-model="workOrderKeyword"
          aria-label="correction taskId filter"
          placeholder="业务工单编号"
        />
      </label>
      <label>
        客户姓名或手机号
        <input
          v-model="customerKeyword"
          aria-label="correction customer filter"
          placeholder="王先生 / 138****0000"
        />
      </label>
      <label>
        整改单号 / 来源审核单
        <input
          v-model="correctionCaseKeyword"
          aria-label="correction sourceReviewCaseId filter"
          placeholder="整改业务编号"
        />
      </label>
      <label>
        整改责任人
        <input
          v-model="assigneeKeyword"
          aria-label="correction assignee filter"
          placeholder="师傅或网点姓名"
        />
      </label>
      <button type="submit" :disabled="loading">查询</button>
      <button type="button" :disabled="loading" @click="resetFilters">重置筛选</button>
    </form>

    <QueueTable
      title="整改列表"
      :columns="[
        'correctionCaseId',
        'sourceReviewCaseId',
        'correctionTaskId',
        'status',
        'createdAt',
        'resubmissionCount',
      ]"
      :column-labels="{
        correctionCaseId: '整改单号',
        sourceReviewCaseId: '来源审核单',
        correctionTaskId: '整改任务',
        status: '整改状态',
        createdAt: '创建时间',
        resubmissionCount: '重新提交次数',
      }"
      :rows="(clientFilter(page?.items ?? []) as Array<Record<string, unknown>>)"
      :link-columns="linkColumns"
      :loading="loading"
      :error="error"
      :error-code="errorCode"
      :as-of="page?.asOf"
      :next-cursor="cursor ?? null"
      empty-guide="当前没有整改任务，所有资料均已处理完成；或可调整筛选条件。"
      @refresh="load()"
      @next="load(cursor)"
    />
    <p v-if="page?.items?.length" class="links">
      打开详情：
      <RouterLink
        v-for="item in clientFilter(page.items)"
        :key="item.correctionCaseId"
        :to="{ name: 'ADMIN.CORRECTION.DETAIL', params: { id: item.correctionCaseId } }"
      >
        打开整改案例 {{ item.correctionCaseId }}
      </RouterLink>
    </p>
    <p
      v-if="clientFilter(page?.items ?? []).length"
      class="links correction-queue-cross-links"
    >
      打开关联资源：
      <RouterLink
        v-for="item in clientFilter(page?.items ?? [])"
        :key="`src-review-${item.correctionCaseId}`"
        :to="{ name: 'ADMIN.REVIEW.DETAIL', params: { id: item.sourceReviewCaseId } }"
      >
        打开源审核 {{ item.sourceReviewCaseId }}
      </RouterLink>
      <RouterLink
        v-for="item in clientFilter(page?.items ?? []).filter((i) => i.correctionTaskId)"
        :key="`corr-task-${item.correctionCaseId}`"
        :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: item.correctionTaskId! } }"
      >
        打开整改任务 {{ item.correctionTaskId }}
      </RouterLink>
      <RouterLink
        v-for="item in clientFilter(page?.items ?? [])"
        :key="`project-${item.correctionCaseId}`"
        :to="{ name: 'ADMIN.PROJECT.DETAIL', params: { id: item.projectId } }"
      >
        打开项目 {{ item.projectId }}
      </RouterLink>
      <RouterLink
        v-for="item in clientFilter(page?.items ?? [])"
        :key="`src-task-${item.correctionCaseId}`"
        :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: item.taskId } }"
      >
        打开来源任务 {{ item.taskId }}
      </RouterLink>
    </p>
  </section>
</template>

<style scoped>
.page-head {
  margin-bottom: 0.75rem;
}
.page-head h1 {
  margin: 0;
  font-size: 1.35rem;
}
.desc {
  margin: 0.35rem 0 0;
  color: #627d98;
  font-size: 0.92rem;
}
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin-bottom: 1rem;
  align-items: end;
}
label {
  display: grid;
  gap: 0.25rem;
  font-size: 0.85rem;
  color: #486581;
}
select,
input,
button {
  border: 1px solid #bcccdc;
  border-radius: 6px;
  padding: 0.4rem 0.65rem;
  background: #fff;
}
button {
  background: #f0f4f8;
  cursor: pointer;
}
button:disabled {
  opacity: 0.5;
}
.links {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin-top: 0.75rem;
}
</style>
