<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Alert, Button, Descriptions, Space, Table, Tabs, TabPane } from 'ant-design-vue'
import { ArrowLeftOutlined } from '@ant-design/icons-vue'
import DetailPageLayout from '../patterns/templates/DetailPageLayout.vue'
import SemanticStatusTag from '../components/business/SemanticStatusTag.vue'
import {
  getProjectFulfillmentDraft,
  getProjectFulfillmentProfile,
  listProjectFulfillmentRevisions,
  type ProjectFulfillmentDraft,
  type ProjectFulfillmentProfileDetail,
  type ProjectFulfillmentRevision,
} from '../api/fulfillmentProfiles'
import { formatDateTimeDisplay } from '../presentation/date-time.presenter'
import { labelServiceProduct } from '../presentation/enum-labels'
import { toUserFacingError } from '../product/errorMessages'
import { statusLabel } from '../product/statusLabels'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => String(route.params.id ?? ''))
const profileId = computed(() => String(route.params.profileId ?? ''))

const loading = ref(false)
const error = ref<string | null>(null)
const detail = ref<ProjectFulfillmentProfileDetail | null>(null)
const draft = ref<ProjectFulfillmentDraft | null>(null)
const revisions = ref<ProjectFulfillmentRevision[]>([])
const activeTab = ref('overview')

const allowedActionSet = computed(() => new Set(detail.value?.allowedActions ?? []))
const canEdit = computed(() => allowedActionSet.value.has('EDIT_DRAFT'))
const canPreview = computed(() => allowedActionSet.value.has('COMPILE_PREVIEW'))
const canPublish = computed(() => allowedActionSet.value.has('PUBLISH'))
const isReadOnly = computed(
  () => !!detail.value && !canEdit.value && !canPreview.value && !canPublish.value,
)

type StageRow = {
  stageCode: string
  stageName: string
  sequence: number
  ownerLabel: string
  formCount: number
  evidenceCount: number
  actionCount: number
  slaLabel: string
}

function ownerLabel(ownerType: unknown): string {
  const normalized = String(ownerType ?? '')
  if (normalized === 'PLATFORM') return '平台运营'
  if (normalized === 'NETWORK') return '合作网点'
  if (normalized === 'TECHNICIAN') return '服务师傅'
  return '责任方待配置'
}

const stageRows = computed<StageRow[]>(() => {
  if (!draft.value?.documentJson) return []
  try {
    const doc = JSON.parse(draft.value.documentJson) as {
      stages?: Array<Record<string, unknown>>
    }
    return (doc.stages ?? []).map((stage) => ({
      stageCode: String(stage.stageCode ?? ''),
      stageName: String(stage.stageName ?? ''),
      sequence: Number(stage.sequence ?? 0),
      ownerLabel: ownerLabel(stage.ownerType),
      formCount: Array.isArray(stage.formRefs) ? stage.formRefs.length : 0,
      evidenceCount: Array.isArray(stage.evidenceRefs) ? stage.evidenceRefs.length : 0,
      actionCount: Array.isArray(stage.actions) ? stage.actions.length : 0,
      slaLabel: stage.slaRef ? '已绑定' : '未绑定',
    }))
  } catch {
    return []
  }
})

