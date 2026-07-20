<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Alert, Button, Descriptions, Table } from 'ant-design-vue'
import { ArrowLeftOutlined } from '@ant-design/icons-vue'
import DetailPageLayout from '../patterns/templates/DetailPageLayout.vue'
import {
  getWorkOrderFulfillmentSnapshot,
  type WorkOrderFulfillmentSnapshot,
} from '../api/fulfillmentProfiles'
import { formatDateTimeDisplay } from '../presentation/date-time.presenter'
import { labelServiceProduct } from '../presentation/enum-labels'
import { toUserFacingError } from '../product/errorMessages'

const route = useRoute()
const router = useRouter()
const workOrderId = computed(() => String(route.params.id ?? ''))

const loading = ref(false)
const error = ref<string | null>(null)
const snapshot = ref<WorkOrderFulfillmentSnapshot | null>(null)
const stageRows = ref<
  Array<{ stageName: string; owner: string; forms: string; evidence: string; actions: string; sla: string }>
>([])

async function load() {
  loading.value = true
  error.value = null
  try {
    snapshot.value = await getWorkOrderFulfillmentSnapshot(workOrderId.value)
    if (snapshot.value.manifestJson) {
      const parsed = JSON.parse(snapshot.value.manifestJson) as {
        stages?: Array<Record<string, unknown>>
      }
      stageRows.value = (parsed.stages ?? []).map((stage) => ({
        stageName: String(stage.stageName ?? stage.stageCode ?? '—'),
        owner: String(stage.ownerType ?? '—'),
        forms: Array.isArray(stage.formRefs) ? `${stage.formRefs.length} 项` : '—',
        evidence: Array.isArray(stage.evidenceRefs) ? `${stage.evidenceRefs.length} 项` : '—',
        actions: Array.isArray(stage.actions) ? `${stage.actions.length} 项` : '—',
        sla: stage.slaRef ? String(stage.slaRef) : '未绑定',
      }))
    } else {
      stageRows.value = []
    }
  } catch (err) {
    error.value = toUserFacingError(err).message
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <DetailPageLayout
    title="工单配置快照"
    description="展示建单时冻结的履约配置；不得显示完整内部 UUID 与原始 Manifest JSON。"
    :loading="loading"
  >
    <template #secondary-actions>
      <Button
        @click="router.push({ name: 'ADMIN.WORKORDER.WORKSPACE', params: { id: workOrderId } })"
      >
        <template #icon><ArrowLeftOutlined /></template>
        返回工单详情
      </Button>
    </template>
    <template #feedback>
      <Alert v-if="error" type="error" show-icon :message="error" style="margin-bottom: 12px" />
      <Alert
        v-if="snapshot?.legacyExplanation"
        type="warning"
        show-icon
        :message="snapshot.legacyExplanation"
        style="margin-bottom: 12px"
      />
    </template>

    <Descriptions v-if="snapshot" bordered size="small" :column="2" style="margin-bottom: 16px">
      <Descriptions.Item label="工单类型">
        {{ labelServiceProduct(snapshot.serviceProductCode) }}
      </Descriptions.Item>
      <Descriptions.Item label="配置种类">
        {{ snapshot.configKind === 'LEGACY_BUNDLE' ? '历史 Bundle 冻结' : '履约方案版本' }}
      </Descriptions.Item>
      <Descriptions.Item label="履约方案">
        {{ snapshot.profileName || '—' }}
      </Descriptions.Item>
      <Descriptions.Item label="履约版本">
        {{ snapshot.fulfillmentVersion || '—' }}
      </Descriptions.Item>
      <Descriptions.Item label="流程/Bundle 版本">
        {{ snapshot.configurationBundleVersion || '—' }}
      </Descriptions.Item>
      <Descriptions.Item label="冻结时间">
        {{ snapshot.frozenAt ? formatDateTimeDisplay(snapshot.frozenAt) : '—' }}
      </Descriptions.Item>
    </Descriptions>

    <Table
      size="middle"
      row-key="stageName"
      :pagination="false"
      :data-source="stageRows"
      :columns="[
        { title: '阶段', dataIndex: 'stageName' },
        { title: '责任', dataIndex: 'owner' },
        { title: '表单', dataIndex: 'forms' },
        { title: '资料', dataIndex: 'evidence' },
        { title: '动作', dataIndex: 'actions' },
        { title: 'SLA', dataIndex: 'sla' },
      ]"
    />
  </DetailPageLayout>
</template>
