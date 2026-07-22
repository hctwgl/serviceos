<script setup lang="ts">
import type { AdminWorkOrderDirectoryItem } from '@serviceos/api-client'
import type { TableColumnsType } from '@serviceos/design-system'

import { loadAdminWorkbench, loadAdminWorkOrders } from '@serviceos/api-client'
import { currentIdentity } from '@serviceos/auth-context'
import {
  Button,
  CalendarOutlined,
  Card,
  Col,
  DownloadOutlined,
  Form,
  Input,
  RightOutlined,
  Row,
  SearchOutlined,
  Select,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
} from '@serviceos/design-system'
import { useQuery } from '@tanstack/vue-query'
import { Page } from '@vben/common-ui'
import { computed, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import PageError from '../components/PageError.vue'
import { formatDateTime } from '../presenters/work-order'

const router = useRouter()
const identity = currentIdentity()
const keyword = ref('')
const projectId = ref<string>()
const status = ref<string>()
const slaRisk = ref<string>()
const applied = ref<Record<string, string | number | undefined>>({ limit: 8 })

const summaryQuery = useQuery({ queryKey: ['admin-workbench'], queryFn: loadAdminWorkbench })
const tasksQuery = useQuery({
  queryKey: computed(() => ['admin-workbench-priority-items', applied.value]),
  queryFn: () => loadAdminWorkOrders(applied.value),
})

const summary = computed(() => summaryQuery.data.value)
const queueTabs = computed(() => [
  { key: 'mine', label: `我的待办 ${summary.value?.priorityCount ?? '—'}` },
  { key: 'sla', label: `SLA 风险 ${summary.value?.slaRiskCount ?? '—'}` },
  { key: 'exceptions', label: `异常阻塞 ${summary.value?.exceptionCount ?? '—'}` },
  { key: 'external', label: `等待外部处理 ${summary.value?.waitingExternalCount ?? '—'}` },
  { key: 'unassigned', label: `待派责任网点 ${summary.value?.unassignedCount ?? '—'}` },
])

const columns: TableColumnsType<AdminWorkOrderDirectoryItem> = [
  { title: '工单编号', key: 'orderCode', width: 172, fixed: 'left' },
  { title: '客户', key: 'customer', width: 170 },
  { title: '项目', key: 'project', width: 190 },
  { title: '当前阶段', key: 'stage', width: 120 },
  { title: '责任网点', key: 'network', width: 150 },
  { title: '责任师傅', key: 'technician', width: 110 },
  { title: '更新时间', key: 'updatedAt', width: 160 },
  { title: 'SLA 风险', key: 'sla', width: 108 },
  { title: '状态', key: 'status', width: 104 },
  { title: '操作', key: 'action', width: 80, fixed: 'right' },
]

function search() {
  applied.value = {
    limit: 8,
    q: keyword.value.trim() || undefined,
    projectId: projectId.value,
    status: status.value,
    slaRisk: slaRisk.value,
  }
}

function reset() {
  keyword.value = ''
  projectId.value = undefined
  status.value = undefined
  slaRisk.value = undefined
  applied.value = { limit: 8 }
}

function openQueue(key: string | number) {
  router.push({ path: '/work-orders', query: { view: String(key) } })
}

function slaColor(level: string) {
  if (level === 'BREACHED') return 'error'
  if (level === 'RISK') return 'warning'
  return 'success'
}
</script>

<template>
  <Page
    title="工单中心 / 工作台"
    :description="`欢迎回来，${identity.displayName}！以下是当前授权范围内的关键任务与运营概况。`"
    content-class="serviceos-workbench-content"
  >
    <PageError
      v-if="summaryQuery.isError.value"
      :detail="summaryQuery.error.value?.message ?? '工作台数据加载失败'"
    />

    <template v-else>
      <Row :gutter="[16, 16]">
        <Col :xs="24" :sm="12" :xl="6">
          <RouterLink to="/work-orders?view=priority">
            <Card class="workbench-metric-card workbench-metric-card--urgent" :bordered="false" hoverable>
              <Statistic title="今日优先处理" :value="summary?.priorityCount ?? 0" />
              <p>其中 {{ summary?.slaRiskCount ?? '—' }} 单存在 SLA 风险</p>
            </Card>
          </RouterLink>
        </Col>
        <Col :xs="24" :sm="12" :xl="6">
          <RouterLink to="/work-orders?view=review">
            <Card class="workbench-metric-card" :bordered="false" hoverable>
              <Statistic title="待审核" :value="summary?.reviewCount ?? 0" />
              <p>当前待处理审核任务</p>
            </Card>
          </RouterLink>
        </Col>
        <Col :xs="24" :sm="12" :xl="6">
          <RouterLink to="/work-orders?view=correction">
            <Card class="workbench-metric-card" :bordered="false" hoverable>
              <Statistic title="待整改" :value="summary?.correctionCount ?? 0" />
              <p>需要及时跟进的整改任务</p>
            </Card>
          </RouterLink>
        </Col>
        <Col :xs="24" :sm="12" :xl="6">
          <RouterLink to="/work-orders?view=dispatch">
            <Card class="workbench-metric-card" :bordered="false" hoverable>
              <Statistic title="待派单" :value="summary?.dispatchCount ?? 0" />
              <p>等待平台分配责任网点</p>
            </Card>
          </RouterLink>
        </Col>
      </Row>

      <Card class="workbench-operations-card" :bordered="false">
        <template #title>
          <Tabs :active-key="'mine'" @change="openQueue">
            <Tabs.TabPane v-for="tab in queueTabs" :key="tab.key" :tab="tab.label" />
          </Tabs>
        </template>
        <template #extra>
          <RouterLink class="workbench-directory-link" to="/work-orders">
            进入工单中心 <RightOutlined />
          </RouterLink>
        </template>

        <div class="workbench-section-heading">
          <div>
            <h2>需要立即处理</h2>
            <p>已按 SLA 风险、预约时间和责任状态排序。</p>
          </div>
        </div>

        <Form layout="inline" class="workbench-query-form" @submit.prevent="search">
          <Form.Item class="workbench-search-field">
            <Input v-model:value="keyword" placeholder="搜索工单编号、客户或项目" allow-clear>
              <template #prefix><SearchOutlined /></template>
            </Input>
          </Form.Item>
          <Form.Item>
            <Select
              v-model:value="projectId"
              placeholder="全部项目"
              allow-clear
              :options="tasksQuery.data.value?.projectOptions.map((item) => ({ value: item.id, label: item.name })) ?? []"
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
                { value: 'BREACHED', label: '已超时' },
                { value: 'NEAR', label: '即将超时' },
              ]"
            />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" html-type="submit">查询</Button>
              <Button @click="reset">重置</Button>
              <Button><CalendarOutlined />保存视图</Button>
              <Button><DownloadOutlined />导出</Button>
            </Space>
          </Form.Item>
        </Form>

        <Table
          row-key="id"
          size="middle"
          :columns="columns"
          :data-source="tasksQuery.data.value?.items ?? []"
          :loading="tasksQuery.isLoading.value"
          :pagination="false"
          :scroll="{ x: 1364 }"
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
                <span>{{ record.clientName }}</span>
              </div>
            </template>
            <template v-else-if="column.key === 'project'">
              <div class="table-primary-cell">
                <strong>{{ record.projectName || '数据不完整' }}</strong>
                <span>{{ record.serviceName }}</span>
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
              <strong>当前没有待处理工单</strong>
              <span>请切换业务视图或调整筛选条件。</span>
            </div>
          </template>
        </Table>

        <div class="workbench-table-footer">
          <span>共 {{ tasksQuery.data.value?.totalCount ?? 0 }} 条</span>
          <RouterLink class="table-primary-link" to="/work-orders">查看全部工单</RouterLink>
        </div>
      </Card>
    </template>
  </Page>
</template>