async function load() {
  if (!projectId.value || !profileId.value) return
  loading.value = true
  error.value = null
  try {
    const result = await getProjectFulfillmentProfile(projectId.value, profileId.value)
    detail.value = result.data
    draft.value = (
      await getProjectFulfillmentDraft(projectId.value, profileId.value)
    ).data
    revisions.value = await listProjectFulfillmentRevisions(projectId.value, profileId.value)
  } catch (err) {
    error.value = toUserFacingError(err).message
  } finally {
    loading.value = false
  }
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

watch([projectId, profileId], () => load())
onMounted(load)
</script>

<template>
  <DetailPageLayout
    :title="detail?.profileName || '履约配置详情'"
    description="查看该工单类型的阶段编排、发布版本与运行说明入口。"
    :loading="loading"
  >
    <template #secondary-actions>
      <Button
        @click="router.push({ name: 'ADMIN.PROJECT.FULFILLMENT.LIST', params: { id: projectId } })"
      >
        <template #icon><ArrowLeftOutlined /></template>
        返回列表
      </Button>
    </template>
    <template #primary-action>
      <Space v-if="canEdit || canPreview || canPublish">
        <Button
          v-if="canEdit"
          @click="
            router.push({
              name: 'ADMIN.PROJECT.FULFILLMENT.EDIT',
              params: { id: projectId, profileId },
            })
          "
        >
          编辑草稿
        </Button>
        <Button
          v-if="canPreview"
          @click="
            router.push({
              name: 'ADMIN.PROJECT.FULFILLMENT.PREVIEW',
              params: { id: projectId, profileId },
            })
          "
        >
          运行预览
        </Button>
        <Button
          v-if="canPublish"
          type="primary"
          @click="
            router.push({
              name: 'ADMIN.PROJECT.FULFILLMENT.PUBLISH',
              params: { id: projectId, profileId },
            })
          "
        >
          发布
        </Button>
      </Space>
    </template>
    <template #feedback>
      <Alert v-if="error" type="error" show-icon :message="error" style="margin-bottom: 12px" />
      <Alert
        v-else-if="isReadOnly"
        type="info"
        show-icon
        message="当前配置为只读"
        description="当前状态或权限不允许编辑、预览或发布。可继续查看阶段和历史发布版本。"
        style="margin-bottom: 12px"
      />
    </template>

    <template v-if="detail">
      <Descriptions bordered :column="2" size="small" style="margin-bottom: 16px">
        <Descriptions.Item label="工单类型">
          {{ labelServiceProduct(detail.serviceProductCode) }}
        </Descriptions.Item>
        <Descriptions.Item label="配置状态">
          <SemanticStatusTag :presentation="statusPresentation(detail.status)" />
        </Descriptions.Item>
        <Descriptions.Item label="当前发布版本">
          {{ detail.activeVersion || '尚未发布' }}
        </Descriptions.Item>
        <Descriptions.Item label="生效时间">
          {{
            detail.activeEffectiveFrom
              ? formatDateTimeDisplay(detail.activeEffectiveFrom)
              : '—'
          }}
        </Descriptions.Item>
        <Descriptions.Item label="业务说明" :span="2">
          {{ detail.description || '—' }}
        </Descriptions.Item>
      </Descriptions>

      <Tabs v-model:activeKey="activeTab">
        <TabPane key="overview" tab="阶段总览">
          <Table
            size="middle"
            row-key="stageCode"
            :pagination="false"
            :data-source="stageRows"
            :columns="[
              { title: '顺序', dataIndex: 'sequence', width: 80 },
              { title: '阶段', dataIndex: 'stageName' },
              { title: '责任方', dataIndex: 'ownerLabel' },
              { title: '表单', dataIndex: 'formCount', width: 80 },
              { title: '资料', dataIndex: 'evidenceCount', width: 80 },
              { title: '动作', dataIndex: 'actionCount', width: 80 },
              { title: 'SLA', dataIndex: 'slaLabel' },
            ]"
          />
        </TabPane>
        <TabPane key="revisions" tab="发布版本">
          <Table
            size="middle"
            row-key="revisionId"
            :pagination="false"
            :data-source="revisions"
            :columns="[
              { title: '版本', dataIndex: 'versionNo', width: 80 },
              { title: '生效时间', dataIndex: 'effectiveFrom' },
              { title: '发布时间', dataIndex: 'publishedAt' },
              { title: '发布人', dataIndex: 'publishedBy' },
            ]"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'effectiveFrom'">
                {{
                  record.effectiveFrom ? formatDateTimeDisplay(record.effectiveFrom) : '—'
                }}
              </template>
              <template v-else-if="column.dataIndex === 'publishedAt'">
                {{ record.publishedAt ? formatDateTimeDisplay(record.publishedAt) : '—' }}
              </template>
            </template>
          </Table>
        </TabPane>
      </Tabs>
    </template>
  </DetailPageLayout>
</template>
