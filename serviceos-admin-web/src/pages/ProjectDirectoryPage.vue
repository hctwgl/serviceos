<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { Alert, Button, Input, Select, Space, Table, Tag } from 'ant-design-vue'
import ListPageLayout from '../patterns/templates/ListPageLayout.vue'
import DedicatedFlowLayout from '../patterns/templates/DedicatedFlowLayout.vue'
import SummaryStrip, { type SummaryStripItem } from '../patterns/SummaryStrip.vue'
import { createProject, listAuthorizedProjects, type ProjectPage } from '../api/projects'
import { firstRouteQuery } from '../routeQuery'
import { statusLabel } from '../product/statusLabels'
import { formatDateTimeDisplay } from '../presentation/date-time.presenter'
import { labelClientCode } from '../presentation/enum-labels'

const route = useRoute()

const loading = ref(false)
const busy = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)
const page = ref<ProjectPage | null>(null)
const cursor = ref<string | undefined>()
/** 运营默认 ACTIVE；显式 route.query 可覆盖。 */
const status = ref<string | undefined>('ACTIVE')
const clientId = ref('')
const activeOn = ref('')
const keyword = ref('')
const createOpen = ref(false)

const createCode = ref('')
const createClientId = ref('client-demo')
const createName = ref('')
const createStartsOn = ref(new Date().toISOString().slice(0, 10))
const createEndsOn = ref('')
const createRegionCodes = ref('')
const createNetworkIds = ref('')
const createdProjectId = ref('')

const summaryItems = computed<SummaryStripItem[]>(() => {
  const items = page.value?.items ?? []
  const active = items.filter((item) => item.status === 'ACTIVE').length
  const draft = items.filter((item) => item.status === 'DRAFT').length
  const suspended = items.filter((item) => item.status === 'SUSPENDED').length
  return [
    { key: 'page', label: '本页项目', value: String(items.length) },
    { key: 'active', label: '生效中', value: String(active), tone: 'success' },
    { key: 'draft', label: '草稿', value: String(draft), tone: draft ? 'info' : 'default' },
    {
      key: 'suspended',
      label: '已暂停',
      value: String(suspended),
      tone: suspended ? 'warning' : 'default',
    },
  ]
})

const columns = [
  { title: '项目名称', dataIndex: 'name', key: 'name' },
  { title: '项目编码', dataIndex: 'code', key: 'code' },
  { title: '所属车企', key: 'clientId' },
  { title: '状态', key: 'status' },
  { title: '生效日期', key: 'startsOn' },
  { title: '结束日期', key: 'endsOn' },
  { title: '服务区域', key: 'regions' },
  { title: '合作网点', key: 'networks' },
  { title: '操作', key: 'actions' },
]

const filteredItems = computed(() => {
  const items = page.value?.items ?? []
  const q = keyword.value.trim().toLowerCase()
  if (!q) return items
  return items.filter(
    (item) =>
      item.name.toLowerCase().includes(q) ||
      item.code.toLowerCase().includes(q) ||
      item.clientId.toLowerCase().includes(q),
  )
})

function hydrateFiltersFromRoute() {
  const nextStatus = firstRouteQuery(route, 'status')
  if (nextStatus !== undefined) {
    status.value = nextStatus || undefined
  }
  const nextClientId = firstRouteQuery(route, 'clientId')
  if (nextClientId !== undefined) {
    clientId.value = nextClientId
  }
  const nextActiveOn = firstRouteQuery(route, 'activeOn')
  if (nextActiveOn !== undefined) {
    activeOn.value = nextActiveOn
  }
}

async function load(next?: string) {
  loading.value = true
  error.value = null
  try {
    page.value = await listAuthorizedProjects({
      cursor: next,
      limit: '20',
      status: status.value || undefined,
      clientId: clientId.value.trim() || undefined,
      activeOn: activeOn.value.trim() || undefined,
    })
    cursor.value = page.value.nextCursor ?? undefined
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载项目目录失败'
  } finally {
    loading.value = false
  }
}

function search() {
  cursor.value = undefined
  return load()
}

