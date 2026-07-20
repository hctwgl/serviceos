<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Alert, Button, DatePicker, Input, Result, Space, Steps, Table } from 'ant-design-vue'
import { ArrowLeftOutlined } from '@ant-design/icons-vue'
import dayjs, { type Dayjs } from 'dayjs'
import DedicatedFlowLayout from '../patterns/templates/DedicatedFlowLayout.vue'
import FulfillmentRunbookTable from '../components/fulfillment/FulfillmentRunbookTable.vue'
import {
  compileProjectFulfillmentPreview,
  getProjectFulfillmentProfile,
  validateProjectFulfillmentDraft,
  type ProjectFulfillmentManifest,
  type ProjectFulfillmentProfileDetail,
  type ProjectFulfillmentValidationIssue,
} from '../api/fulfillmentProfiles'
import { createCoreApi, fromRaw, newIdempotencyKey, quotedVersion } from '../api/coreApi'
import { toUserFacingError } from '../product/errorMessages'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => String(route.params.id ?? ''))
const profileId = computed(() => String(route.params.profileId ?? ''))

const step = ref(0)
const loading = ref(false)
const publishing = ref(false)
const error = ref<string | null>(null)
const detail = ref<ProjectFulfillmentProfileDetail | null>(null)
const issues = ref<ProjectFulfillmentValidationIssue[]>([])
const manifest = ref<ProjectFulfillmentManifest | null>(null)
const effectiveFrom = ref<Dayjs | undefined>(dayjs().add(5, 'minute'))
const publishNote = ref('')
const publishedVersion = ref<number | null>(null)

const blockingErrors = computed(() => issues.value.filter((i) => i.severity === 'ERROR'))
const publishAllowed = computed(() => detail.value?.allowedActions?.includes('PUBLISH') ?? false)
const canPublish = computed(
  () =>
    publishAllowed.value &&
    blockingErrors.value.length === 0 &&
    !!manifest.value &&
    !!effectiveFrom.value,
)

async function loadStepData() {
  loading.value = true
  error.value = null
  try {
    detail.value = (await getProjectFulfillmentProfile(projectId.value, profileId.value)).data
    if (!detail.value.allowedActions?.includes('PUBLISH')) {
      issues.value = []
      manifest.value = null
      return
    }
    issues.value = (await validateProjectFulfillmentDraft(projectId.value, profileId.value)).data
    manifest.value = (
      await compileProjectFulfillmentPreview(projectId.value, profileId.value)
    ).data
  } catch (err) {
    error.value = toUserFacingError(err).message
  } finally {
    loading.value = false
  }
}

async function publish() {
  if (!detail.value || !effectiveFrom.value || !canPublish.value) return
  publishing.value = true
  error.value = null
  try {
    const revision = await fromRaw(
      createCoreApi().publishProjectFulfillmentRevisionRaw({
        projectId: projectId.value,
        profileId: profileId.value,
        idempotencyKey: newIdempotencyKey('pfp-publish'),
        ifMatch: quotedVersion(detail.value.aggregateVersion),
        publishProjectFulfillmentRevisionRequest: {
          effectiveFrom: effectiveFrom.value.toDate(),
          publishNote: publishNote.value || '发布履约配置',
        },
      }),
    )
    publishedVersion.value = revision.data.versionNo ?? null
    step.value = 4
  } catch (err) {
    error.value = toUserFacingError(err).message
  } finally {
    publishing.value = false
  }
}

onMounted(loadStepData)
</script>

