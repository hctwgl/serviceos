<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { Table, Select, Input, Button, Space, Alert, Tooltip } from 'ant-design-vue'
import type { TableColumnsType } from 'ant-design-vue'
import SavedViewBar from '../components/SavedViewBar.vue'
import ListPageLayout from '../patterns/templates/ListPageLayout.vue'
import SemanticStatusTag from '../components/business/SemanticStatusTag.vue'
import AsyncContent from '../components/feedback/AsyncContent.vue'
import { listAuthorizedWorkOrders, type WorkOrderPage } from '../api/workOrders'
import { firstRouteQuery } from '../routeQuery'
import { statusOptions } from '../product/statusLabels'
import { toUserFacingError } from '../product/errorMessages'
import { presentWorkOrderStatus } from '../presentation/work-order-status.presenter'
import { labelClientCode } from '../presentation/enum-labels'
import { presentEntityName } from '../presentation/entity-name.presenter'
import { formatDateTimeDisplay } from '../presentation/date-time.presenter'
import { presentEmptyValue } from '../presentation/empty-value.presenter'

const statusChoices = statusOptions(['RECEIVED', 'ACTIVE', 'FULFILLED', 'CANCELLED'])

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const error = ref<string | null>(null)
const errorCode = ref<string | null>(null)
const page = ref<WorkOrderPage | null>(null)
const cursor = ref<string | undefined>()
const pageSize = ref(20)
const status = ref<string | undefined>(undefined)
const clientCode = ref<string | undefined>(undefined)
const projectKeyword = ref('')
const keyword = ref('')

/** 更多筛选：当前列表 API 未提供对应查询参数，仅展示缺口说明。 */
const moreRegion = ref('')
const moreNetwork = ref('')
const moreTechnician = ref('')
const moreStage = ref('')
const moreSla = ref<string | undefined>(undefined)

function hydrateFiltersFromRoute() {
  const nextStatus = firstRouteQuery(route, 'status')
  if (nextStatus !== undefined) status.value = nextStatus || undefined
  const nextClientCode = firstRouteQuery(route, 'clientCode')
  if (nextClientCode !== undefined) clientCode.value = nextClientCode || undefined
  const nextProjectId = firstRouteQuery(route, 'projectId')
  if (nextProjectId !== undefined) projectKeyword.value = nextProjectId
  const nextKeyword = firstRouteQuery(route, 'q')
  if (nextKeyword !== undefined) keyword.value = nextKeyword
}

function looksLikeUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
    value.trim(),
  )
}

async function load(next?: string) {
  loading.value = true
  error.value = null
  errorCode.value = null
  try {
    page.value = await listAuthorizedWorkOrders({
      cursor: next,
      limit: String(pageSize.value),
      status: status.value || undefined,
      clientCode: clientCode.value?.trim() || undefined,
      projectId: looksLikeUuid(projectKeyword.value)
        ? projectKeyword.value.trim()
        : undefined,
    })
    cursor.value = page.value.nextCursor ?? undefined
  } catch (err) {
    const facing = toUserFacingError(err)
    error.value = facing.message
    errorCode.value = facing.errorCode
    page.value = null
    cursor.value = undefined
  } finally {
    loading.value = false
  }
}

function search() {
  cursor.value = undefined
  return load()
}

function currentFilters() {
  return {
    status: status.value || undefined,
    clientCode: clientCode.value?.trim() || undefined,
    projectId: looksLikeUuid(projectKeyword.value)
      ? projectKeyword.value.trim()
      : undefined,
  }
}

function applySavedView(filters: Record<string, string>) {
  status.value = filters.status || undefined
  clientCode.value = filters.clientCode || undefined
  projectKeyword.value = filters.projectId ?? ''
  return search()
}

function resetFilters() {
  status.value = undefined
  clientCode.value = undefined
  projectKeyword.value = ''
  keyword.value = ''
  moreRegion.value = ''
  moreNetwork.value = ''
  moreTechnician.value = ''
  moreStage.value = ''
  moreSla.value = undefined
  return search()
}

type Row = {
  key: string
  id: string
  externalOrderCode: string
  status: string
  clientCode: string
  projectId: string
  receivedAt: string
}

