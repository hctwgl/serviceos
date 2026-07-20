<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Alert,
  Button,
  Form,
  FormItem,
  Input,
  Radio,
  RadioGroup,
  Select,
  Space,
  Steps,
} from 'ant-design-vue'
import { ArrowLeftOutlined, CheckOutlined } from '@ant-design/icons-vue'
import FormPageLayout from '../patterns/templates/FormPageLayout.vue'
import {
  createProjectFulfillmentProfile,
  listProjectFulfillmentProfiles,
  type ProjectFulfillmentProfileSummary,
} from '../api/fulfillmentProfiles'
import { getAuthorizedProject, type ProjectDetail } from '../api/projectDetail'
import { labelServiceProduct } from '../presentation/enum-labels'
import { toUserFacingError } from '../product/errorMessages'

type StartMode = 'STANDARD' | 'COPY' | 'BLANK'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => String(route.params.id ?? ''))

const loading = ref(false)
const submitting = ref(false)
const error = ref<string | null>(null)
const currentStep = ref(0)
const project = ref<ProjectDetail | null>(null)
const existingProfiles = ref<ProjectFulfillmentProfileSummary[]>([])

const model = reactive({
  serviceProductCode: 'HOME_CHARGING_SURVEY_INSTALL',
  profileName: '标准家充履约方案',
  description: '',
  startMode: 'STANDARD' as StartMode,
  copyFromProfileId: undefined as string | undefined,
})

const serviceProductOptions = [
  { value: 'HOME_CHARGING_SURVEY_INSTALL', label: '家充勘测安装' },
  { value: 'SURVEY', label: '勘测服务' },
  { value: 'INSTALLATION', label: '安装服务' },
  { value: 'REPAIR', label: '维修服务' },
  { value: 'CORRECTION', label: '整改服务' },
  { value: 'SECOND_VISIT', label: '二次上门' },
]

const copyOptions = computed(() =>
  existingProfiles.value.map((item) => ({
    value: item.profileId,
    label: `${item.profileName} · ${labelServiceProduct(item.serviceProductCode)}`,
  })),
)

const selectedProductLabel = computed(() => labelServiceProduct(model.serviceProductCode))
const standardTemplateAvailable = computed(
  () => model.serviceProductCode === 'HOME_CHARGING_SURVEY_INSTALL',
)
const duplicateProfile = computed(() =>
  existingProfiles.value.find((item) => item.serviceProductCode === model.serviceProductCode),
)
const startModeLabel = computed(() => {
  if (model.startMode === 'STANDARD') return '从标准勘测安装方案开始'
  if (model.startMode === 'COPY') {
    const source = existingProfiles.value.find((item) => item.profileId === model.copyFromProfileId)
    return source ? `复制“${source.profileName}”` : '复制项目内已有方案'
  }
  return '从空白方案开始'
})

const canContinue = computed(() => {
  if (currentStep.value === 0) {
    return !!model.serviceProductCode && !duplicateProfile.value
  }
  if (currentStep.value === 1) {
    if (!model.profileName.trim()) return false
    if (model.startMode === 'STANDARD' && !standardTemplateAvailable.value) return false
    if (model.startMode === 'COPY' && !model.copyFromProfileId) return false
    return true
  }
  return true
})

async function load() {
  if (!projectId.value) return
  loading.value = true
  error.value = null
  try {
    const [projectResult, profiles] = await Promise.all([
      getAuthorizedProject(projectId.value),
      listProjectFulfillmentProfiles(projectId.value),
    ])
    project.value = projectResult
    existingProfiles.value = profiles
  } catch (err) {
    error.value = toUserFacingError(err).message
  } finally {
    loading.value = false
  }
}

function next() {
  if (!canContinue.value) return
  currentStep.value = Math.min(2, currentStep.value + 1)
}

function previous() {
  currentStep.value = Math.max(0, currentStep.value - 1)
}

function cancel() {
  router.push({
    name: 'ADMIN.PROJECT.FULFILLMENT.LIST',
    params: { id: projectId.value },
  })
}

async function submit() {
  if (!canContinue.value || duplicateProfile.value) return
  submitting.value = true
  error.value = null
  try {
    const created = await createProjectFulfillmentProfile(projectId.value, {
      serviceProductCode: model.serviceProductCode,
      profileName: model.profileName.trim(),
      description: model.description.trim() || undefined,
      templateCode:
        model.startMode === 'STANDARD'
          ? 'HOME_CHARGING_SURVEY_INSTALL'
          : model.startMode === 'BLANK'
            ? 'BLANK'
            : undefined,
      copyFromProfileId: model.startMode === 'COPY' ? model.copyFromProfileId : undefined,
    })
    await router.push({
      name: 'ADMIN.PROJECT.FULFILLMENT.EDIT',
      params: { id: projectId.value, profileId: created.data.profileId },
    })
  } catch (err) {
    error.value = toUserFacingError(err).message
  } finally {
    submitting.value = false
  }
}

watch(
  () => model.serviceProductCode,
  (nextCode, previousCode) => {
    if (nextCode !== 'HOME_CHARGING_SURVEY_INSTALL' && model.startMode === 'STANDARD') {
      model.startMode = 'BLANK'
    }
    const previousLabel = labelServiceProduct(previousCode)
    const nextLabel = labelServiceProduct(nextCode)
    if (!model.profileName.trim() || model.profileName.includes(previousLabel)) {
      model.profileName = `${nextLabel}履约方案`
    }
  },
)

watch(
  () => model.startMode,
  (mode) => {
    if (mode !== 'COPY') model.copyFromProfileId = undefined
  },
)

onMounted(load)
</script>

