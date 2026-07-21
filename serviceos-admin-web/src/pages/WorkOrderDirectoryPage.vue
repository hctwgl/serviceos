<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { Table, Select, Input, Button, Space, Alert, Tooltip, DatePicker } from 'ant-design-vue'
import type { TableColumnsType } from 'ant-design-vue'
import dayjs, { type Dayjs } from 'dayjs'
import SavedViewBar from '../components/SavedViewBar.vue'
import ListPageLayout from '../patterns/templates/ListPageLayout.vue'
import SemanticStatusTag from '../components/business/SemanticStatusTag.vue'
import AsyncContent from '../components/feedback/AsyncContent.vue'
import { listAuthorizedWorkOrders, type WorkOrderPage } from '../api/workOrders'
import { listRegionCatalog, type RegionCatalogItem } from '../api/projectCatalog'
import { listServiceNetworks, type ServiceNetwork } from '../api/networks'
import { listTechnicianProfiles, type TechnicianProfile } from '../api/technicians'
import { firstRouteQuery } from '../routeQuery'
import { statusLabel, statusOptions } from '../product/statusLabels'
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
/** M431：region-catalog 编码→名称；未命中时区域列回退国标码。 */
const regionNameByCode = ref<Map<string, string>>(new Map())
/** M437：region-catalog 编码→级别，用于选择服务区域后映射查询参数。 */
const regionLevelByCode = ref<Map<string, RegionCatalogItem['regionLevel']>>(new Map())

/** M437：服务区域筛选（国标码）。 */
const regionFilterCode = ref<string | undefined>(undefined)
/** M438：当前阶段筛选（与目录列同口径）。 */
const stageFilterCode = ref<string | undefined>(undefined)
/** M446：当前任务状态筛选（与目录列同口径）。 */
const taskStatusFilterCode = ref<string | undefined>(undefined)
/** M447：审核/整改运营桶筛选。 */
const reviewCorrectionFilter = ref<string | undefined>(undefined)
/** M440：服务网点筛选（与目录 currentNetworkId 同口径）。 */
const networkFilterId = ref<string | undefined>(undefined)
const networkOptions = ref<ServiceNetwork[]>([])
/** M441：服务师傅筛选（与目录 currentTechnicianId 同口径）。 */
const technicianFilterId = ref<string | undefined>(undefined)
const technicianOptions = ref<TechnicianProfile[]>([])
/** M442：SLA 风险筛选（与目录列 OPEN/BREACHED 同口径）。 */
const slaRiskFilter = ref<string | undefined>(undefined)
/** M443：创建日闭区间（Asia/Shanghai；按 receivedAt）。 */
const receivedRange = ref<[Dayjs, Dayjs] | undefined>(undefined)

/** 常用履约阶段；未知码由服务端校验失败关闭，不在此发明业务阶段。 */
const stageFilterOptions = statusOptions([
  'SURVEY',
  'INSTALLATION',
  'REPAIR',
  'CORRECTION',
  'SECOND_VISIT',
  'PILOT_SURVEY',
  'PILOT_INSTALL',
  'PILOT_COMPLETION',
  'HOME_CHARGING_SURVEY_INSTALL',
])

const taskStatusFilterOptions = statusOptions([
  'READY',
  'PENDING',
  'CLAIMED',
  'RUNNING',
  'RETRY_WAIT',
  'MANUAL_INTERVENTION',
])

const regionFilterOptions = computed(() =>
  [...regionNameByCode.value.entries()].map(([code, name]) => ({
    value: code,
    label: `${name}（${code}）`,
  })),
)

const networkFilterOptions = computed(() =>
  networkOptions.value
    .filter((item) => item.status === 'ACTIVE')
    .map((item) => ({
      value: item.id,
      label: `${item.networkName}（${item.networkCode}）`,
    })),
)

const technicianFilterOptions = computed(() =>
  technicianOptions.value
    .filter((item) => item.status === 'ACTIVE')
    .map((item) => ({
      value: item.id,
      label: item.displayName?.trim() || item.id,
    })),
)

const slaRiskFilterOptions = [
  { value: 'OPEN', label: '有开放风险' },
  { value: 'BREACHED', label: '已超时' },
  { value: 'NEAR', label: '即将超时' },
]

const reviewCorrectionFilterOptions = [
  { value: 'REVIEW_OPEN', label: '待审核' },
  { value: 'CORRECTION_ACTIVE', label: '整改中' },
]

