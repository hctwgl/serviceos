<script setup lang="ts">
import type { AdminWorkOrderDirectoryItem } from '@serviceos/api-client'
import type { TableColumnsType } from '@serviceos/design-system'

import { loadAdminWorkOrders } from '@serviceos/api-client'
import {
  Button,
  CalendarOutlined,
  Card,
  DownloadOutlined,
  Form,
  Input,
  SearchOutlined,
  Select,
  SettingOutlined,
  Space,
  Table,
  Tabs,
  Tag,
} from '@serviceos/design-system'
import { useQuery } from '@tanstack/vue-query'
import { Page } from '@vben/common-ui'
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import PageError from '../components/PageError.vue'
import { formatDateTime } from '../presenters/work-order'

type BusinessView = 'all' | 'correction' | 'dispatch' | 'review' | 'sla'

const route = useRoute()
const router = useRouter()
const keyword = ref(typeof route.query.q === 'string' ? route.query.q : '')
const project = ref<string | undefined>(
  typeof route.query.projectId === 'string' ? route.query.projectId : undefined,
)
const status = ref<string>()
const slaRisk = ref<string>()
const activeView = ref<BusinessView>(normalizeView(route.query.view))
const cursor = ref<string>()
const cursorHistory = ref<Array<string | undefined>>([])

const applied = ref<Record<string, string | number | undefined>>(
  buildFilters(activeView.value, keyword.value, project.value, status.value, slaRisk.value),
)

const query = useQuery({
  queryKey: computed(() => ['work-orders', applied.value, cursor.value]),
  queryFn: () => loadAdminWorkOrders({ ...applied.value, cursor: cursor.value, limit: 20 }),
})

const viewTabs = computed(() => [
  { key: 'all', label: `全部工单 ${query.data.value?.totalCount ?? '—'}` },
  { key: 'dispatch', label: `待派责任网点 ${query.data.value?.queueSummary.dispatchCount ?? '—'}` },
  { key: 'sla', label: `SLA 风险 ${query.data.value?.queueSummary.slaRiskCount ?? '—'}` },
  { key: 'review', label: `待审核 ${query.data.value?.queueSummary.reviewCount ?? '—'}` },
  { key: 'correction', label: `待整改 ${query.data.value?.queueSummary.correctionCount ?? '—'}` },
])

const columns: TableColumnsType<AdminWorkOrderDirectoryItem> = [
  { title: '工单编号', key: 'orderCode', width: 170, fixed: 'left' },
  { title: '客户', key: 'customer', width: 170 },
  { title: '项目 / 服务', key: 'project', width: 210 },
  { title: '当前阶段', key: 'stage', width: 120 },
  { title: '责任网点', key: 'network', width: 160 },
  { title: '责任师傅', key: 'technician', width: 110 },
  { title: '更新时间', key: 'updatedAt', width: 155 },
  { title: 'SLA 风险', key: 'sla', width: 108 },
  { title: '状态', key: 'status', width: 104 },
  { title: '操作', key: 'action', width: 80, fixed: 'right' },
]

function normalizeView(value: unknown): BusinessView {
  return ['all', 'correction', 'dispatch', 'review', 'sla'].includes(String(value))
    ? (String(value) as BusinessView)
    : 'all'
}

function buildFilters(
  view: BusinessView,
  searchKeyword: string,
  projectId?: string,
  workOrderStatus?: string,
  selectedSlaRisk?: string,
) {
  const viewFilters: Record<string, string | undefined> = {}
  if (view === 'dispatch') viewFilters.currentStageCode = 'PILOT_DISPATCH'
  if (view === 'sla') viewFilters.slaRisk = 'OPEN'
  if (view === 'review') viewFilters.reviewCorrectionStatus = 'REVIEW_OPEN'
  if (view === 'correction') viewFilters.reviewCorrectionStatus = 'CORRECTION_ACTIVE'
  return {
    ...viewFilters,
    q: searchKeyword.trim() || undefined,
    projectId,
    status: workOrderStatus,
    slaRisk: selectedSlaRisk || viewFilters.slaRisk,
  }
}

function applyFilters() {
  cursor.value = undefined
  cursorHistory.value = []
  applied.value = buildFilters(activeView.value, keyword.value, project.value, status.value, slaRisk.value)
}

function resetFilters() {
  keyword.value = ''
  project.value = undefined
  status.value = undefined
  slaRisk.value = undefined
  applyFilters()
}

function changeView(key: string | number) {
  activeView.value = normalizeView(key)
  router.replace({ query: { ...route.query, view: activeView.value } })
  applyFilters()
}

function nextPage() {
  const next = query.data.value?.nextCursor
  if (!next) return
  cursorHistory.value.push(cursor.value)
  cursor.value = next
}

function previousPage() {
  if (!cursorHistory.value.length) return
  cursor.value = cursorHistory.value.pop()
}

function slaColor(level: string) {
  if (level === 'BREACHED') return 'error'
  if (level === 'RISK') return 'warning'
  return 'success'
}

