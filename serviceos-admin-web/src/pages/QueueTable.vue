<script setup lang="ts">
import { RouterLink, type RouteLocationRaw } from 'vue-router'
import { fieldLabel } from '../product/terms'
import { statusLabel } from '../product/statusLabels'
import { formatDateTime } from '../product/formatTime'
import PageState from '../components/PageState.vue'
import StatusBadge from '../components/StatusBadge.vue'

const props = defineProps<{
  title: string
  columns: string[]
  /** 可选：覆盖列中文标题 */
  columnLabels?: Record<string, string>
  rows: Array<Record<string, unknown>>
  loading: boolean
  error: string | null
  errorCode?: string | null
  asOf?: string | null
  nextCursor?: string | null
  emptyGuide?: string
  /**
   * 可选列深链：仅当解析结果非 null 时渲染 RouterLink。
   */
  linkColumns?: Record<
    string,
    (row: Record<string, unknown>) => RouteLocationRaw | null | undefined
  >
}>()

const emit = defineEmits<{ refresh: []; next: [] }>()

const TIME_COLUMNS = new Set([
  'createdAt',
  'updatedAt',
  'receivedAt',
  'openedAt',
  'deadlineAt',
  'startedAt',
  'completedAt',
  'asOf',
])

function linkFor(column: string, row: Record<string, unknown>): RouteLocationRaw | null {
  const resolver = props.linkColumns?.[column]
  if (!resolver) {
    return null
  }
  return resolver(row) ?? null
}

function headerFor(column: string): string {
  return props.columnLabels?.[column] ?? fieldLabel(column)
}

const ENUM_LABEL_COLUMNS = new Set([
  'taskType',
  'taskKind',
  'stageCode',
  'businessType',
  'type',
  'origin',
  'decision',
])

function usesEnumLabel(column: string): boolean {
  return column === 'status' || column.endsWith('Status') || ENUM_LABEL_COLUMNS.has(column)
}

function displayValue(column: string, value: unknown): string {
  if (value == null || value === '') {
    return '—'
  }
  if (usesEnumLabel(column)) {
    return statusLabel(String(value))
  }
  if (TIME_COLUMNS.has(column) || column.endsWith('At')) {
    return formatDateTime(String(value))
  }
  if (Array.isArray(value)) {
    return value.length ? value.join(', ') : '—'
  }
  return String(value)
}

function isStatusColumn(column: string): boolean {
  return column === 'status' || column.endsWith('Status')
}

function isBadgeColumn(column: string): boolean {
  return isStatusColumn(column)
}
</script>

<template>
  <section class="queue">
    <header>
      <div>
        <h2>{{ title }}</h2>
        <p v-if="asOf" class="meta">统计时间 {{ formatDateTime(asOf) }}</p>
      </div>
      <div class="actions">
        <button type="button" :disabled="loading" @click="emit('refresh')">刷新</button>
        <button type="button" :disabled="loading || !nextCursor" @click="emit('next')">下一页</button>
      </div>
    </header>
    <PageState
      v-if="error"
      kind="error"
      :description="error"
      :error-code="errorCode ?? undefined"
      @reload="emit('refresh')"
    />
    <PageState v-else-if="loading" kind="loading" />
    <PageState
      v-else-if="rows.length === 0"
      kind="empty"
      :guide="emptyGuide ?? '当前没有相关数据，可以调整筛选条件或初始化演示数据。'"
    />
    <table v-else>
      <thead>
        <tr>
          <th v-for="column in columns" :key="column">{{ headerFor(column) }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, index) in rows" :key="index">
          <td v-for="column in columns" :key="column">
            <StatusBadge
              v-if="isBadgeColumn(column) && !linkFor(column, row)"
              :status="String(row[column] ?? '')"
            />
            <RouterLink
              v-else-if="linkFor(column, row)"
              :to="linkFor(column, row)!"
              class="queue-cell-link"
            >
              {{ displayValue(column, row[column]) }}
            </RouterLink>
            <template v-else>{{ displayValue(column, row[column]) }}</template>
          </td>
        </tr>
      </tbody>
    </table>
  </section>
</template>

<style scoped>
.queue {
  background: #fff;
  border-radius: 12px;
  padding: 1rem 1.25rem;
  box-shadow: 0 1px 3px rgb(16 42 67 / 8%);
}
header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
  margin-bottom: 1rem;
}
h2 {
  margin: 0;
}
.meta {
  margin: 0.25rem 0 0;
  color: #627d98;
  font-size: 0.85rem;
}
.actions {
  display: flex;
  gap: 0.5rem;
}
button {
  border: 1px solid #bcccdc;
  background: #f0f4f8;
  border-radius: 6px;
  padding: 0.4rem 0.75rem;
  cursor: pointer;
}
button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th,
td {
  text-align: left;
  padding: 0.55rem 0.4rem;
  border-bottom: 1px solid #e2e8f0;
  font-size: 0.92rem;
  vertical-align: top;
}
th {
  color: #486581;
  font-weight: 600;
}
.queue-cell-link {
  word-break: break-all;
}
</style>