function regionQueryParams(): {
  provinceCode?: string
  cityCode?: string
  districtCode?: string
} {
  const code = regionFilterCode.value?.trim()
  if (!code) return {}
  const level = regionLevelByCode.value.get(code)
  if (level === 'PROVINCE') return { provinceCode: code }
  if (level === 'CITY') return { cityCode: code }
  if (level === 'DISTRICT') return { districtCode: code }
  // 目录未命中时按区县精确匹配，不猜测层级。
  return { districtCode: code }
}

function hydrateFiltersFromRoute() {
  const nextStatus = firstRouteQuery(route, 'status')
  if (nextStatus !== undefined) status.value = nextStatus || undefined
  const nextClientCode = firstRouteQuery(route, 'clientCode')
  if (nextClientCode !== undefined) clientCode.value = nextClientCode || undefined
  const nextProjectId = firstRouteQuery(route, 'projectId')
  if (nextProjectId !== undefined) projectKeyword.value = nextProjectId
  const nextKeyword = firstRouteQuery(route, 'q')
  if (nextKeyword !== undefined) keyword.value = nextKeyword
  const nextProvince = firstRouteQuery(route, 'provinceCode')
  const nextCity = firstRouteQuery(route, 'cityCode')
  const nextDistrict = firstRouteQuery(route, 'districtCode')
  // 路由优先最细粒度；与 API AND 语义一致时 UI 只保留一个选择值。
  if (nextDistrict) regionFilterCode.value = nextDistrict
  else if (nextCity) regionFilterCode.value = nextCity
  else if (nextProvince) regionFilterCode.value = nextProvince
  const nextStage = firstRouteQuery(route, 'currentStageCode')
  if (nextStage !== undefined) stageFilterCode.value = nextStage || undefined
  const nextTaskStatus = firstRouteQuery(route, 'currentTaskStatus')
  if (nextTaskStatus !== undefined) taskStatusFilterCode.value = nextTaskStatus || undefined
  const nextReviewCorrection = firstRouteQuery(route, 'reviewCorrectionStatus')
  if (nextReviewCorrection !== undefined)
    reviewCorrectionFilter.value = nextReviewCorrection || undefined
  const nextNetwork = firstRouteQuery(route, 'currentNetworkId')
  if (nextNetwork !== undefined) networkFilterId.value = nextNetwork || undefined
  const nextTechnician = firstRouteQuery(route, 'currentTechnicianId')
  if (nextTechnician !== undefined) technicianFilterId.value = nextTechnician || undefined
  const nextSlaRisk = firstRouteQuery(route, 'slaRisk')
  if (nextSlaRisk !== undefined) slaRiskFilter.value = nextSlaRisk || undefined
  const nextReceivedFrom = firstRouteQuery(route, 'receivedFrom')
  const nextReceivedTo = firstRouteQuery(route, 'receivedTo')
  if (nextReceivedFrom || nextReceivedTo) {
    const from = dayjs(nextReceivedFrom || nextReceivedTo)
    const to = dayjs(nextReceivedTo || nextReceivedFrom)
    receivedRange.value = from.isValid() && to.isValid() ? [from, to] : undefined
  }
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
      currentStageCode: stageFilterCode.value || undefined,
      currentTaskStatus: taskStatusFilterCode.value || undefined,
      reviewCorrectionStatus: reviewCorrectionFilter.value || undefined,
      q: keyword.value.trim() || undefined,
      currentNetworkId: networkFilterId.value || undefined,
      currentTechnicianId: technicianFilterId.value || undefined,
      slaRisk: slaRiskFilter.value || undefined,
      receivedFrom: receivedRange.value?.[0]?.format('YYYY-MM-DD'),
      receivedTo: receivedRange.value?.[1]?.format('YYYY-MM-DD'),
      ...regionQueryParams(),
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
  const region = regionQueryParams()
  return {
    status: status.value || undefined,
    clientCode: clientCode.value?.trim() || undefined,
    projectId: looksLikeUuid(projectKeyword.value)
      ? projectKeyword.value.trim()
      : undefined,
    currentStageCode: stageFilterCode.value || undefined,
    currentTaskStatus: taskStatusFilterCode.value || undefined,
    reviewCorrectionStatus: reviewCorrectionFilter.value || undefined,
    q: keyword.value.trim() || undefined,
    currentNetworkId: networkFilterId.value || undefined,
    currentTechnicianId: technicianFilterId.value || undefined,
    slaRisk: slaRiskFilter.value || undefined,
    receivedFrom: receivedRange.value?.[0]?.format('YYYY-MM-DD'),
    receivedTo: receivedRange.value?.[1]?.format('YYYY-MM-DD'),
    ...region,
  }
}

