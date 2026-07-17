<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import {
  listTechnicianTaskFeed,
  type TechnicianPortalFeedItem,
} from '../api/technicianPortal'

const props = defineProps<{ technicianContextId: string | null }>()
const items = ref<TechnicianPortalFeedItem[]>([])
const networkId = ref<string | null>(null)
const asOf = ref<string | null>(null)
const nextCursor = ref<string | null>(null)
const error = ref<string | null>(null)
const loadingMore = ref(false)

async function load(options?: { append?: boolean; sinceCursor?: string }) {
  if (!props.technicianContextId) {
    items.value = []
    networkId.value = null
    asOf.value = null
    nextCursor.value = null
    error.value = '请选择 TECHNICIAN 上下文'
    return
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
    error.value = err instanceof Error ? err.message : '任务 Feed 加载失败'
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
})
watch(() => props.technicianContextId, () => {
  void load()
})
</script>

<template>
  <section
    data-testid="technician-portal-task-feed"
    data-page-id="TECHNICIAN.TASK.LIST"
  >
    <header class="top">
      <div>
        <h2>任务 Feed</h2>
        <p class="hint">M218：展示 M195 Accepted 非 PII 字段；支持 sinceCursor 增量。</p>
      </div>
      <button type="button" data-testid="technician-feed-refresh" @click="load()">
        刷新
      </button>
    </header>
    <p v-if="error" data-testid="technician-portal-error">{{ error }}</p>
    <template v-else>
      <dl v-if="asOf" data-testid="technician-feed-meta" class="meta">
        <div><dt>networkId</dt><dd data-testid="technician-feed-network-id">{{ networkId }}</dd></div>
        <div><dt>asOf</dt><dd data-testid="technician-feed-as-of">{{ asOf }}</dd></div>
      </dl>
      <table data-testid="technician-feed-table">
        <thead>
          <tr>
            <th>类型</th>
            <th>任务</th>
            <th>工单</th>
            <th>项目</th>
            <th>状态</th>
            <th>阶段</th>
            <th>类型码</th>
            <th>种类</th>
            <th>业务</th>
            <th>生效自</th>
            <th>失效原因</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="item in items"
            :key="item.cursor"
            :data-testid="`technician-feed-row-${item.taskId}`"
          >
            <td>{{ item.itemType }}</td>
            <td>
              <RouterLink
                v-if="item.itemType === 'ASSIGNMENT'"
                :to="{ path: '/technician-portal/schedule', query: { taskId: item.taskId } }"
                data-testid="technician-feed-schedule-deeplink"
              >
                {{ item.taskId }}
              </RouterLink>
              <span v-else>{{ item.taskId }}</span>
            </td>
            <td>{{ item.workOrderId ?? '—' }}</td>
            <td data-testid="technician-feed-project-id">{{ item.projectId ?? '—' }}</td>
            <td>{{ item.taskStatus ?? '—' }}</td>
            <td data-testid="technician-feed-stage-code">{{ item.stageCode ?? '—' }}</td>
            <td data-testid="technician-feed-task-type">{{ item.taskType ?? '—' }}</td>
            <td data-testid="technician-feed-task-kind">{{ item.taskKind ?? '—' }}</td>
            <td data-testid="technician-feed-business-type">{{ item.businessType ?? '—' }}</td>
            <td data-testid="technician-feed-effective-from">{{ item.effectiveFrom ?? '—' }}</td>
            <td>{{ item.invalidationReason ?? '—' }}</td>
          </tr>
        </tbody>
      </table>
      <p v-if="items.length === 0" data-testid="technician-feed-empty">暂无 ACTIVE 责任任务</p>
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
</style>
