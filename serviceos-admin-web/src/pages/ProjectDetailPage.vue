<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Alert,
  Button,
  Descriptions,
  Input,
  Space,
  Tabs,
  TabPane,
  Tag,
  Table,
} from 'ant-design-vue'
import { ArrowLeftOutlined } from '@ant-design/icons-vue'
import {
  getAuthorizedProject,
  listAuthorizedProjectScopeRevisions,
  reviseProjectScopeRelations,
  type ProjectDetail,
  type ProjectScopeRelationRevisionPage,
} from '../api/projectDetail'
import {
  listProjectFulfillmentProfiles,
  type ProjectFulfillmentProfileSummary,
} from '../api/fulfillmentProfiles'
import { listServiceNetworks, type ServiceNetwork } from '../api/networks'
import { recordRecentVisit } from '../recent/recordRecentVisit'
import DetailPageLayout from '../patterns/templates/DetailPageLayout.vue'
import DedicatedFlowLayout from '../patterns/templates/DedicatedFlowLayout.vue'
import SemanticStatusTag from '../components/business/SemanticStatusTag.vue'
import { presentEmptyValue } from '../presentation/empty-value.presenter'
import { formatDateTimeDisplay } from '../presentation/date-time.presenter'
import { presentEntityName } from '../presentation/entity-name.presenter'
import { useDeveloperDiagnostics } from '../composables/useDeveloperDiagnostics'
import { statusLabel } from '../product/statusLabels'
import { toUserFacingError } from '../product/errorMessages'
import ProjectRegionPicker from '../components/ProjectRegionPicker.vue'
import NetworkEntityPicker from '../components/NetworkEntityPicker.vue'
import {
  followProject,
  getFollowedProjectStatus,
  unfollowProject,
} from '../api/followedProjects'

const route = useRoute()
const router = useRouter()
const diagnostics = useDeveloperDiagnostics()
const projectId = computed(() => String(route.params.id ?? ''))

const loading = ref(false)
const busy = ref(false)
const error = ref<string | null>(null)
const errorCode = ref<string | null>(null)
const message = ref<string | null>(null)
const detail = ref<ProjectDetail | null>(null)
const revisions = ref<ProjectScopeRelationRevisionPage | null>(null)
const revisionCursor = ref<string | undefined>()
const networks = ref<ServiceNetwork[]>([])
const selectedRegionCodes = ref<string[]>([])
const selectedNetworkIds = ref<string[]>([])
const reason = ref('运营调整服务范围')
const flowOpen = ref(false)
const activeTab = ref('basic')
const showDevTools = import.meta.env.DEV

const baselineRegionCodes = ref<string[]>([])
const baselineNetworkIds = ref<string[]>([])
const fulfillmentProfiles = ref<ProjectFulfillmentProfileSummary[]>([])
const followed = ref(false)
const followBusy = ref(false)

const fulfillmentPublishedCount = computed(
  () => fulfillmentProfiles.value.filter((p) => p.status === 'ACTIVE').length,
)
const fulfillmentDraftCount = computed(
  () => fulfillmentProfiles.value.filter((p) => p.status === 'DRAFT').length,
)
const latestFulfillmentVersion = computed(() => {
  const versions = fulfillmentProfiles.value
    .map((p) => p.activeVersion)
    .filter((v): v is string => !!v)
  return versions[0] ?? '—'
})

async function loadNetworks() {
  try {
    const page = await listServiceNetworks()
    networks.value = page.items
  } catch (err) {
    networks.value = []
    diagnostics.pushDiagnostic({
      title: '网点目录加载失败',
      fields: {
        message: err instanceof Error ? err.message : 'unknown',
      },
    })
  }
}

async function loadDetail() {
  loading.value = true
  error.value = null
  errorCode.value = null
  try {
    detail.value = await getAuthorizedProject(projectId.value)
    try {
      fulfillmentProfiles.value = await listProjectFulfillmentProfiles(projectId.value)
    } catch {
      fulfillmentProfiles.value = []
    }
    await loadRevisions()
    const latest = revisions.value?.items?.[0]
    const regions = latest?.regionCodes ?? detail.value.project.regionCodes ?? []
    const nets = latest?.networkIds ?? detail.value.project.networkIds ?? []
    selectedRegionCodes.value = [...regions]
    selectedNetworkIds.value = [...nets]
    baselineRegionCodes.value = [...regions]
    baselineNetworkIds.value = [...nets]
    recordRecentVisit({
      resourceType: 'PROJECT',
      resourceId: projectId.value,
      pageId: 'ADMIN.PROJECT.DETAIL',
      displayRef: detail.value.project.name || detail.value.project.code,
    })
    try {
      const status = await getFollowedProjectStatus(projectId.value)
      followed.value = status.followed
    } catch {
      followed.value = false
    }
    diagnostics.pushDiagnostic({
      title: '项目详情技术上下文',
      fields: {
        projectId: projectId.value,
        version: detail.value.project.version,
        asOf: detail.value.asOf,
        clientId: detail.value.project.clientId,
      },
    })
  } catch (err) {
    const facing = toUserFacingError(err)
    error.value = facing.message
    errorCode.value = facing.errorCode
    detail.value = null
    revisions.value = null
  } finally {
    loading.value = false
  }
}

