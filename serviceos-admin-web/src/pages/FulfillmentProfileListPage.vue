<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Alert, Button, Empty, Space, Table, Tag } from 'ant-design-vue'
import { ArrowLeftOutlined, PlusOutlined } from '@ant-design/icons-vue'
import ConfigurationWorkspaceLayout from '../patterns/templates/ConfigurationWorkspaceLayout.vue'
import type { ConfigurationNavItem } from '../patterns/ConfigurationSubNav.vue'
import type { SummaryStripItem } from '../patterns/SummaryStrip.vue'
import SemanticStatusTag from '../components/business/SemanticStatusTag.vue'
import FulfillmentCompareImpactPanel from '../components/fulfillment/FulfillmentCompareImpactPanel.vue'
import {
  compareProjectFulfillmentImpact,
  getProjectFulfillmentUsageSummary,
  listProjectFulfillmentProfiles,
  type ProjectFulfillmentCompareImpact,
  type ProjectFulfillmentProfileSummary,
  type ProjectFulfillmentUsageSummary,
} from '../api/fulfillmentProfiles'
import { getAuthorizedProject, type ProjectDetail } from '../api/projectDetail'
import { formatDateTimeDisplay } from '../presentation/date-time.presenter'
import { labelServiceProduct } from '../presentation/enum-labels'
import { toUserFacingError } from '../product/errorMessages'
import { statusLabel } from '../product/statusLabels'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => String(route.params.id ?? ''))

const loading = ref(false)
const railLoading = ref(false)
const error = ref<string | null>(null)
const railError = ref<string | null>(null)
const project = ref<ProjectDetail | null>(null)
const items = ref<ProjectFulfillmentProfileSummary[]>([])
const usageSummary = ref<ProjectFulfillmentUsageSummary | null>(null)
const activeNavKey = ref('work-order-types')
const compareImpact = ref<ProjectFulfillmentCompareImpact | null>(null)
const selectedProfileId = ref<string | null>(null)

const versionTimeline = computed(() =>
  items.value
    .filter((item) => !!item.activeVersion || item.status === 'DRAFT')
    .map((item) => ({
      key: item.profileId,
      title: `${labelServiceProduct(item.serviceProductCode)} · ${item.profileName}`,
      versionLabel: item.activeVersion ? `已发布 ${item.activeVersion}` : '仅草稿',
      effectiveFromLabel: item.effectiveFrom
        ? formatDateTimeDisplay(item.effectiveFrom)
        : '尚未生效',
      status: item.status,
    })),
)

const navItems = computed<ConfigurationNavItem[]>(() => [
  { key: 'basics', label: '基础信息' },
  { key: 'work-order-types', label: '工单类型', badge: String(items.value.length) },
  { key: 'workflow', label: '工作流', badge: '后续' },
  { key: 'task-templates', label: '任务模板' },
  { key: 'forms', label: '表单模板', badge: '后续' },
  { key: 'evidence', label: '资料要求', badge: '后续' },
  { key: 'sla', label: 'SLA 规则', badge: '后续' },
  { key: 'dispatch', label: '派单策略', badge: '后续' },
  { key: 'notification', label: '通知策略', badge: '后续' },
  { key: 'integration', label: '集成映射', badge: '后续' },
  { key: 'publish-history', label: '发布记录' },
])

const summaryItems = computed<SummaryStripItem[]>(() => {
  const active = items.value.filter((item) => item.status === 'ACTIVE').length
  const drafts = items.value.filter((item) => item.status === 'DRAFT').length
  const latestPublished = items.value
    .map((item) => item.effectiveFrom)
    .filter((value): value is Date => !!value)
    .sort((a, b) => b.getTime() - a.getTime())[0]
  return [
    {
      key: 'project',
      label: '当前项目',
      value: project.value?.project.name || '—',
    },
    {
      key: 'published',
      label: '已发布方案',
      value: String(active),
      tone: active > 0 ? 'success' : 'default',
    },
    {
      key: 'draft',
      label: '草稿方案',
      value: String(drafts),
      tone: drafts > 0 ? 'warning' : 'default',
    },
    {
      key: 'types',
      label: '工单类型数',
      value: String(items.value.length),
    },
    {
      key: 'latest',
      label: '最近生效时间',
      value: latestPublished ? formatDateTimeDisplay(latestPublished) : '尚未发布',
    },
    {
      key: 'in-use',
      label: '使用中工单',
      value: formatActiveWorkOrderCount(usageSummary.value),
      hint: activeWorkOrderHint(usageSummary.value),
    },
  ]
})

function formatActiveWorkOrderCount(summary: ProjectFulfillmentUsageSummary | null): string {
  if (!summary || summary.activeWorkOrderCount == null) {
    return '不可用'
  }
  const base = String(summary.activeWorkOrderCount)
  return summary.activeWorkOrderCountTruncated ? `${base}+` : base
}

