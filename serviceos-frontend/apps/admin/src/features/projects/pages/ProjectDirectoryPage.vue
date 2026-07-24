<script setup lang="ts">
import type { AdminProjectDirectoryItem, CreateProjectInput } from '@serviceos/api-client'
import type { VxeTableGridOptions } from '@vben/plugins/vxe-table'
import { computed, reactive, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useVbenVxeGrid } from '@vben/plugins/vxe-table'
import { Button, Drawer, Form, Input, SearchOutlined, Select } from '@serviceos/design-system'
import PageError from '../../../components/PageError.vue'
import StatusPill from '../../../components/StatusPill.vue'
import { presentConfigurationStatus, presentProjectPeriod, presentProjectStatus } from '../presenters/client-project-directory'
import { useCreateProjectCommand } from '../commands/use-create-project-command'
import { useClientProjectDirectoryQuery } from '../queries/use-client-project-directory-query'
import { useProjectCreationOptionsQuery } from '../queries/use-project-creation-options-query'

const router = useRouter()
const directory = useClientProjectDirectoryQuery()
const keyword = ref('')
const clientFilter = ref<string>()
const regionFilter = ref<string>()
const statusFilter = ref<string>()
const configurationFilter = ref<string>()
const createOpen = ref(false)
const regionKeyword = ref('')
const creationOptions = useProjectCreationOptionsQuery(createOpen, regionKeyword)
const createCommand = useCreateProjectCommand()
const createForm = reactive<Omit<CreateProjectInput, 'endsOn'> & { endsOn: string }>({
  code: '',
  clientId: '',
  name: '',
  startsOn: '',
  endsOn: '',
  regionCodes: [],
  networkIds: [],
})
const projects = computed(() => {
  const normalized = keyword.value.trim().toLowerCase()
  const items = directory.data.value?.projects ?? []
  return items.filter((item) => {
    const matchesKeyword = !normalized || `${item.projectName} ${item.clientName ?? ''} ${item.regionNames.join(' ')}`.toLowerCase().includes(normalized)
    const matchesClient = !clientFilter.value || item.clientCode === clientFilter.value
    const matchesRegion = !regionFilter.value || item.regionNames.includes(regionFilter.value)
    const matchesStatus = !statusFilter.value || item.status === statusFilter.value
    const matchesConfiguration = !configurationFilter.value || item.configurationStatus === configurationFilter.value
    return matchesKeyword && matchesClient && matchesRegion && matchesStatus && matchesConfiguration
  })
})
const currentPage = ref(1)
const pageSize = ref(20)
const pagedProjects = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return projects.value.slice(start, start + pageSize.value)
})

const projectGridOptions: VxeTableGridOptions<AdminProjectDirectoryItem> = {
  columns: [
    { field: 'projectName', minWidth: 175, slots: { default: 'projectName' }, title: '项目名称' },
    { field: 'clientName', minWidth: 85, title: '客户品牌' },
    { field: 'regionNames', minWidth: 120, slots: { default: 'regions' }, title: '服务区域' },
    { field: 'startsOn', minWidth: 135, slots: { default: 'period' }, title: '服务周期' },
    { field: 'status', minWidth: 85, slots: { default: 'status' }, title: '项目状态' },
    { field: 'currentFulfillmentPlanName', minWidth: 145, slots: { default: 'plan' }, title: '当前履约方案' },
    { field: 'currentFulfillmentVersion', minWidth: 75, slots: { default: 'version' }, title: '当前版本' },
    { field: 'configurationStatus', minWidth: 105, slots: { default: 'configuration' }, title: '配置状态' },
    { field: 'updatedAt', minWidth: 115, slots: { default: 'updatedAt' }, title: '更新时间' },
    { field: 'operation', fixed: 'right', minWidth: 90, slots: { default: 'operation' }, title: '操作' },
  ],
  pagerConfig: {},
  rowConfig: { keyField: 'id' },
}
const [ProjectGrid] = useVbenVxeGrid<AdminProjectDirectoryItem>({
  gridOptions: projectGridOptions,
  showSearchForm: false,
})

const clientFilterOptions = computed(() => (directory.data.value?.clients ?? []).map((client) => ({
  value: client.clientCode,
  label: client.clientName,
})))
const regionFilterOptions = computed(() => [...new Set((directory.data.value?.projects ?? []).flatMap((item) => item.regionNames))].map((region) => ({
  value: region,
  label: region,
})))
const statusFilterOptions = [
  { value: 'ACTIVE', label: '运行中' },
  { value: 'DRAFT', label: '待启动' },
  { value: 'SUSPENDED', label: '已暂停' },
  { value: 'CLOSED', label: '已完成' },
]
const configurationFilterOptions = [
  { value: 'PUBLISHED', label: '已发布' },
  { value: 'DRAFT', label: '草稿待发布' },
  { value: 'UNPUBLISHED_CHANGES', label: '有未发布变更' },
  { value: 'NOT_CONFIGURED', label: '待首次配置' },
  { value: 'NO_PERMISSION', label: '无权查看' },
]