async function loadRevisions(next?: string) {
  const page = await listAuthorizedProjectScopeRevisions(projectId.value, {
    cursor: next,
    limit: '20',
  })
  revisions.value = page
  revisionCursor.value = page.nextCursor ?? undefined
}

async function toggleFollow() {
  if (!detail.value) {
    return
  }
  followBusy.value = true
  error.value = null
  errorCode.value = null
  message.value = null
  try {
    if (followed.value) {
      await unfollowProject(projectId.value)
      followed.value = false
      message.value = '已取消关注'
    } else {
      await followProject({
        projectId: projectId.value,
        displayRef: detail.value.project.name || detail.value.project.code,
      })
      followed.value = true
      message.value = '已关注项目，可在运营工作台查看'
    }
  } catch (err) {
    const facing = toUserFacingError(err)
    error.value = facing.message
    errorCode.value = facing.errorCode
  } finally {
    followBusy.value = false
  }
}

const addedRegions = computed(() =>
  selectedRegionCodes.value.filter((c) => !baselineRegionCodes.value.includes(c)),
)
const removedRegions = computed(() =>
  baselineRegionCodes.value.filter((c) => !selectedRegionCodes.value.includes(c)),
)
const addedNetworks = computed(() =>
  selectedNetworkIds.value.filter((id) => !baselineNetworkIds.value.includes(id)),
)
const removedNetworks = computed(() =>
  baselineNetworkIds.value.filter((id) => !selectedNetworkIds.value.includes(id)),
)

const hasScopeChanges = computed(
  () =>
    addedRegions.value.length +
      removedRegions.value.length +
      addedNetworks.value.length +
      removedNetworks.value.length >
    0,
)

const networkOptions = computed(() =>
  networks.value.map((n) => ({
    value: n.id,
    label: `${n.networkName} · ${n.networkCode} · ${statusLabel(n.status)}`,
  })),
)

const statusPresentation = computed(() => {
  const status = detail.value?.project.status
  if (status === 'ACTIVE') {
    return { label: statusLabel(status), semantic: 'info' as const, icon: 'info' as const }
  }
  if (status === 'CLOSED') {
    return { label: statusLabel(status), semantic: 'neutral' as const, icon: 'check' as const }
  }
  if (status === 'SUSPENDED') {
    return { label: statusLabel(status), semantic: 'warning' as const, icon: 'warning' as const }
  }
  return { label: statusLabel(status), semantic: 'warning' as const, icon: 'clock' as const }
})

const revisionRows = computed(() =>
  (revisions.value?.items ?? []).map((item) => ({
    key: item.revisionId,
    revisedAt: formatDateTimeDisplay(item.revisedAt),
    reason: item.reason,
    regions: item.regionCodes.length ? item.regionCodes.join('、') : presentEmptyValue('no_records'),
    networks: item.networkIds.length
      ? item.networkIds
          .map((id) => presentEntityName({ id, loaded: true }).label)
          .join('、')
      : presentEmptyValue('no_records'),
  })),
)

function openImpactFlow() {
  if (!hasScopeChanges.value) {
    message.value = '未检测到服务范围或网点变更'
    return
  }
  flowOpen.value = true
}

async function confirmReviseScope() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    const aggregateVersion =
      revisions.value?.items?.[0]?.aggregateVersion ?? detail.value?.project.version
    if (!aggregateVersion) throw new Error('缺少版本信息，请刷新后重试')
    const result = await reviseProjectScopeRelations(projectId.value, aggregateVersion, {
      regionCodes: selectedRegionCodes.value,
      networkIds: selectedNetworkIds.value,
      reason: reason.value.trim() || '运营调整服务范围',
    })
    message.value = `服务范围已保存（版本 ${result.data.aggregateVersion}）`
    flowOpen.value = false
    await loadDetail()
  } catch (err) {
    const facing = toUserFacingError(err)
    error.value = facing.message
    errorCode.value = facing.errorCode
  } finally {
    busy.value = false
  }
}