<template>
  <FormPageLayout
    title="新增工单类型配置"
    description="为当前项目选择一种工单类型，并创建可继续编辑、校验和发布的履约方案草稿。"
    :sticky-note="currentStep === 2 ? '确认后只创建草稿，不会立即影响新工单。' : '完成当前步骤后继续。'"
  >
    <template #feedback>
      <Alert v-if="error" type="error" show-icon :message="error" style="margin-bottom: 12px" />
      <Alert
        v-if="duplicateProfile"
        type="warning"
        show-icon
        message="当前项目已经配置了该工单类型"
        :description="`已有方案：${duplicateProfile.profileName}。同一项目下同一种工单类型只能保留一套履约配置。`"
        style="margin-bottom: 12px"
      />
    </template>

    <Steps
      :current="currentStep"
      :items="[
        { title: '选择工单类型' },
        { title: '设置起始方案' },
        { title: '确认创建' },
      ]"
      style="margin-bottom: 24px"
    />

    <Form layout="vertical" :disabled="loading || submitting">
      <template v-if="currentStep === 0">
        <FormItem label="工单类型" required>
          <Select
            v-model:value="model.serviceProductCode"
            :options="serviceProductOptions"
            show-search
            option-filter-prop="label"
            placeholder="请选择工单类型"
          />
        </FormItem>
        <Alert
          type="info"
          show-icon
          message="一类工单对应一套履约配置"
          description="流程、表单、资料、动作、审核和 SLA 都将在创建草稿后按该工单类型配置。"
        />
      </template>

      <template v-else-if="currentStep === 1">
        <FormItem label="方案名称" required>
          <Input v-model:value="model.profileName" :maxlength="200" placeholder="例如：山东家充勘测安装方案" />
        </FormItem>
        <FormItem label="业务说明">
          <Input.TextArea
            v-model:value="model.description"
            :maxlength="2000"
            :rows="3"
            show-count
            placeholder="说明适用区域、合同年度或特殊履约要求"
          />
        </FormItem>
        <FormItem label="起始方式" required>
          <RadioGroup v-model:value="model.startMode">
            <Space direction="vertical" :size="12">
              <Radio value="STANDARD" :disabled="!standardTemplateAvailable">
                从标准勘测安装方案开始
                <span v-if="!standardTemplateAvailable" class="muted">（当前工单类型暂无标准模板）</span>
              </Radio>
              <Radio value="COPY" :disabled="copyOptions.length === 0">复制项目内已有方案</Radio>
              <Radio value="BLANK">从空白方案开始</Radio>
            </Space>
          </RadioGroup>
        </FormItem>
        <FormItem v-if="model.startMode === 'COPY'" label="复制来源" required>
          <Select
            v-model:value="model.copyFromProfileId"
            :options="copyOptions"
            show-search
            option-filter-prop="label"
            placeholder="请选择要复制的履约方案"
          />
        </FormItem>
      </template>

      <template v-else>
        <Alert
          type="success"
          show-icon
          message="即将创建履约方案草稿"
          description="创建后将进入配置编辑工作区。只有完成校验并发布后，新工单才会按生效时间使用该版本。"
          style="margin-bottom: 16px"
        />
        <dl class="summary-list">
          <div>
            <dt>所属项目</dt>
            <dd>{{ project?.project.name || '加载中' }}</dd>
          </div>
          <div>
            <dt>工单类型</dt>
            <dd>{{ selectedProductLabel }}</dd>
          </div>
          <div>
            <dt>方案名称</dt>
            <dd>{{ model.profileName }}</dd>
          </div>
          <div>
            <dt>起始方式</dt>
            <dd>{{ startModeLabel }}</dd>
          </div>
          <div>
            <dt>创建结果</dt>
            <dd>仅创建草稿，不立即生效</dd>
          </div>
        </dl>
      </template>
    </Form>

    <template #impact>
      <p><strong>当前项目</strong></p>
      <p>{{ project?.project.name || '正在加载项目信息' }}</p>
      <p><strong>创建后的下一步</strong></p>
      <p>配置阶段责任、表单、资料、动作、审核、SLA 和异常路径。</p>
      <Alert
        type="info"
        show-icon
        message="不会影响存量工单"
        description="草稿发布前不参与建单解析；发布新版本也不会改变已经冻结配置的存量工单。"
      />
    </template>

    <template #sticky-secondary>
      <Button @click="cancel">
        <template #icon><ArrowLeftOutlined /></template>
        取消
      </Button>
    </template>
    <template #sticky-actions>
      <Space>
        <Button v-if="currentStep > 0" @click="previous">上一步</Button>
        <Button v-if="currentStep < 2" type="primary" :disabled="!canContinue" @click="next">
          下一步
        </Button>
        <Button
          v-else
          type="primary"
          :loading="submitting"
          :disabled="!canContinue || !!duplicateProfile"
          @click="submit"
        >
          <template #icon><CheckOutlined /></template>
          创建草稿并继续配置
        </Button>
      </Space>
    </template>
  </FormPageLayout>
</template>

<style scoped>
.muted {
  margin-left: 6px;
  color: var(--sos-color-text-secondary);
}
.summary-list {
  margin: 0;
  border: 1px solid var(--sos-color-border-light);
  border-radius: var(--sos-radius-md);
}
.summary-list > div {
  display: grid;
  grid-template-columns: 140px minmax(0, 1fr);
  gap: 16px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--sos-color-border-light);
}
.summary-list > div:last-child {
  border-bottom: 0;
}
.summary-list dt {
  color: var(--sos-color-text-secondary);
}
.summary-list dd {
  margin: 0;
  font-weight: 500;
}
</style>