function activeWorkOrderHint(summary: ProjectFulfillmentUsageSummary | null): string | undefined {
  if (!summary || summary.activeWorkOrderCount == null) {
    return '缺少 workOrder.read，计数已省略'
  }
  if (summary.activeWorkOrderCountTruncated) {
    return '超过 100 仅显示上限'
  }
  return undefined
}

const columns = [
  { title: '工单类型', dataIndex: 'serviceProductLabel', key: 'serviceProduct' },
  { title: '履约方案', dataIndex: 'profileName', key: 'profileName' },
  { title: '流程摘要', dataIndex: 'workflowSummary', key: 'workflowSummary' },
  { title: '表单', dataIndex: 'formCount', key: 'formCount', width: 80 },
  { title: '资料', dataIndex: 'evidenceCount', key: 'evidenceCount', width: 80 },
  { title: 'SLA', dataIndex: 'slaSummary', key: 'slaSummary', width: 120 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
  { title: '当前版本', dataIndex: 'activeVersion', key: 'activeVersion', width: 100 },
  { title: '更新时间', dataIndex: 'updatedAtLabel', key: 'updatedAt', width: 180 },
  { title: '操作', key: 'actions', width: 220 },
]

const rows = computed(() =>
  items.value.map((item) => ({
    ...item,
    serviceProductLabel: labelServiceProduct(item.serviceProductCode),
    workflowSummary: item.workflowSummary || `${item.stageCount ?? 0} 个阶段`,
    slaSummary: item.slaSummary || '未摘要',
    updatedAtLabel: item.updatedAt ? formatDateTimeDisplay(item.updatedAt) : '—',
  })),
)

const publishedRows = computed(() =>
  items.value.filter((item) => !!item.activeVersion),
)

async function loadCompare(profileId: string) {
  railLoading.value = true
  railError.value = null
  selectedProfileId.value = profileId
  try {
    compareImpact.value = await compareProjectFulfillmentImpact(projectId.value, profileId)
  } catch (err) {
    compareImpact.value = null
    railError.value = toUserFacingError(err).message
  } finally {
    railLoading.value = false
  }
}

async function load() {
  if (!projectId.value) return
  loading.value = true
  error.value = null
  try {
    project.value = await getAuthorizedProject(projectId.value)
    const [profiles, usage] = await Promise.all([
      listProjectFulfillmentProfiles(projectId.value),
      getProjectFulfillmentUsageSummary(projectId.value).catch(() => null),
    ])
    items.value = profiles
    usageSummary.value = usage
    const first = items.value[0]
    if (first) {
      await loadCompare(first.profileId)
    } else {
      compareImpact.value = null
      selectedProfileId.value = null
    }
  } catch (err) {
    error.value = toUserFacingError(err).message
    items.value = []
    usageSummary.value = null
  } finally {
    loading.value = false
  }
}

function openCreate() {
  router.push({
    name: 'ADMIN.PROJECT.FULFILLMENT.CREATE',
    params: { id: projectId.value },
  })
}

function openDetail(profileId: string) {
  router.push({
    name: 'ADMIN.PROJECT.FULFILLMENT.DETAIL',
    params: { id: projectId.value, profileId },
  })
}

function openPreview(profileId: string) {
  router.push({
    name: 'ADMIN.PROJECT.FULFILLMENT.PREVIEW',
    params: { id: projectId.value, profileId },
  })
}

function statusPresentation(status: string) {
  const semantic =
    status === 'ACTIVE' ? 'success' : status === 'SUSPENDED' ? 'warning' : 'neutral'
  return {
    semantic,
    label: statusLabel(status),
    icon: semantic === 'success' ? 'check' : semantic === 'warning' ? 'warning' : 'info',
  } as const
}

function onNavSelect(key: string) {
  if (key === 'workflow') {
    router.push({ name: 'ADMIN.WORKFLOW.DESIGNER' })
    return
  }
  if (key === 'task-templates') {
    router.push({ name: 'ADMIN.TASK_TEMPLATE.CENTER' })
    return
  }
  activeNavKey.value = key
}

watch(projectId, () => load())
onMounted(load)
</script>

<template>
  <ConfigurationWorkspaceLayout
    title="项目履约配置中心"
    description="在单一工作区中管理项目工单类型、草稿、已发布版本、影响范围与运行说明入口。"
    :summary-items="summaryItems"
    :nav-items="navItems"
    :active-nav-key="activeNavKey"
    right-title="配置说明 / 影响分析"
    :loading="loading"
    @nav-select="onNavSelect"
  >
    <template #breadcrumb>
      <a @click.prevent="router.push({ name: 'ADMIN.PROJECT.DETAIL', params: { id: projectId } })">
        项目详情
      </a>
      <span> / 履约配置中心</span>
    </template>
    <template #secondary-actions>
      <Button @click="router.push({ name: 'ADMIN.PROJECT.DETAIL', params: { id: projectId } })">
        <template #icon><ArrowLeftOutlined /></template>
        返回项目
      </Button>
    </template>
    <template #primary-action>
      <Button type="primary" @click="openCreate">
        <template #icon><PlusOutlined /></template>
        新增工单类型
      </Button>
    </template>
    <template #feedback>
      <Alert v-if="error" type="error" show-icon :message="error" style="margin-bottom: 12px" />
    </template>

    <section v-if="activeNavKey === 'basics'">
      <h2>配置包概览</h2>
      <p>项目：{{ project?.project.name || '—' }}</p>
      <p>项目编码：{{ project?.project.code || '—' }}</p>
      <p>已配置工单类型 {{ items.length }} 种；其中已发布 {{ summaryItems[1]?.value }} 种。</p>
      <Alert
        type="info"
        show-icon
        message="发布新版本不会自动迁移存量工单"
        description="新工单在生效时间后使用新版本；历史工单继续使用创建时冻结的配置。"
      />
    </section>

    <section v-else-if="activeNavKey === 'work-order-types'">
      <div class="section-header">
        <h2>工单类型配置</h2>
        <Button type="link" @click="openCreate">新增工单类型</Button>
      </div>
      <Table
        v-if="rows.length"
        size="middle"
        row-key="profileId"
        :loading="loading"
        :pagination="false"
        :columns="columns"
        :data-source="rows"
        :custom-row="(record) => ({ onClick: () => loadCompare(record.profileId) })"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <SemanticStatusTag :presentation="statusPresentation(record.status)" />
          </template>
          <template v-else-if="column.key === 'actions'">
            <Space wrap>
              <Button type="link" @click.stop="openDetail(record.profileId)">查看</Button>
              <Button type="link" @click.stop="openPreview(record.profileId)">运行说明</Button>
              <Tag v-if="selectedProfileId === record.profileId">已选中</Tag>
            </Space>
          </template>
        </template>
      </Table>
      <Empty
        v-else-if="!loading"
        description="当前项目还没有工单类型配置。创建后即可定义流程、表单、资料要求和 SLA。"
      >
        <Button type="primary" @click="openCreate">新增工单类型</Button>
      </Empty>
    </section>

    <section v-else-if="activeNavKey === 'publish-history'">
      <h2>发布记录</h2>
      <Table
        v-if="publishedRows.length"
        size="small"
        row-key="profileId"
        :pagination="false"
        :data-source="publishedRows.map((item) => ({
          ...item,
          serviceProductLabel: labelServiceProduct(item.serviceProductCode),
          effectiveFromLabel: item.effectiveFrom
            ? formatDateTimeDisplay(item.effectiveFrom)
            : '—',
        }))"
        :columns="[
          { title: '工单类型', dataIndex: 'serviceProductLabel' },
          { title: '方案', dataIndex: 'profileName' },
          { title: '版本', dataIndex: 'activeVersion' },
          { title: '生效时间', dataIndex: 'effectiveFromLabel' },
        ]"
      />
      <Empty v-else description="还没有已发布版本" />
    </section>

    <section v-else class="placeholder">
      <h2>{{ navItems.find((item) => item.key === activeNavKey)?.label }}</h2>
      <Alert
        type="warning"
        show-icon
        message="本切片尚未实现该配置分区的产品编辑器"
        description="入口已按母版预留。请从工单类型进入详情/编辑，或等待工作流设计器与任务模板中心切片。"
      />
    </section>

    <template #rail>
      <Alert
        v-if="railError"
        type="error"
        show-icon
        :message="railError"
        style="margin-bottom: 12px"
      />
      <FulfillmentCompareImpactPanel :impact="compareImpact" :loading="railLoading" />
      <section class="timeline" data-testid="fulfillment-version-timeline">
        <h3>版本时间线</h3>
        <ul v-if="versionTimeline.length">
          <li v-for="entry in versionTimeline" :key="entry.key">
            <strong>{{ entry.title }}</strong>
            <div>{{ entry.versionLabel }}</div>
            <div class="muted">{{ entry.effectiveFromLabel }}</div>
          </li>
        </ul>
        <p v-else class="muted">暂无版本记录</p>
      </section>
      <p class="rail-hint">
        点击左侧工单类型行可刷新该方案相对当前发布版的真实差异。使用中工单数为项目 ACTIVE 工单摘要。
      </p>
    </template>
  </ConfigurationWorkspaceLayout>
</template>

<style scoped>
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
h2 {
  margin: 0 0 12px;
  font-size: 16px;
}
.placeholder {
  color: var(--sos-color-text-secondary);
}
.rail-hint {
  margin: 0;
  font-size: 12px;
  color: var(--sos-color-text-tertiary);
}
.timeline h3 {
  margin: 0 0 8px;
  font-size: 14px;
  color: var(--sos-color-text-primary);
}
.timeline ul {
  margin: 0;
  padding-left: 18px;
  color: var(--sos-color-text-secondary);
}
.timeline li + li {
  margin-top: 10px;
}
.muted {
  color: var(--sos-color-text-tertiary);
  font-size: 12px;
}
</style>