const rows = computed((): Row[] => {
  const q = keyword.value.trim().toLowerCase()
  const project = projectKeyword.value.trim().toLowerCase()
  return (page.value?.items ?? [])
    .filter((item) => {
      if (q) {
        const hay = `${item.externalOrderCode} ${item.clientCode}`.toLowerCase()
        if (!hay.includes(q)) return false
      }
      if (project && !looksLikeUuid(projectKeyword.value)) {
        if (
          !item.projectId.toLowerCase().includes(project) &&
          !item.clientCode.toLowerCase().includes(project)
        ) {
          return false
        }
      }
      return true
    })
    .map((item) => ({
      key: item.id,
      id: item.id,
      externalOrderCode: item.externalOrderCode,
      status: item.status,
      clientCode: item.clientCode,
      projectId: item.projectId,
      receivedAt: item.receivedAt,
    }))
})

const countLabel = computed(() => {
  if (error.value) return undefined
  const n = rows.value.length
  if (cursor.value) return `已加载 ${n} 条，还有更多（列表无总数，UI_DATA_GAP）`
  return `已加载 ${n} 条`
})

const columns = computed((): TableColumnsType<Row> => [
  {
    title: '工单编号',
    dataIndex: 'externalOrderCode',
    key: 'externalOrderCode',
    fixed: 'left',
    width: 160,
    customRender: ({ record }) =>
      h(
        RouterLink,
        {
          to: { name: 'ADMIN.WORKORDER.WORKSPACE', params: { id: record.id } },
          class: 'work-order-link',
        },
        () => record.externalOrderCode || presentEmptyValue('not_provided'),
      ),
  },
  {
    title: '当前状态',
    dataIndex: 'status',
    key: 'status',
    width: 120,
    customRender: ({ record }) =>
      h(SemanticStatusTag, { presentation: presentWorkOrderStatus(record.status) }),
  },
  {
    title: '当前阶段',
    key: 'stage',
    width: 120,
    customRender: () =>
      h(
        Tooltip,
        { title: '工单目录投影未提供阶段字段（UI_DATA_GAP）' },
        () => presentEmptyValue('not_loaded'),
      ),
  },
  {
    title: '车企',
    dataIndex: 'clientCode',
    key: 'clientCode',
    width: 120,
    customRender: ({ record }) => labelClientCode(record.clientCode),
  },
  {
    title: '所属项目',
    dataIndex: 'projectId',
    key: 'projectId',
    width: 140,
    customRender: ({ record }) => {
      const name = presentEntityName({ id: record.projectId, loaded: true })
      return h(
        Tooltip,
        { title: import.meta.env.DEV ? name.technicalId : '项目名称暂未随目录返回' },
        () =>
          h(
            RouterLink,
            {
              // 真实链接：键盘可达，且可访问名称带项目标识供冒烟与跨页跳转定位。
              to: { name: 'ADMIN.PROJECT.DETAIL', params: { id: record.projectId } },
              'aria-label': `打开项目 ${record.projectId}`,
              class: 'project-link',
            },
            () => name.label,
          ),
      )
    },
  },
  {
    title: '客户',
    key: 'customer',
    width: 100,
    customRender: () =>
      h(Tooltip, { title: '目录投影未提供客户字段（UI_DATA_GAP）' }, () =>
        presentEmptyValue('not_provided'),
      ),
  },
  {
    title: '服务区域',
    key: 'region',
    width: 100,
    customRender: () =>
      h(Tooltip, { title: '目录投影未提供服务区域（UI_DATA_GAP）' }, () =>
        presentEmptyValue('not_provided'),
      ),
  },
  {
    title: '当前责任人',
    key: 'assignee',
    width: 110,
    customRender: () =>
      h(Tooltip, { title: '目录投影未提供责任人（UI_DATA_GAP）' }, () =>
        presentEmptyValue('not_provided'),
      ),
  },
  {
    title: 'SLA',
    key: 'sla',
    width: 90,
    customRender: () =>
      h(Tooltip, { title: '目录投影未提供 SLA 摘要（UI_DATA_GAP）' }, () =>
        presentEmptyValue('not_provided'),
      ),
  },
  {
    title: '更新时间',
    dataIndex: 'receivedAt',
    key: 'receivedAt',
    width: 150,
    customRender: ({ record }) =>
      h(
        Tooltip,
        { title: '列表当前返回接收时间，非独立 updatedAt（UI_DATA_GAP）' },
        () => formatDateTimeDisplay(record.receivedAt),
      ),
  },
  {
    title: '操作',
    key: 'actions',
    fixed: 'right',
    width: 100,
    customRender: ({ record }) =>
      h(
        Button,
        {
          type: 'link',
          onClick: () =>
            router.push({ name: 'ADMIN.WORKORDER.WORKSPACE', params: { id: record.id } }),
        },
        () => '打开详情',
      ),
  },
])