<template>
  <DedicatedFlowLayout
    title="发布履约配置新版本"
    description="专用四步发布流程。存量工单继续使用创建时冻结版本，不受本次发布影响。"
  >
    <template #secondary-actions>
      <Button
        @click="
          router.push({
            name: 'ADMIN.PROJECT.FULFILLMENT.EDIT',
            params: { id: projectId, profileId },
          })
        "
      >
        <template #icon><ArrowLeftOutlined /></template>
        返回编辑
      </Button>
    </template>
    <template #feedback>
      <Alert v-if="error" type="error" show-icon :message="error" style="margin-bottom: 12px" />
      <Alert
        v-else-if="detail && !publishAllowed"
        type="info"
        show-icon
        message="当前配置不可发布"
        description="当前状态或权限未允许发布新版本。请返回配置详情查看可执行操作。"
        style="margin-bottom: 12px"
      />
    </template>

    <Steps
      :current="Math.min(step, 3)"
      :items="[
        { title: '完整性校验' },
        { title: '运行说明预览' },
        { title: '影响分析' },
        { title: '设置生效并发布' },
      ]"
      style="margin-bottom: 24px"
    />

    <div v-if="step === 0">
      <Alert
        :type="blockingErrors.length ? 'error' : 'success'"
        show-icon
        :message="
          blockingErrors.length
            ? `存在 ${blockingErrors.length} 个阻断错误，无法发布`
            : publishAllowed
              ? '未发现阻断错误'
              : '当前状态或权限不允许发布'
        "
        style="margin-bottom: 12px"
      />
      <Table
        size="small"
        row-key="errorCode"
        :pagination="false"
        :loading="loading"
        :data-source="issues"
        :columns="[
          { title: '级别', dataIndex: 'severity', width: 100 },
          { title: '阶段', dataIndex: 'stageCode', width: 140 },
          { title: '说明', dataIndex: 'userMessage' },
          { title: '建议', dataIndex: 'suggestion' },
        ]"
      />
      <Space style="margin-top: 16px">
        <Button
          type="primary"
          :disabled="!publishAllowed || !!blockingErrors.length"
          @click="step = 1"
        >
          下一步
        </Button>
      </Space>
    </div>

    <div v-else-if="step === 1">
      <Alert
        type="info"
        show-icon
        message="请确认本版本的工单运行说明"
        description="普通发布流程不展示 Manifest JSON、Digest 或内部配置引用。"
        style="margin-bottom: 12px"
      />
      <FulfillmentRunbookTable :manifest-json="manifest?.manifestJson" :loading="loading" />
      <Space style="margin-top: 16px">
        <Button @click="step = 0">上一步</Button>
        <Button type="primary" @click="step = 2">下一步</Button>
      </Space>
    </div>

    <div v-else-if="step === 2">
      <Alert
        type="warning"
        show-icon
        message="存量工单继续使用创建时冻结版本，不受本次发布影响。"
        description="新工单在生效时间后命中本版本。不会自动迁移历史工单阶段、表单或资料要求。"
        style="margin-bottom: 12px"
      />
      <Alert
        type="info"
        show-icon
        message="版本差异明细尚未由服务端返回"
        description="本步骤当前只确认版本隔离与生效范围。M385 后续将接入服务端 Revision Compare / Impact DTO，届时逐项展示阶段、表单、资料、动作和 SLA 的真实变化。"
      />
      <Space style="margin-top: 16px">
        <Button @click="step = 1">上一步</Button>
        <Button type="primary" @click="step = 3">已核对运行说明，继续</Button>
      </Space>
    </div>

    <div v-else-if="step === 3">
      <label class="field">
        <span>生效时间</span>
        <DatePicker
          v-model:value="effectiveFrom"
          show-time
          style="width: 100%"
          aria-label="生效时间"
        />
      </label>
      <label class="field">
        <span>发布备注</span>
        <Input.TextArea v-model:value="publishNote" :rows="3" aria-label="发布备注" />
      </label>
      <Alert
        type="info"
        show-icon
        message="最终确认：发布后版本不可修改。"
        style="margin: 12px 0"
      />
      <Space>
        <Button @click="step = 2">上一步</Button>
        <Button type="primary" :loading="publishing" :disabled="!canPublish" @click="publish">
          确认发布
        </Button>
      </Space>
    </div>

    <Result
      v-else
      status="success"
      :title="`已发布版本 v${publishedVersion}`"
      sub-title="新工单将在生效时间后使用该版本；存量工单不变。"
    >
      <template #extra>
        <Button
          type="primary"
          @click="
            router.push({
              name: 'ADMIN.PROJECT.FULFILLMENT.DETAIL',
              params: { id: projectId, profileId },
            })
          "
        >
          查看配置
        </Button>
      </template>
    </Result>
  </DedicatedFlowLayout>
</template>

<style scoped>
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 12px;
}
</style>