function applySavedView(filters: Record<string, string>) {
  status.value = filters.status || undefined
  clientCode.value = filters.clientCode || undefined
  projectKeyword.value = filters.projectId ?? ''
  regionFilterCode.value =
    filters.districtCode || filters.cityCode || filters.provinceCode || undefined
  stageFilterCode.value = filters.currentStageCode || undefined
  taskStatusFilterCode.value = filters.currentTaskStatus || undefined
  reviewCorrectionFilter.value = filters.reviewCorrectionStatus || undefined
  keyword.value = filters.q ?? ''
  networkFilterId.value = filters.currentNetworkId || undefined
  technicianFilterId.value = filters.currentTechnicianId || undefined
  slaRiskFilter.value = filters.slaRisk || undefined
  if (filters.receivedFrom || filters.receivedTo) {
    const from = dayjs(filters.receivedFrom || filters.receivedTo)
    const to = dayjs(filters.receivedTo || filters.receivedFrom)
    receivedRange.value = from.isValid() && to.isValid() ? [from, to] : undefined
  } else {
    receivedRange.value = undefined
  }
  return search()
}

function resetFilters() {
  status.value = undefined
  clientCode.value = undefined
  projectKeyword.value = ''
  keyword.value = ''
  regionFilterCode.value = undefined
  stageFilterCode.value = undefined
  taskStatusFilterCode.value = undefined
  reviewCorrectionFilter.value = undefined
  networkFilterId.value = undefined
  technicianFilterId.value = undefined
  slaRiskFilter.value = undefined
  receivedRange.value = undefined
  return search()
}

type Row = {
  key: string
  id: string
  externalOrderCode: string
  status: string
  clientCode: string
  projectId: string
  provinceCode: string
  cityCode: string
  districtCode: string
  receivedAt: string
  updatedAt: string
  maskedCustomerName: string | null
  maskedCustomerPhone: string | null
  maskedServiceAddress: string | null
  currentStageCode: string | null
  currentTaskType: string | null
  currentTaskStatus: string | null
  currentClaimedBy: string | null
  currentAssigneeDisplayName: string | null
  currentNetworkId: string | null
  currentNetworkDisplayName: string | null
  currentTechnicianId: string | null
  currentTechnicianDisplayName: string | null
}

/** M430/M431：优先展示目录中文名，未命中则回退国标码（不发明名称）。 */
function regionPartLabel(code: string | null | undefined) {
  if (code == null || String(code).trim() === '') {
    return null
  }
  const trimmed = String(code).trim()
  return regionNameByCode.value.get(trimmed) ?? trimmed
}

function regionLabel(row: Pick<Row, 'provinceCode' | 'cityCode' | 'districtCode'>) {
  const parts = [row.provinceCode, row.cityCode, row.districtCode]
    .map((code) => regionPartLabel(code))
    .filter((part): part is string => part != null)
  return parts.length ? parts.join('/') : '—'
}

function regionCodesTooltip(row: Pick<Row, 'provinceCode' | 'cityCode' | 'districtCode'>) {
  const codes = [row.provinceCode, row.cityCode, row.districtCode].filter(
    (part) => part != null && String(part).trim() !== '',
  )
  return codes.length ? `区域编码：${codes.join('/')}` : '无区域编码'
}

async function loadRegionNames() {
  try {
    const page = await listRegionCatalog({ parentCode: '*', limit: 200 })
    const names = new Map<string, string>()
    const levels = new Map<string, RegionCatalogItem['regionLevel']>()
    for (const item of page.items) {
      names.set(item.regionCode, item.regionName)
      levels.set(item.regionCode, item.regionLevel)
    }
    regionNameByCode.value = names
    regionLevelByCode.value = levels
  } catch {
    // 缺 project.read 或目录失败时保持码展示，不阻断工单目录主路径。
    regionNameByCode.value = new Map()
    regionLevelByCode.value = new Map()
  }
}