onMounted(() => {
  hydrateFiltersFromRoute()
  return load()
})
</script>

<template>
  <ListPageLayout
    title="工单中心"
    description="统一查询和处理家充勘测、安装、整改与审核工单。"
    :loading="loading"
    :count-label="countLabel"
    @search="search"
    @reset="resetFilters"
  >
    <template #secondary-actions>
      <Button :loading="loading" @click="load()">刷新</Button>
    </template>

    <template #feedback>
      <Alert
        v-if="error"
        type="error"
        show-icon
        :message="error"
        :description="errorCode ? `问题编号：${errorCode}` : undefined"
      />
    </template>

    <template #filters>
      <label class="filter-field">
        <span>工单状态</span>
        <Select
          v-model:value="status"
          allow-clear
          placeholder="不限"
          style="width: 160px"
          aria-label="工单状态筛选"
          :options="statusChoices.map((o) => ({ value: o.value, label: o.label }))"
        />
      </label>
      <label class="filter-field">
        <span>车企</span>
        <Select
          v-model:value="clientCode"
          allow-clear
          placeholder="选择车企"
          style="width: 160px"
          aria-label="车企筛选"
          :options="[
            { value: 'GEELY', label: '吉利汽车' },
            { value: 'BYD', label: '比亚迪' },
          ]"
        />
      </label>
      <label class="filter-field">
        <span>所属项目</span>
        <Input
          v-model:value="projectKeyword"
          allow-clear
          style="width: 180px"
          aria-label="所属项目筛选"
          placeholder="项目名称或编号"
        />
      </label>
      <label class="filter-field">
        <span>关键词</span>
        <Input
          v-model:value="keyword"
          allow-clear
          style="width: 280px"
          aria-label="工单关键词筛选"
          placeholder="搜索工单编号、客户、手机号后四位或地址"
        />
      </label>
    </template>

    <template #more-filters>
      <Alert
        type="info"
        show-icon
        message="更多筛选暂不可用"
        description="服务区域、网点、师傅、阶段、SLA、创建时间等条件尚未由工单目录查询 API 提供（UI_DATA_GAP），不会假装可筛。"
      />
      <label class="filter-field">
        <span>服务区域</span>
        <Input v-model:value="moreRegion" disabled placeholder="暂未提供" />
      </label>
      <label class="filter-field">
        <span>服务网点</span>
        <Input v-model:value="moreNetwork" disabled placeholder="暂未提供" />
      </label>
      <label class="filter-field">
        <span>服务师傅</span>
        <Input v-model:value="moreTechnician" disabled placeholder="暂未提供" />
      </label>
      <label class="filter-field">
        <span>当前阶段</span>
        <Input v-model:value="moreStage" disabled placeholder="暂未提供" />
      </label>
      <label class="filter-field">
        <span>SLA 状态</span>
        <Select
          v-model:value="moreSla"
          disabled
          placeholder="暂未提供"
          style="width: 160px"
          :options="[]"
        />
      </label>
    </template>

    <template #toolbar-views>
      <SavedViewBar
        page-id="ADMIN.WORKORDER.LIST"
        :schema-version="1"
        :current-filters="currentFilters()"
        @apply="applySavedView"
      />
    </template>

    <AsyncContent :loading="loading && !page" :error="null" :empty="!loading && !error && rows.length === 0" empty-description="当前没有工单。可调整筛选，或到演示数据管理初始化演示工单。">
      <Table
        size="middle"
        row-key="key"
        :columns="columns"
        :data-source="rows"
        :pagination="false"
        :scroll="{ x: 1400 }"
        :loading="loading"
        data-testid="work-order-table"
      />
    </AsyncContent>

    <template #pagination>
      <Space>
        <span>每页</span>
        <label class="filter-field">
          <span class="sr-only">每页条数</span>
          <Select
            v-model:value="pageSize"
            style="width: 88px"
            :options="[
              { value: 20, label: '20' },
              { value: 50, label: '50' },
              { value: 100, label: '100' },
            ]"
            @change="search"
          />
        </label>
        <Button :disabled="loading || !cursor" @click="load(cursor)">下一页</Button>
      </Space>
    </template>
  </ListPageLayout>
</template>

<style scoped>
.filter-field {
  display: grid;
  gap: 4px;
  font-size: 13px;
  color: var(--sos-color-text-secondary, #4b5563);
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
</style>