function resetFilters() {
  status.value = 'ACTIVE'
  clientId.value = ''
  activeOn.value = ''
  keyword.value = ''
  cursor.value = undefined
  return load()
}

async function create() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    if (!createCode.value.trim() || !createName.value.trim()) {
      throw new Error('请填写项目编码与名称')
    }
    const regionCodes = createRegionCodes.value
      .split(/[,\s]+/)
      .map((v) => v.trim())
      .filter(Boolean)
    const networkIds = createNetworkIds.value
      .split(/[,\s]+/)
      .map((v) => v.trim())
      .filter(Boolean)
    const created = await createProject({
      code: createCode.value.trim(),
      clientId: createClientId.value.trim(),
      name: createName.value.trim(),
      startsOn: createStartsOn.value,
      endsOn: createEndsOn.value.trim() || null,
      regionCodes,
      networkIds,
    })
    createdProjectId.value = created.data.id
    message.value = `已创建项目 ${created.data.name}（${created.data.code}）`
    createOpen.value = false
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '创建项目失败'
  } finally {
    busy.value = false
  }
}

onMounted(() => {
  hydrateFiltersFromRoute()
  return load()
})
</script>

<template>
  <div data-testid="project-directory-page">
    <DedicatedFlowLayout
      v-if="createOpen"
      title="新建项目"
      description="创建车企项目并设置初始服务范围。创建后进入项目详情继续配置履约方案。"
      sticky-note="创建项目是高风险配置入口，请确认编码与车企后提交。"
      data-testid="project-create-flow"
    >
      <template #back>
        <Button type="text" @click="createOpen = false">返回项目列表</Button>
      </template>
      <template #feedback>
        <Alert v-if="error" type="error" show-icon :message="error" style="margin-bottom: 12px" />
        <Alert v-if="message" type="success" show-icon :message="message" style="margin-bottom: 12px" />
      </template>
      <template #sticky-secondary>
        <Button @click="createOpen = false">取消</Button>
      </template>
      <template #sticky-actions>
        <Button type="primary" :loading="busy" data-testid="project-create-submit" @click="create">
          创建项目
        </Button>
      </template>

      <div class="create-grid">
        <label>
          <span>项目编码</span>
          <Input v-model:value="createCode" placeholder="例如 PRJ_DEMO_01" aria-label="project code" />
        </label>
        <label>
          <span>所属车企</span>
          <Input v-model:value="createClientId" aria-label="project clientId" />
        </label>
        <label class="wide">
          <span>项目名称</span>
          <Input v-model:value="createName" aria-label="project name" />
        </label>
        <label>
          <span>生效日期</span>
          <Input v-model:value="createStartsOn" type="date" aria-label="project startsOn" />
        </label>
        <label>
          <span>结束日期（可选）</span>
          <Input v-model:value="createEndsOn" type="date" aria-label="project endsOn" />
        </label>
        <label class="wide">
          <span>服务区域编码（逗号分隔，可选）</span>
          <Input v-model:value="createRegionCodes" aria-label="project regionCodes" />
        </label>
        <label class="wide">
          <span>合作网点（逗号分隔 ID，可选）</span>
          <Input v-model:value="createNetworkIds" aria-label="project networkIds" />
        </label>
      </div>
      <p class="muted">
        UI_DATA_GAP：车企/区域/网点实体选择器尚未完整交付，当前仍接受编码输入，不猜测显示名。
      </p>
    </DedicatedFlowLayout>

    <ListPageLayout
      v-else
      title="项目管理"
      description="管理车企项目、服务范围、合作网点与履约配置入口。"
      :loading="loading"
      :count-label="page ? `本页 ${filteredItems.length} 个项目` : undefined"
      @search="search"
      @reset="resetFilters"
    >
      <template #primary-action>
        <Button type="primary" data-testid="project-directory-create" @click="createOpen = true">
          新建项目
        </Button>
      </template>
      <template #secondary-actions>
        <Button :loading="loading" @click="load()">刷新</Button>
      </template>
      <template #feedback>
        <Alert v-if="error" type="error" show-icon :message="error" style="margin-bottom: 12px" />
        <Alert v-if="message" type="success" show-icon :message="message" style="margin-bottom: 12px" />
        <Alert
          v-if="createdProjectId"
          type="info"
          show-icon
          message="项目已创建"
          style="margin-bottom: 12px"
        >
          <template #action>
            <RouterLink :to="{ name: 'ADMIN.PROJECT.DETAIL', params: { id: createdProjectId } }">
              打开新建项目
            </RouterLink>
          </template>
        </Alert>
      </template>

      <template #filters>
        <label class="filter">
          <span>关键词</span>
          <Input
            v-model:value="keyword"
            allow-clear
            placeholder="项目名称 / 编码 / 车企"
            aria-label="project keyword filter"
          />
        </label>
        <label class="filter">
          <span>项目状态</span>
          <Select
            v-model:value="status"
            allow-clear
            style="min-width: 160px"
            aria-label="project status filter"
            placeholder="全部"
            :options="[
              { value: 'DRAFT', label: '草稿' },
              { value: 'ACTIVE', label: '生效中' },
              { value: 'SUSPENDED', label: '已暂停' },
              { value: 'CLOSED', label: '已关闭' },
            ]"
          />
        </label>
        <label class="filter">
          <span>所属车企</span>
          <Input
            v-model:value="clientId"
            allow-clear
            aria-label="project clientId filter"
            placeholder="车企编码"
          />
        </label>
        <label class="filter">
          <span>生效日期</span>
          <Input v-model:value="activeOn" type="date" aria-label="project activeOn filter" />
        </label>
      </template>

      <SummaryStrip :items="summaryItems" />

      <Table
        data-testid="project-directory-table"
        size="middle"
        :loading="loading"
        :pagination="false"
        :columns="columns"
        :data-source="filteredItems"
        :row-key="(row) => row.id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'clientId'">
            {{ labelClientCode(record.clientId) }}
          </template>
          <template v-else-if="column.key === 'status'">
            <Tag
              :color="
                record.status === 'ACTIVE'
                  ? 'success'
                  : record.status === 'SUSPENDED'
                    ? 'warning'
                    : 'default'
              "
            >
              {{ statusLabel(record.status) }}
            </Tag>
          </template>
          <template v-else-if="column.key === 'startsOn'">
            {{ record.startsOn || '—' }}
          </template>
          <template v-else-if="column.key === 'endsOn'">
            {{ record.endsOn || '—' }}
          </template>
          <template v-else-if="column.key === 'regions'">
            {{ record.regionCodes?.length ?? 0 }}
          </template>
          <template v-else-if="column.key === 'networks'">
            {{ record.networkIds?.length ?? 0 }}
          </template>
          <template v-else-if="column.key === 'actions'">
            <Space>
              <RouterLink :to="{ name: 'ADMIN.PROJECT.DETAIL', params: { id: record.id } }">
                打开
              </RouterLink>
              <RouterLink
                :to="{
                  name: 'ADMIN.PROJECT.FULFILLMENT.LIST',
                  params: { id: record.id },
                }"
              >
                履约配置
              </RouterLink>
            </Space>
          </template>
        </template>
      </Table>
      <p v-if="!loading && !filteredItems.length" class="muted">暂无符合条件的项目</p>

      <template #pagination>
        <Button
          v-if="cursor"
          :loading="loading"
          data-testid="project-directory-next"
          @click="load(cursor)"
        >
          下一页
        </Button>
        <span v-if="page?.asOf" class="muted">
          统计时间 {{ formatDateTimeDisplay(page.asOf) }}
        </span>
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
.create-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.create-grid .wide {
  grid-column: 1 / -1;
}
.create-grid label {
  display: grid;
  gap: 4px;
  font-size: 13px;
}
.muted {
  color: var(--sos-color-text-tertiary, #6b7280);
  font-size: 13px;
}
@media (max-width: 720px) {
  .create-grid {
    grid-template-columns: 1fr;
  }
}
</style>