async function loadNetworkOptions() {
  try {
    const page = await listServiceNetworks()
    networkOptions.value = page.items
  } catch {
    // 缺 network.read 时筛选下拉为空；不阻断目录主路径。
    networkOptions.value = []
  }
}

async function loadTechnicianOptions() {
  try {
    const page = await listTechnicianProfiles()
    technicianOptions.value = page.items
  } catch {
    // 缺 technician 目录能力时筛选下拉为空；不阻断目录主路径。
    technicianOptions.value = []
  }
}

const rows = computed((): Row[] => {
  const project = projectKeyword.value.trim().toLowerCase()
  return (page.value?.items ?? [])
    .filter((item) => {
      // M448：关键词已由服务端 q 收敛；此处仅保留非 UUID 项目关键字的客户端辅助过滤。
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
      provinceCode: item.provinceCode,
      cityCode: item.cityCode,
      districtCode: item.districtCode,
      receivedAt: item.receivedAt,
      updatedAt: item.updatedAt,
      maskedCustomerName: item.maskedCustomerName,
      maskedCustomerPhone: item.maskedCustomerPhone,
      maskedServiceAddress: item.maskedServiceAddress,
      currentStageCode: item.currentStageCode,
      currentTaskType: item.currentTaskType,
      currentTaskStatus: item.currentTaskStatus,
      currentClaimedBy: item.currentClaimedBy,
      currentAssigneeDisplayName: item.currentAssigneeDisplayName,
      currentNetworkId: item.currentNetworkId,
      currentNetworkDisplayName: item.currentNetworkDisplayName,
      currentTechnicianId: item.currentTechnicianId,
      currentTechnicianDisplayName: item.currentTechnicianDisplayName,
    }))
})

const countLabel = computed(() => {
  if (error.value || page.value == null) return undefined
  const total = page.value.totalCount
  const truncated = page.value.totalCountTruncated
  // M444：服务端精确总数；truncatedated 防御分支保留；客户端关键词不改变 total。
  return truncated ? `共 ${total}+ 条` : `共 ${total} 条`
})

function slaRiskLabel(workOrderId: string) {
  const summaries = page.value?.slaRiskSummaries
  if (summaries === undefined || summaries === null) {
    return {
      text: presentEmptyValue('not_provided'),
      tooltip: '无 sla.read 或 SLA 旁载未返回（soft-omit）',
    }
  }
  const matched = summaries.find((row) => row.workOrderId === workOrderId)
  if (!matched) {
    return { text: '暂无', tooltip: '本页无开放 SLA 风险' }
  }
  return {
    text: `开放 ${matched.openCount} / 超时 ${matched.breachedCount}`,
    tooltip: `openCount=${matched.openCount}, breachedCount=${matched.breachedCount}`,
  }
}