const clientOptions = computed(() => (creationOptions.data.value?.clients ?? []).map((item) => ({
  value: item.code,
  label: item.name,
})))
const regionOptions = computed(() => (creationOptions.data.value?.regions ?? []).map((item) => ({
  value: item.code,
  label: `${item.name} · ${regionLevelLabel(item.level)}`,
})))
const networkOptions = computed(() => (creationOptions.data.value?.networks ?? []).map((item) => ({
  value: item.id,
  label: `${item.name} · ${item.code}`,
})))
const canCreate = computed(() => creationOptions.data.value?.allowedActions.includes('CREATE_PROJECT') ?? false)
const formValid = computed(() => Boolean(
  createForm.code.trim()
  && /^[A-Za-z0-9][A-Za-z0-9_-]*$/.test(createForm.code.trim())
  && createForm.clientId
  && createForm.name.trim()
  && createForm.startsOn
  && (!createForm.endsOn || createForm.endsOn >= createForm.startsOn),
))

function regionLevelLabel(level: string) {
  if (level === 'PROVINCE') return '省级'
  if (level === 'CITY') return '市级'
  return '区县'
}

function resetFilters() {
  keyword.value = ''
  clientFilter.value = undefined
  regionFilter.value = undefined
  statusFilter.value = undefined
  configurationFilter.value = undefined
}

function handlePageChange({ currentPage: nextPage, pageSize: nextSize }: { currentPage: number; pageSize: number }) {
  currentPage.value = nextSize === pageSize.value ? nextPage : 1
  pageSize.value = nextSize
}

function fulfillmentPlanLabel(item: typeof projects.value[number]) {
  if (item.currentFulfillmentPlanName) return item.currentFulfillmentPlanName
  if (item.configurationStatus === 'NO_PERMISSION') return '无权查看方案'
  if (item.configurationStatus === 'NOT_CONFIGURED') return '尚未建立方案'
  if (item.configurationStatus === 'DRAFT') return '待发布方案'
  return '当前方案待确认'
}

function versionLabel(item: typeof projects.value[number]) {
  return item.currentFulfillmentVersion ? `V${item.currentFulfillmentVersion}` : '—'
}

function updatedAtLabel(item: typeof projects.value[number]) {
  if (!item.updatedAt) return item.configurationStatus === 'NOT_CONFIGURED' ? '—' : '待确认'
  return item.updatedAt.slice(0, 16).replace('T', ' ')
}

watch(projects, () => {
  const lastPage = Math.max(1, Math.ceil(projects.value.length / pageSize.value))
  currentPage.value = Math.min(currentPage.value, lastPage)
})

function openCreate() {
  Object.assign(createForm, {
    code: '',
    clientId: '',
    name: '',
    startsOn: '',
    endsOn: '',
    regionCodes: [],
    networkIds: [],
  })
  regionKeyword.value = ''
  createCommand.reset()
  createOpen.value = true
}

function closeCreate() {
  createOpen.value = false
  regionKeyword.value = ''
}

async function submitCreate() {
  if (!formValid.value) return
  const result = await createCommand.mutateAsync({
    ...createForm,
    code: createForm.code.trim(),
    name: createForm.name.trim(),
    endsOn: createForm.endsOn || null,
  })
  closeCreate()
  await router.push(`/projects/${result.id}`)
}
</script>