watch(
  () => [route.query.q, route.query.projectId, route.query.view],
  ([nextKeyword, nextProject, nextView]) => {
    keyword.value = typeof nextKeyword === 'string' ? nextKeyword : ''
    project.value = typeof nextProject === 'string' ? nextProject : undefined
    activeView.value = normalizeView(nextView)
    applyFilters()
  },
)
</script>

<template>
  <Page
    title="工单中心"
    description="统一查看工单状态、责任、预约与 SLA 风险，并进入完整履约工作区。"
    content-class="serviceos-directory-content"
  >
    <template #extra>
      <Space>
        <Button><SettingOutlined />自定义列</Button>
        <Button><DownloadOutlined />导出</Button>
      </Space>
    </template>

    <PageError v-if="query.isError.value" :detail="query.error.value?.message ?? '工单目录加载失败'" />

    <Card v-else class="serviceos-directory-card" :bordered="false">
      <template #title>
        <Tabs :active-key="activeView" @change="changeView">
          <Tabs.TabPane v-for="tab in viewTabs" :key="tab.key" :tab="tab.label" />
        </Tabs>
      </template>

      <Form layout="inline" class="directory-query-form" @submit.prevent="applyFilters">
        <Form.Item class="directory-search-field">
          <Input v-model:value="keyword" placeholder="搜索工单编号、客户或项目" allow-clear>
            <template #prefix><SearchOutlined /></template>
          </Input>
        </Form.Item>
        <Form.Item>
          <Select
            v-model:value="project"
            placeholder="全部项目"
            allow-clear
            :options="query.data.value?.projectOptions.map((item) => ({ value: item.id, label: item.name })) ?? []"
          />
        </Form.Item>
        <Form.Item>
          <Select
            v-model:value="status"
            placeholder="工单状态"
            allow-clear
            :options="[
              { value: 'RECEIVED', label: '待受理' },
              { value: 'ACTIVE', label: '进行中' },
              { value: 'FULFILLED', label: '已完成' },
            ]"
          />
        </Form.Item>
        <Form.Item>
          <Select
            v-model:value="slaRisk"
            placeholder="SLA 风险"
            allow-clear
            :options="[
              { value: 'OPEN', label: '存在风险' },
              { value: 'BREACHED', label: '已超时' },
              { value: 'NEAR', label: '即将超时' },
            ]"
          />
        </Form.Item>
        <Form.Item>
          <Space>
            <Button type="primary" html-type="submit">查询</Button>
            <Button @click="resetFilters">重置</Button>
            <Button><CalendarOutlined />保存视图</Button>
          </Space>
        </Form.Item>
      </Form>

      <Table
        row-key="id"
        size="middle"
        :columns="columns"
        :data-source="query.data.value?.items ?? []"
        :loading="query.isLoading.value || query.isFetching.value"
        :pagination="false"
        :scroll="{ x: 1387 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'orderCode'">
            <RouterLink class="table-primary-link" :to="`/work-orders/${record.id}`">
              {{ record.orderCode }}
            </RouterLink>
          </template>
          <template v-else-if="column.key === 'customer'">
            <div class="table-primary-cell">
              <strong>{{ record.customerName || '数据不完整' }}</strong>
              <span>{{ record.customerPhone || '联系方式未提供' }}</span>
            </div>
          </template>
          <template v-else-if="column.key === 'project'">
            <div class="table-primary-cell">
              <strong>{{ record.projectName || '数据不完整' }}</strong>
              <span>{{ record.clientName }} · {{ record.serviceName }}</span>
            </div>
          </template>
          <template v-else-if="column.key === 'stage'">{{ record.stageName || '数据不完整' }}</template>
          <template v-else-if="column.key === 'network'">{{ record.networkName || '待分配' }}</template>
          <template v-else-if="column.key === 'technician'">{{ record.technicianName || '待分配' }}</template>
          <template v-else-if="column.key === 'updatedAt'">{{ formatDateTime(record.updatedAt) }}</template>
          <template v-else-if="column.key === 'sla'">
            <Tag :color="slaColor(record.slaLevel)">{{ record.slaLabel }}</Tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <Tag :color="record.dataComplete ? 'processing' : 'error'">
              {{ record.dataComplete ? (record.statusName || '数据不完整') : '数据不完整' }}
            </Tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <RouterLink class="table-primary-link" :to="`/work-orders/${record.id}`">查看</RouterLink>
          </template>
        </template>
        <template #emptyText>
          <div class="workbench-empty-state">
            <strong>暂无符合条件的工单</strong>
            <span>调整业务视图或筛选条件后重新查询。</span>
          </div>
        </template>
      </Table>

      <footer class="directory-table-footer">
        <span>共 {{ query.data.value?.totalCount ?? 0 }} 条</span>
        <Space>
          <Button :disabled="!cursorHistory.length" @click="previousPage">上一页</Button>
          <Tag color="processing">第 {{ cursorHistory.length + 1 }} 页</Tag>
          <Button :disabled="!query.data.value?.nextCursor" @click="nextPage">下一页</Button>
        </Space>
      </footer>
    </Card>
  </Page>
</template>