const columns = computed((): TableColumnsType<Row> => {
  // 依赖 region-catalog 映射与 SLA 旁载，避免异步加载完成后列不刷新。
  void regionNameByCode.value
  void page.value?.slaRiskSummaries
  return [
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
    customRender: ({ record }: { record: Row }) => {
      const code = record.currentStageCode
      if (code == null || String(code).trim() === '') {
        return h(
          'span',
          { 'data-testid': 'work-order-current-stage' },
          presentEmptyValue('not_provided'),
        )
      }
      return h(
        Tooltip,
        { title: `阶段编码：${code}` },
        () =>
          h(
            'span',
            { 'data-testid': 'work-order-current-stage' },
            statusLabel(code),
          ),
      )
    },
  },
  {
    title: '当前任务',
    key: 'taskType',
    width: 130,
    customRender: ({ record }: { record: Row }) => {
      const code = record.currentTaskType
      if (code == null || String(code).trim() === '') {
        return h(
          'span',
          { 'data-testid': 'work-order-current-task-type' },
          presentEmptyValue('not_provided'),
        )
      }
      return h(
        Tooltip,
        { title: `任务类型：${code}` },
        () =>
          h(
            'span',
            { 'data-testid': 'work-order-current-task-type' },
            statusLabel(code),
          ),
      )
    },
  },
  {
    title: '任务状态',
    key: 'taskStatus',
    width: 110,
    customRender: ({ record }: { record: Row }) => {
      const code = record.currentTaskStatus
      if (code == null || String(code).trim() === '') {
        return h(
          'span',
          { 'data-testid': 'work-order-current-task-status' },
          presentEmptyValue('not_provided'),
        )
      }
      return h(
        Tooltip,
        { title: `任务状态：${code}` },
        () =>
          h(
            'span',
            { 'data-testid': 'work-order-current-task-status' },
            statusLabel(code),
          ),
      )
    },
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
    width: 160,
    customRender: ({ record }: { record: Row }) =>
      h(
        'div',
        { class: 'masked-customer-cell', 'data-testid': 'work-order-masked-customer' },
        [
          h(
            'div',
            { 'data-testid': 'work-order-masked-customer-name' },
            record.maskedCustomerName || '—',
          ),
          h(
            'div',
            { class: 'masked-customer-sub', 'data-testid': 'work-order-masked-customer-phone' },
            record.maskedCustomerPhone || '—',
          ),
          h(
            'div',
            { class: 'masked-customer-sub', 'data-testid': 'work-order-masked-service-address' },
            record.maskedServiceAddress || '—',
          ),
        ],
      ),
  },
  {
    title: '服务区域',
    key: 'region',
    width: 160,
    customRender: ({ record }: { record: Row }) =>
      h(
        Tooltip,
        { title: regionCodesTooltip(record) },
        () => h('span', { 'data-testid': 'work-order-region' }, regionLabel(record)),
      ),
  },
  {
    title: '当前责任人',
    key: 'assignee',
    width: 110,
    customRender: ({ record }: { record: Row }) => {
      const name = record.currentAssigneeDisplayName?.trim()
      if (name) {
        return h(
          Tooltip,
          {
            title: record.currentClaimedBy
              ? `责任主体：${record.currentClaimedBy}`
              : '当前认领责任人',
          },
          () => h('span', { 'data-testid': 'work-order-current-assignee' }, name),
        )
      }
      return h(
        Tooltip,
        {
          title: record.currentClaimedBy
            ? `已认领但无显示名（主体：${record.currentClaimedBy}）`
            : '无 ACTIVE 任务认领人',
        },
        () =>
          h(
            'span',
            { 'data-testid': 'work-order-current-assignee' },
            presentEmptyValue('not_provided'),
          ),
      )
    },
  },
  {
    title: '网点/师傅',
    key: 'networkTechnician',
    width: 160,
    customRender: ({ record }: { record: Row }) => {
      const network =
        record.currentNetworkDisplayName?.trim() ||
        (record.currentNetworkId ? presentEmptyValue('not_provided') : null)
      const technician =
        record.currentTechnicianDisplayName?.trim() ||
        (record.currentTechnicianId ? presentEmptyValue('not_provided') : null)
      if (!network && !technician) {
        return h(
          Tooltip,
          { title: '无 ACTIVE 网点或师傅服务责任' },
          () =>
            h(
              'span',
              { 'data-testid': 'work-order-network-technician' },
              presentEmptyValue('not_provided'),
            ),
        )
      }
      const label = [network, technician].filter(Boolean).join(' / ')
      const tipParts = [
        record.currentNetworkId ? `网点：${record.currentNetworkId}` : null,
        record.currentTechnicianId ? `师傅档案：${record.currentTechnicianId}` : null,
      ].filter(Boolean)
      return h(
        Tooltip,
        { title: tipParts.length > 0 ? tipParts.join('；') : '当前服务责任' },
        () => h('span', { 'data-testid': 'work-order-network-technician' }, label),
      )
    },
  },
  {
    title: 'SLA',
    key: 'sla',
    width: 120,
    customRender: ({ record }: { record: Row }) => {
      const presentation = slaRiskLabel(record.id)
      return h(
        Tooltip,
        { title: presentation.tooltip },
        () => h('span', { 'data-testid': 'work-order-sla-risk' }, presentation.text),
      )
    },
  },
  {
    title: '更新时间',
    dataIndex: 'updatedAt',
    key: 'updatedAt',
    width: 150,
    customRender: ({ record }: { record: Row }) =>
      h(
        Tooltip,
        { title: '工单聚合更新时间（独立于接收时间）' },
        () =>
          h(
            'span',
            { 'data-testid': 'work-order-updated-at' },
            formatDateTimeDisplay(record.updatedAt),
          ),
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
]
})

onMounted(() => {
  hydrateFiltersFromRoute()
  void loadRegionNames()
  void loadNetworkOptions()
  void loadTechnicianOptions()
  return load()
})

// 同组件复用时（例如从工作区返回目录深链）必须重新水合并查询，否则 route.query 失效。
watch(
  () =>
    [
      firstRouteQuery(route, 'projectId'),
      firstRouteQuery(route, 'status'),
      firstRouteQuery(route, 'clientCode'),
      firstRouteQuery(route, 'q'),
    ] as const,
  () => {
    hydrateFiltersFromRoute()
    return search()
  },
)
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
          @pressEnter="search"
        />
      </label>
      <label class="filter-field">
        <span>关键词</span>
        <Input
          v-model:value="keyword"
          allow-clear
          style="width: 280px"
          aria-label="工单关键词筛选"
          data-testid="work-order-keyword-filter"
          placeholder="搜索工单编号、客户、手机号后四位或地址"
          @pressEnter="search"
          @change="search"
        />
      </label>
    </template>

    <template #more-filters>
      <label class="filter-field" data-testid="work-order-received-range-filter">
        <span>创建时间</span>
        <DatePicker.RangePicker
          v-model:value="receivedRange"
          allow-clear
          style="width: 260px"
          aria-label="创建时间筛选"
          :placeholder="['开始日期', '结束日期']"
          @change="search"
        />
      </label>
      <label class="filter-field">
        <span>服务区域</span>
        <Select
          v-model:value="regionFilterCode"
          allow-clear
          show-search
          style="width: 240px"
          aria-label="服务区域筛选"
          data-testid="work-order-region-filter"
          placeholder="按省/市/区县筛选"
          :options="regionFilterOptions"
          :filter-option="(input, option) => String(option?.label ?? '').includes(input)"
          @change="search"
        />
      </label>
      <label class="filter-field">
        <span>当前阶段</span>
        <Select
          v-model:value="stageFilterCode"
          allow-clear
          show-search
          style="width: 200px"
          aria-label="当前阶段筛选"
          data-testid="work-order-stage-filter"
          placeholder="按当前阶段筛选"
          :options="stageFilterOptions"
          :filter-option="(input, option) => String(option?.label ?? '').includes(input)"
          @change="search"
        />
      </label>
      <label class="filter-field">
        <span>任务状态</span>
        <Select
          v-model:value="taskStatusFilterCode"
          allow-clear
          show-search
          style="width: 180px"
          aria-label="任务状态筛选"
          data-testid="work-order-task-status-filter"
          placeholder="按当前任务状态筛选"
          :options="taskStatusFilterOptions"
          :filter-option="(input, option) => String(option?.label ?? '').includes(input)"
          @change="search"
        />
      </label>
      <label class="filter-field">
        <span>审核/整改状态</span>
        <Select
          v-model:value="reviewCorrectionFilter"
          allow-clear
          style="width: 180px"
          aria-label="审核整改状态筛选"
          data-testid="work-order-review-correction-filter"
          placeholder="按审核/整改状态筛选"
          :options="reviewCorrectionFilterOptions"
          @change="search"
        />
      </label>
      <label class="filter-field">
        <span>服务网点</span>
        <Select
          v-model:value="networkFilterId"
          allow-clear
          show-search
          style="width: 240px"
          aria-label="服务网点筛选"
          data-testid="work-order-network-filter"
          placeholder="按 ACTIVE 责任网点筛选"
          :options="networkFilterOptions"
          :filter-option="(input, option) => String(option?.label ?? '').includes(input)"
          @change="search"
        />
      </label>
      <label class="filter-field">
        <span>服务师傅</span>
        <Select
          v-model:value="technicianFilterId"
          allow-clear
          show-search
          style="width: 200px"
          aria-label="服务师傅筛选"
          data-testid="work-order-technician-filter"
          placeholder="按 ACTIVE 责任师傅筛选"
          :options="technicianFilterOptions"
          :filter-option="(input, option) => String(option?.label ?? '').includes(input)"
          @change="search"
        />
      </label>
      <label class="filter-field">
        <span>SLA 风险</span>
        <Select
          v-model:value="slaRiskFilter"
          allow-clear
          style="width: 160px"
          aria-label="SLA 风险筛选"
          data-testid="work-order-sla-risk-filter"
          placeholder="按 SLA 风险筛选"
          :options="slaRiskFilterOptions"
          @change="search"
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
.masked-customer-cell {
  display: grid;
  gap: 2px;
  font-size: 13px;
  line-height: 1.35;
}
.masked-customer-sub {
  color: var(--sos-color-text-secondary, #5b6575);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
</style>
