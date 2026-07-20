<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Alert, Button, Empty, Space, Table, Tag } from 'ant-design-vue'
import { ArrowLeftOutlined, PlusOutlined } from '@ant-design/icons-vue'
import ListPageLayout from '../patterns/templates/ListPageLayout.vue'
import SemanticStatusTag from '../components/business/SemanticStatusTag.vue'
import {
  createProjectFulfillmentProfile,
  listProjectFulfillmentProfiles,
  type ProjectFulfillmentProfileSummary,
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
const creating = ref(false)
const error = ref<string | null>(null)
const project = ref<ProjectDetail | null>(null)
const items = ref<ProjectFulfillmentProfileSummary[]>([])

const columns = [
  { title: '工单类型', dataIndex: 'serviceProductLabel', key: 'serviceProduct' },
  { title: '履约方案', dataIndex: 'profileName', key: 'profileName' },
  { title: '阶段数量', dataIndex: 'stageCount', key: 'stageCount', width: 100 },
  { title: '表单数量', dataIndex: 'formCount', key: 'formCount', width: 100 },
  { title: '资料要求', dataIndex: 'evidenceCount', key: 'evidenceCount', width: 100 },
  { title: '当前版本', dataIndex: 'activeVersion', key: 'activeVersion', width: 100 },
  { title: '配置状态', dataIndex: 'status', key: 'status', width: 120 },
  { title: '生效时间', dataIndex: 'effectiveFromLabel', key: 'effectiveFrom', width: 180 },
  { title: '操作', key: 'actions', width: 220 },
]

const rows = computed(() =>
  items.value.map((item) => ({
    ...item,
    serviceProductLabel: labelServiceProduct(item.serviceProductCode),
    effectiveFromLabel: item.effectiveFrom
      ? formatDateTimeDisplay(item.effectiveFrom)
      : '—',
  })),
)

async function load() {
  if (!projectId.value) return
  loading.value = true
  error.value = null
  try {
    project.value = await getAuthorizedProject(projectId.value)
    items.value = await listProjectFulfillmentProfiles(projectId.value)
  } catch (err) {
    error.value = toUserFacingError(err).message
    items.value = []
  } finally {
    loading.value = false
  }
}

async function createStandard() {
  creating.value = true
  error.value = null
  try {
    const created = await createProjectFulfillmentProfile(projectId.value, {
      serviceProductCode: 'HOME_CHARGING_SURVEY_INSTALL',
      profileName: '标准家充履约方案',
      description: '基于标准勘测安装模板创建',
      templateCode: 'HOME_CHARGING_SURVEY_INSTALL',
    })
    await router.push({
      name: 'ADMIN.PROJECT.FULFILLMENT.DETAIL',
      params: { id: projectId.value, profileId: created.data.profileId },
    })
  } catch (err) {
    error.value = toUserFacingError(err).message
  } finally {
    creating.value = false
  }
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

watch(projectId, () => load(), { immediate: false })
onMounted(load)
</script>

<template>
  <ListPageLayout
    title="工单类型与履约配置"
    description="配置当前项目支持的工单类型，以及每种工单的流程、表单、资料、动作、审核和 SLA。"
    :loading="loading"
    :count-label="`共 ${items.length} 种工单类型`"
  >
    <template #secondary-actions>
      <Button @click="router.push({ name: 'ADMIN.PROJECT.DETAIL', params: { id: projectId } })">
        <template #icon><ArrowLeftOutlined /></template>
        返回项目详情
      </Button>
    </template>
    <template #primary-action>
      <Button type="primary" :loading="creating" @click="createStandard">
        <template #icon><PlusOutlined /></template>
        新增工单类型
      </Button>
    </template>
    <template #feedback>
      <Alert
        v-if="project"
        type="info"
        show-icon
        :message="`当前项目：${project.project.name}`"
        style="margin-bottom: 12px"
      />
      <Alert v-if="error" type="error" show-icon :message="error" style="margin-bottom: 12px" />
    </template>

    <Table
      v-if="rows.length"
      size="middle"
      row-key="profileId"
      :loading="loading"
      :pagination="false"
      :columns="columns"
      :data-source="rows"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <SemanticStatusTag :presentation="statusPresentation(record.status)" />
        </template>
        <template v-else-if="column.key === 'actions'">
          <Space wrap>
            <Button type="link" @click="openDetail(record.profileId)">查看配置</Button>
            <Button type="link" @click="openPreview(record.profileId)">运行预览</Button>
            <Tag v-if="record.workflowSummary">{{ record.workflowSummary }}</Tag>
          </Space>
        </template>
      </template>
    </Table>
    <Empty
      v-else-if="!loading"
      description="当前项目还没有工单类型配置。创建后即可定义该类工单的流程、表单、资料要求和 SLA。"
    >
      <Button type="primary" :loading="creating" @click="createStandard">新增工单类型</Button>
    </Empty>
  </ListPageLayout>
</template>
