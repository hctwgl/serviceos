<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, useRoute, type RouteLocationRaw } from 'vue-router'
import QueueTable from './QueueTable.vue'
import {
  listReviewCases,
  type ReviewCaseQueuePage,
  type ReviewCaseQueueQuery,
} from '../api/queues'
import { firstRouteQuery, uuidRoute } from '../routeQuery'
import { statusOptions } from '../product/statusLabels'
import { toUserFacingError } from '../product/errorMessages'

const linkColumns: Record<
  string,
  (row: Record<string, unknown>) => RouteLocationRaw | null
> = {
  reviewCaseId: (row) => uuidRoute(row.reviewCaseId, 'ADMIN.REVIEW.DETAIL'),
  projectId: (row) => uuidRoute(row.projectId, 'ADMIN.PROJECT.DETAIL'),
}

const route = useRoute()

const loading = ref(false)
const error = ref<string | null>(null)
const errorCode = ref<string | null>(null)
const page = ref<ReviewCaseQueuePage | null>(null)
const cursor = ref<string | undefined>()

/** 与 OpenAPI 省略默认一致：status=OPEN。显式 query 可覆盖。 */
const status = ref('OPEN')
const origin = ref('')
const projectId = ref('')
const taskId = ref('')

const statusChoices = statusOptions([
  'OPEN',
  'APPROVED',
  'REJECTED',
  'FORCE_APPROVED',
  'REOPENED',
])

function hydrateFiltersFromRoute() {
  const nextStatus = firstRouteQuery(route, 'status')
  if (nextStatus !== undefined) {
    status.value = nextStatus
  }
  const nextOrigin = firstRouteQuery(route, 'origin')
  if (nextOrigin !== undefined) {
    origin.value = nextOrigin
  }
  const nextProjectId = firstRouteQuery(route, 'projectId')
  if (nextProjectId !== undefined) {
    projectId.value = nextProjectId
  }
  const nextTaskId = firstRouteQuery(route, 'taskId')
  if (nextTaskId !== undefined) {
    taskId.value = nextTaskId
  }
}

function queryParams(next?: string): ReviewCaseQueueQuery {
  return {
    cursor: next,
    limit: '20',
    status: status.value || undefined,
    origin: origin.value || undefined,
    projectId: projectId.value.trim() || undefined,
    taskId: taskId.value.trim() || undefined,
  }
}

async function load(next?: string) {
  loading.value = true
  error.value = null
  errorCode.value = null
  try {
    page.value = await listReviewCases(queryParams(next))
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
  status.value = 'OPEN'
  origin.value = ''
  projectId.value = ''
  taskId.value = ''
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
      <h1>审核中心</h1>
      <p class="desc">
        查看待审与已审资料。可按审核状态、来源、项目或关联任务筛选，第一列展示审核单号。
      </p>
    </header>
    <form class="filters" @submit.prevent="search">
      <label>
        审核状态
        <select v-model="status" aria-label="review status filter">
          <option v-for="opt in statusChoices" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </option>
        </select>
      </label>
      <label>
        审核来源
        <select v-model="origin" aria-label="review origin filter">
          <option value="">（不限）</option>
          <option value="INTERNAL">内部审核</option>
          <option value="CLIENT">车企审核</option>
        </select>
      </label>
      <label>
        所属项目
        <input
          v-model="projectId"
          aria-label="review projectId filter"
          placeholder="项目名称或编号"
        />
      </label>
      <label>
        关联任务
        <input
          v-model="taskId"
          aria-label="review taskId filter"
          placeholder="任务编号"
        />
      </label>
      <button type="submit" :disabled="loading">查询</button>
      <button type="button" :disabled="loading" @click="resetFilters">重置筛选</button>
    </form>

    <QueueTable
      title="审核队列"
      :columns="['reviewCaseId', 'projectId', 'status', 'origin', 'createdAt', 'latestDecision']"
      :column-labels="{
        reviewCaseId: '审核单号',
        projectId: '所属项目',
        status: '审核状态',
        origin: '审核来源',
        createdAt: '创建时间',
        latestDecision: '最近审核结论',
      }"
      :rows="(page?.items ?? []) as Array<Record<string, unknown>>"
      :link-columns="linkColumns"
      :loading="loading"
      :error="error"
      :error-code="errorCode"
      :as-of="page?.asOf"
      :next-cursor="cursor ?? null"
      empty-guide="当前没有待审或已审记录，所有资料均已处理完成；或可调整筛选条件。"
      @refresh="load()"
      @next="load(cursor)"
    />
    <p v-if="page?.items?.length" class="links">
      打开详情：
      <RouterLink
        v-for="item in page.items"
        :key="item.reviewCaseId"
        :to="{ name: 'ADMIN.REVIEW.DETAIL', params: { id: item.reviewCaseId } }"
      >
        打开审核案例 {{ item.reviewCaseId }}
      </RouterLink>
    </p>
    <p v-if="page?.items?.length" class="links review-queue-cross-links">
      打开关联资源：
      <RouterLink
        v-for="item in page.items"
        :key="`project-${item.reviewCaseId}`"
        :to="{ name: 'ADMIN.PROJECT.DETAIL', params: { id: item.projectId } }"
      >
        打开项目 {{ item.projectId }}
      </RouterLink>
      <RouterLink
        v-for="item in page.items"
        :key="`task-${item.reviewCaseId}`"
        :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: item.taskId } }"
      >
        打开任务 {{ item.taskId }}
      </RouterLink>
      <RouterLink
        v-for="item in page.items"
        :key="`snapshot-${item.reviewCaseId}`"
        :to="{
          name: 'ADMIN.EVIDENCE_SET_SNAPSHOT.DETAIL',
          params: { id: item.evidenceSetSnapshotId },
        }"
      >
        打开资料快照 {{ item.evidenceSetSnapshotId }}
      </RouterLink>
      <RouterLink
        v-for="item in page.items.filter((i) => i.sourceReviewCaseId)"
        :key="`src-review-${item.reviewCaseId}`"
        :to="{ name: 'ADMIN.REVIEW.DETAIL', params: { id: item.sourceReviewCaseId! } }"
      >
        打开源审核 {{ item.sourceReviewCaseId }}
      </RouterLink>
      <RouterLink
        v-for="item in page.items.filter((i) => i.reopenedFromReviewCaseId)"
        :key="`reopened-from-${item.reviewCaseId}`"
        :to="{ name: 'ADMIN.REVIEW.DETAIL', params: { id: item.reopenedFromReviewCaseId! } }"
      >
        打开重开来源 {{ item.reopenedFromReviewCaseId }}
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
}
input {
  min-width: 12rem;
}
button {
  background: #243b53;
  color: #fff;
  border-color: #243b53;
  cursor: pointer;
}
button:disabled {
  opacity: 0.5;
}
.links {
  margin-top: 0.75rem;
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  font-size: 0.9rem;
}
</style>
