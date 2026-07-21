<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Alert, Button, Descriptions, Space, Table, Tabs, TabPane } from 'ant-design-vue'
import { ArrowLeftOutlined } from '@ant-design/icons-vue'
import DetailPageLayout from '../patterns/templates/DetailPageLayout.vue'
import SemanticStatusTag from '../components/business/SemanticStatusTag.vue'
import FulfillmentRunbookTable from '../components/fulfillment/FulfillmentRunbookTable.vue'
import {
  compileProjectFulfillmentPreview,
  getProjectFulfillmentProfile,
  hasAllowedAction,
  listProjectFulfillmentRevisions,
  type ProjectFulfillmentManifest,
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
const revisions = ref<ProjectFulfillmentRevision[]>([])
const manifest = ref<ProjectFulfillmentManifest | null>(null)
const activeTab = ref('overview')

const canEdit = computed(() => hasAllowedAction(detail.value, 'EDIT_DRAFT'))
const canPreview = computed(
  () =>
    hasAllowedAction(detail.value, 'COMPILE_PREVIEW') ||
    hasAllowedAction(detail.value, 'VIEW'),
)
const canPublish = computed(() => hasAllowedAction(detail.value, 'PUBLISH'))
const readonly = computed(
  () => detail.value?.status === 'SUSPENDED' || detail.value?.status === 'RETIRED',
)

async function load() {
  if (!projectId.value || !profileId.value) return
  loading.value = true
  error.value = null
  try {
    const result = await getProjectFulfillmentProfile(projectId.value, profileId.value)
    detail.value = result.data
    revisions.value = await listProjectFulfillmentRevisions(projectId.value, profileId.value)
    if (canPreview.value || hasAllowedAction(detail.value, 'VALIDATE')) {
      manifest.value = (
        await compileProjectFulfillmentPreview(projectId.value, profileId.value)
      ).data
    } else {
      manifest.value = null
    }
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
        返回配置中心
      </Button>
    </template>
    <template #primary-action>
      <Space>
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
          运行说明
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
        v-if="readonly"
        type="warning"
        show-icon
        message="当前配置为只读状态"
        :description="`状态：${statusLabel(detail?.status || '')}。可查看历史版本，写操作已由服务端关闭。`"
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
        <TabPane key="overview" tab="运行说明书">
          <FulfillmentRunbookTable :runbook="manifest?.runbook" :loading="loading" />
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
