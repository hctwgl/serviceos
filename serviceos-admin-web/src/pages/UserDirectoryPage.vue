<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { Alert, Button, Input, Select, Space, Table, Tag } from 'ant-design-vue'
import ListPageLayout from '../patterns/templates/ListPageLayout.vue'
import SummaryStrip, { type SummaryStripItem } from '../patterns/SummaryStrip.vue'
import { listSecurityPrincipals, type SecurityPrincipalPage } from '../api/securityPrincipals'
import { safeAccessDeniedMessage } from '../api/client'
import { statusLabel } from '../product/statusLabels'
import { formatDateTimeDisplay } from '../presentation/date-time.presenter'

const loading = ref(false)
const error = ref<string | null>(null)
const query = ref('')
const status = ref<string | undefined>(undefined)
const page = ref<SecurityPrincipalPage | null>(null)

const summaryItems = computed<SummaryStripItem[]>(() => {
  const items = page.value?.items ?? []
  const active = items.filter((item) => item.status === 'ACTIVE').length
  const disabled = items.filter((item) => item.status === 'DISABLED').length
  return [
    { key: 'page', label: '本页用户', value: String(items.length) },
    { key: 'active', label: '启用', value: String(active), tone: 'success' },
    { key: 'disabled', label: '停用', value: String(disabled), tone: disabled ? 'warning' : 'default' },
    {
      key: 'gap',
      label: '组织 / 角色列',
      value: '待读模型',
      hint: 'UI_DATA_GAP',
      tone: 'info',
    },
  ]
})

const columns = [
  { title: '姓名', dataIndex: 'displayName', key: 'displayName' },
  { title: '登录账号 / 工号', dataIndex: 'employeeNumber', key: 'employeeNumber' },
  { title: '状态', key: 'status' },
  { title: '类型', key: 'type' },
  { title: '更新时间', key: 'updatedAt' },
  { title: '操作', key: 'actions' },
]

async function load(cursor?: string) {
  loading.value = true
  error.value = null
  try {
    page.value = await listSecurityPrincipals({
      query: query.value.trim() || undefined,
      status: status.value || undefined,
      cursor,
      limit: '20',
    })
  } catch (err) {
    page.value = null
    error.value = safeAccessDeniedMessage(err)
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  query.value = ''
  status.value = undefined
  void load()
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div data-testid="user-directory-page">
    <ListPageLayout
      title="用户管理"
      description="查询、查看和管理平台用户主体。创建与组织归属使用业务实体选择器，不直接输入内部 ID。"
      :loading="loading"
      :count-label="page ? `本页 ${page.items.length} 人` : undefined"
      @search="load()"
      @reset="resetFilters"
    >
      <template #primary-action>
        <Button type="primary" disabled data-testid="user-directory-create-disabled">
          新建用户
        </Button>
      </template>
      <template #feedback>
        <Alert
          v-if="error"
          type="error"
          show-icon
          :message="error"
          data-testid="access-denied"
          style="margin-bottom: 12px"
        />
        <Alert
          type="info"
          show-icon
          message="新建用户、组织归属、角色摘要与最近登录读模型尚未完整交付（UI_DATA_GAP）。本页使用真实主体目录 API，不伪造字段。"
          style="margin-bottom: 12px"
        />
      </template>

      <template #filters>
        <label class="filter">
          <span>姓名 / 工号</span>
          <Input
            v-model:value="query"
            allow-clear
            aria-label="user directory search"
            placeholder="搜索显示名或工号"
          />
        </label>
        <label class="filter">
          <span>状态</span>
          <Select
            v-model:value="status"
            allow-clear
            style="min-width: 160px"
            aria-label="user status filter"
            placeholder="全部"
            :options="[
              { value: 'ACTIVE', label: '启用' },
              { value: 'DISABLED', label: '停用' },
            ]"
          />
        </label>
      </template>

      <SummaryStrip :items="summaryItems" />

      <Table
        v-if="page"
        data-testid="user-directory-table"
        size="middle"
        :loading="loading"
        :pagination="false"
        :columns="columns"
        :data-source="page.items"
        :row-key="(row) => row.id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'employeeNumber'">
            {{ record.employeeNumber || '—' }}
          </template>
          <template v-else-if="column.key === 'status'">
            <Tag :color="record.status === 'ACTIVE' ? 'success' : 'default'">
              {{ statusLabel(record.status) }}
            </Tag>
          </template>
          <template v-else-if="column.key === 'type'">
            {{ statusLabel(record.type) }}
          </template>
          <template v-else-if="column.key === 'updatedAt'">
            {{ formatDateTimeDisplay(record.updatedAt) }}
          </template>
          <template v-else-if="column.key === 'actions'">
            <Space>
              <RouterLink :to="{ name: 'ADMIN.USER.DETAIL', params: { id: record.id } }">
                打开
              </RouterLink>
              <RouterLink :to="{ name: 'ADMIN.USER.DETAIL', params: { id: record.id } }">
                查看
              </RouterLink>
            </Space>
          </template>
        </template>
      </Table>
      <p v-else-if="!error && !loading" class="muted">暂无用户数据</p>

      <template #pagination>
        <Button
          v-if="page?.nextCursor"
          :loading="loading"
          data-testid="user-directory-next"
          @click="load(page.nextCursor!)"
        >
          下一页
        </Button>
        <span v-if="page?.asOf" class="muted">统计时间 {{ formatDateTimeDisplay(page.asOf) }}</span>
      </template>
    </ListPageLayout>
  </div>
</template>

<style scoped>
.filter {
  display: grid;
  gap: 4px;
  font-size: 13px;
}
.muted {
  color: var(--sos-color-text-tertiary, #6b7280);
  font-size: 13px;
}
</style>