watch(projectId, () => {
  if (projectId.value) void loadDetail()
})
onMounted(() => {
  void loadNetworks()
  if (projectId.value) void loadDetail()
})
</script>

<template>
  <DedicatedFlowLayout
    v-if="flowOpen && detail"
    title="确认服务范围调整"
    description="请确认新增/移除的区域与网点影响后再保存。此操作不可用普通确认框替代。"
    step-label="步骤 2 / 2：影响确认"
    sticky-note="保存将调用服务端范围修订命令，并携带版本条件。"
  >
    <template #back>
      <Button type="text" @click="flowOpen = false">返回编辑</Button>
    </template>
    <template #feedback>
      <Alert v-if="error" type="error" show-icon :message="error" :description="errorCode ? `问题编号：${errorCode}` : undefined" />
    </template>
    <Descriptions bordered :column="1" size="small">
      <Descriptions.Item label="新增区域">
        {{ addedRegions.length ? addedRegions.join('、') : presentEmptyValue('no_records') }}
      </Descriptions.Item>
      <Descriptions.Item label="移除区域">
        {{ removedRegions.length ? removedRegions.join('、') : presentEmptyValue('no_records') }}
      </Descriptions.Item>
      <Descriptions.Item label="新增网点">
        {{
          addedNetworks.length
            ? addedNetworks.map((id) => presentEntityName({ id, loaded: true }).label).join('、')
            : presentEmptyValue('no_records')
        }}
      </Descriptions.Item>
      <Descriptions.Item label="移除网点">
        {{
          removedNetworks.length
            ? removedNetworks.map((id) => presentEntityName({ id, loaded: true }).label).join('、')
            : presentEmptyValue('no_records')
        }}
      </Descriptions.Item>
      <Descriptions.Item label="现有工单影响">
        服务端未返回影响试算；保存后请在工单中心核对相关项目工单（UI_DATA_GAP）。
      </Descriptions.Item>
      <Descriptions.Item label="调整原因">
        <Input v-model:value="reason" aria-label="服务范围调整原因" />
      </Descriptions.Item>
    </Descriptions>
    <template #sticky-secondary>
      <Button @click="flowOpen = false">取消</Button>
    </template>
    <template #sticky-actions>
      <Button type="primary" :loading="busy" @click="confirmReviseScope">确认保存服务范围调整</Button>
    </template>
  </DedicatedFlowLayout>

  <DetailPageLayout
    v-else
    title="项目详情"
    :eyebrow="detail ? `项目编码 ${detail.project.code}` : undefined"
  >
    <template #back>
      <Button type="text" aria-label="返回项目目录" @click="router.push({ name: 'ADMIN.PROJECT.LIST' })">
        <template #icon><ArrowLeftOutlined /></template>
        返回项目目录
      </Button>
    </template>
    <template #status>
      <SemanticStatusTag v-if="detail" :presentation="statusPresentation" />
    </template>
    <template #secondary-actions>
      <Button :loading="loading" @click="loadDetail">刷新</Button>
      <Button
        data-testid="project-follow-toggle"
        :loading="followBusy"
        :type="followed ? 'default' : 'primary'"
        ghost
        @click="toggleFollow"
      >
        {{ followed ? '取消关注' : '关注项目' }}
      </Button>
      <Button v-if="showDevTools" type="link" @click="diagnostics.openDrawer()">技术诊断</Button>
    </template>
    <template #primary-action>
      <Button type="primary" :disabled="!detail || !hasScopeChanges" @click="openImpactFlow">
        保存服务范围调整
      </Button>
    </template>
    <template #feedback>
      <Alert v-if="error" type="error" show-icon :message="error" :description="errorCode ? `问题编号：${errorCode}` : undefined" />
      <Alert v-else-if="loading && !detail" type="info" show-icon message="正在加载项目…" />
      <Alert v-if="message" type="success" show-icon :message="message" />
    </template>

    <template v-if="detail" #summary>
      <Descriptions :column="3" size="small">
        <Descriptions.Item label="项目名称">{{ detail.project.name }}</Descriptions.Item>
        <Descriptions.Item label="项目编码">{{ detail.project.code }}</Descriptions.Item>
        <Descriptions.Item label="所属车企">
          {{ presentEntityName({ id: detail.project.clientId, loaded: true }).label }}
        </Descriptions.Item>
        <Descriptions.Item label="生效日期">{{ detail.project.startsOn || presentEmptyValue('not_provided') }}</Descriptions.Item>
        <Descriptions.Item label="失效日期">{{ detail.project.endsOn || presentEmptyValue('not_provided') }}</Descriptions.Item>
        <Descriptions.Item label="服务区域数量">{{ selectedRegionCodes.length }}</Descriptions.Item>
        <Descriptions.Item label="合作网点数量">{{ selectedNetworkIds.length }}</Descriptions.Item>
        <Descriptions.Item label="支持工单类型">{{ fulfillmentProfiles.length }}</Descriptions.Item>
        <Descriptions.Item label="已发布方案">{{ fulfillmentPublishedCount }}</Descriptions.Item>
        <Descriptions.Item label="草稿方案">{{ fulfillmentDraftCount }}</Descriptions.Item>
        <Descriptions.Item label="最近发布版本">{{ latestFulfillmentVersion }}</Descriptions.Item>
        <Descriptions.Item label="当前版本">{{ detail.project.version }}</Descriptions.Item>
        <Descriptions.Item label="最近更新时间">{{ formatDateTimeDisplay(detail.asOf) }}</Descriptions.Item>
      </Descriptions>
    </template>

    <Tabs v-if="detail" v-model:activeKey="activeTab">
      <TabPane key="basic" tab="项目概览">
        <Descriptions bordered :column="2" size="small">
          <Descriptions.Item label="项目名称">{{ detail.project.name }}</Descriptions.Item>
          <Descriptions.Item label="项目编码">{{ detail.project.code }}</Descriptions.Item>
          <Descriptions.Item label="状态">
            <SemanticStatusTag :presentation="statusPresentation" />
          </Descriptions.Item>
          <Descriptions.Item label="所属车企">
            {{ presentEntityName({ id: detail.project.clientId, loaded: true }).label }}
          </Descriptions.Item>
        </Descriptions>
      </TabPane>

      <TabPane key="regions" tab="服务范围">
        <Alert
          type="info"
          show-icon
          message="区域选项来自已授权项目生效 REGION"
          description="完整行政区名称树仍属 UI_DATA_GAP；可从选项选择或继续输入编码。"
          style="margin-bottom: 12px"
        />
        <label class="field">
          <span>服务区域</span>
          <ProjectRegionPicker v-model="selectedRegionCodes" />
        </label>
        <p class="muted">已选 {{ selectedRegionCodes.length }} 个区域</p>
      </TabPane>

      <TabPane key="networks" tab="合作网点">
        <label class="field">
          <span>合作网点</span>
          <NetworkEntityPicker v-model="selectedNetworkIds" />
        </label>
        <p class="muted">已选 {{ selectedNetworkIds.length }} 个网点</p>
        <Space wrap>
          <Tag
            v-for="id in selectedNetworkIds"
            :key="id"
            closable
            @close="selectedNetworkIds = selectedNetworkIds.filter((x) => x !== id)"
          >
            {{
              networkOptions.find((option) => option.value === id)?.label ||
              presentEntityName({ id, loaded: true }).label
            }}
          </Tag>
        </Space>
      </TabPane>

      <TabPane key="fulfillment" tab="工单类型与履约配置">
        <Alert
          type="info"
          show-icon
          message="进入项目履约配置中心"
          description="按工单类型维护流程、表单、资料、动作、审核与 SLA；发布使用服务端运行说明书与真实差异分析。"
          style="margin-bottom: 12px"
        />
        <p class="muted">
          已配置 {{ fulfillmentProfiles.length }} 种工单类型；已发布
          {{ fulfillmentPublishedCount }}，草稿 {{ fulfillmentDraftCount }}。
        </p>
        <Button
          type="primary"
          @click="
            router.push({
              name: 'ADMIN.PROJECT.FULFILLMENT.LIST',
              params: { id: projectId },
            })
          "
        >
          打开项目履约配置中心
        </Button>
      </TabPane>

      <TabPane key="history" tab="变更记录">
        <Table
          size="middle"
          row-key="key"
          :pagination="false"
          :columns="[
            { title: '调整时间', dataIndex: 'revisedAt' },
            { title: '原因', dataIndex: 'reason' },
            { title: '服务区域', dataIndex: 'regions' },
            { title: '合作网点', dataIndex: 'networks' },
          ]"
          :data-source="revisionRows"
        />
        <Button
          v-if="revisionCursor"
          style="margin-top: 12px"
          @click="loadRevisions(revisionCursor)"
        >
          加载更多
        </Button>
      </TabPane>
    </Tabs>
  </DetailPageLayout>
</template>

<style scoped>
.field {
  display: grid;
  gap: 6px;
  margin-bottom: 12px;
  font-size: 13px;
}
.muted {
  color: var(--sos-color-text-tertiary, #7b8494);
  font-size: 13px;
}
</style>