<template>
  <div class="sos-project-directory-page">
    <header class="sos-directory-heading">
      <h1>项目管理</h1>
      <div class="heading-actions"><Button type="primary" @click="openCreate">新建项目</Button></div>
    </header>
    <PageError v-if="directory.isError.value" :detail="directory.error.value?.message ?? '项目目录加载失败'" />
    <template v-else>
      <section class="sos-project-directory-surface">
        <div class="sos-project-filter-bar">
          <Input v-model:value="keyword" placeholder="搜索项目名称" allow-clear><template #prefix><SearchOutlined /></template></Input>
          <Select v-model:value="clientFilter" :options="clientFilterOptions" allow-clear placeholder="客户品牌" />
          <Select v-model:value="regionFilter" :options="regionFilterOptions" allow-clear placeholder="服务区域" />
          <Select v-model:value="statusFilter" :options="statusFilterOptions" allow-clear placeholder="项目状态" />
          <Select v-model:value="configurationFilter" :options="configurationFilterOptions" allow-clear placeholder="履约方案状态" />
          <Button type="link" @click="resetFilters">重置</Button>
        </div>
        <ProjectGrid
          class="sos-project-vxe-grid"
          :grid-events="{ pageChange: handlePageChange }"
          :grid-options="{
            ...projectGridOptions,
            loading: directory.isLoading.value,
            pagerConfig: {
              currentPage,
              pageSize,
              total: projects.length,
            },
          }"
          :table-data="pagedProjects"
        >
          <template #projectName="{ row }">
            <div class="sos-project-name-cell">
              <RouterLink class="table-link" :to="`/projects/${row.id}`"><strong>{{ row.projectName }}</strong></RouterLink>
              <small>{{ row.projectCode }}</small>
              <em v-if="!row.dataComplete">{{ row.dataProblem }}</em>
            </div>
          </template>
          <template #regions="{ row }">{{ row.regionNames.join('、') || '数据不完整' }}</template>
          <template #period="{ row }">{{ presentProjectPeriod(row.startsOn, row.endsOn) }}</template>
          <template #status="{ row }">
            <StatusPill :tone="presentProjectStatus(row.status).tone" :label="presentProjectStatus(row.status).label" />
          </template>
          <template #plan="{ row }"><strong class="sos-project-plan-name">{{ fulfillmentPlanLabel(row) }}</strong></template>
          <template #version="{ row }"><span class="sos-version-chip">{{ versionLabel(row) }}</span></template>
          <template #configuration="{ row }">
            <StatusPill :tone="presentConfigurationStatus(row.configurationStatus).tone" :label="presentConfigurationStatus(row.configurationStatus).label" />
          </template>
          <template #updatedAt="{ row }"><span class="sos-table-muted">{{ updatedAtLabel(row) }}</span></template>
          <template #operation="{ row }">
            <RouterLink class="table-link sos-table-action" :to="`/projects/${row.id}`">进入详情 <span aria-hidden="true">→</span></RouterLink>
          </template>
          <template #empty>
            <div class="sos-project-grid-empty">暂无符合条件的项目</div>
          </template>
        </ProjectGrid>
      </section>
    </template>

    <Drawer :open="createOpen" width="620" title="新建项目" placement="right" @close="closeCreate">
      <div class="create-project-intro">
        新项目以草稿状态创建。完成项目团队、区域分工和履约配置并校验后，才能投入正式接单。
      </div>
      <PageError
        v-if="creationOptions.isError.value"
        :detail="creationOptions.error.value?.message ?? '新建项目选项加载失败'"
      />
      <div v-else-if="creationOptions.isLoading.value" class="page-loading">正在加载客户、行政区域和服务网点…</div>
      <template v-else-if="creationOptions.data.value">
        <div v-if="!canCreate" class="permission-state">
          <h3>当前账号不能创建项目</h3><p>你可以继续查看项目目录，但没有项目创建权限。</p>
        </div>
        <Form v-else layout="vertical" @submit.prevent="submitCreate">
          <div class="create-project-form-grid">
            <Form.Item label="项目名称" required class="full-row">
              <Input v-model:value="createForm.name" :maxlength="200" placeholder="例如：比亚迪山东家充安装服务项目" />
            </Form.Item>
            <Form.Item label="项目编码" required>
              <Input v-model:value="createForm.code" :maxlength="64" placeholder="例如：BYD-SD-HC-2026" />
              <small>仅使用英文字母、数字、短横线和下划线，创建后作为稳定业务编码。</small>
            </Form.Item>
            <Form.Item label="合作客户" required>
              <Select
                v-model:value="createForm.clientId"
                show-search
                option-filter-prop="label"
                :options="clientOptions"
                placeholder="选择已登记客户"
              />
            </Form.Item>
            <Form.Item label="服务开始日期" required>
              <Input v-model:value="createForm.startsOn" type="date" />
            </Form.Item>
            <Form.Item label="服务结束日期">
              <Input v-model:value="createForm.endsOn" type="date" />
            </Form.Item>
            <Form.Item label="服务行政区域" class="full-row">
              <Select
                v-model:value="createForm.regionCodes"
                mode="multiple"
                show-search
                :filter-option="false"
                :options="regionOptions"
                :not-found-content="creationOptions.isFetching.value ? '正在查询行政区域…' : '未找到匹配的标准行政区域'"
                placeholder="选择项目允许服务的行政区域"
                @search="regionKeyword = $event"
              />
            </Form.Item>
            <Form.Item label="参与服务网点" class="full-row">
              <Select
                v-model:value="createForm.networkIds"
                mode="multiple"
                show-search
                option-filter-prop="label"
                :options="networkOptions"
                placeholder="选择允许参与项目的服务网点"
              />
            </Form.Item>
          </div>
          <PageError
            v-if="createCommand.isError.value"
            :detail="createCommand.error.value?.message ?? '项目创建失败'"
          />
          <div class="project-create-summary">
            <strong>创建后下一步</strong>
            <span>维护项目团队与区域分工 → 建立履约方案 → 校验并发布 → 启用项目接单</span>
          </div>
          <div class="drawer-actions">
            <Button @click="closeCreate">取消</Button>
            <Button type="primary" html-type="submit" :loading="createCommand.isPending.value" :disabled="!formValid">创建项目</Button>
          </div>
        </Form>
      </template>
    </Drawer>
  </div>
</template>
