<script setup lang="ts">
import type {
  CreateProjectFulfillmentProfileInput,
  ProjectFulfillmentMatchResult,
  ProjectFulfillmentProfileSummary,
} from '@serviceos/api-client'
import type { TableColumnsType } from '@serviceos/design-system'

import {
  simulateProjectFulfillmentMatch,
} from '@serviceos/api-client'
import {
  Button,
  Card,
  Drawer,
  Form,
  Input,
  Select,
  Space,
  Table,
  Tag,
} from '@serviceos/design-system'
import { Page } from '@vben/common-ui'
import { computed, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import PageError from '../../../components/PageError.vue'
import { formatDateTime } from '../../../presenters/work-order'
import { useCreateProjectFulfillmentCommand } from '../commands/use-create-project-fulfillment-command'
import {
  useProjectFulfillmentProfileQuery,
  useProjectFulfillmentProfilesQuery,
} from '../queries/use-project-fulfillment-query'
import { useProjectWorkspaceQuery } from '../queries/use-project-workspace-query'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => String(route.params.id ?? ''))
const project = useProjectWorkspaceQuery(projectId)
const profiles = useProjectFulfillmentProfilesQuery(projectId)
const selectedProfileId = ref<string>()
const selectedProfile = useProjectFulfillmentProfileQuery(projectId, selectedProfileId)
const selectedSummary = computed(() => (
  profiles.data.value?.find((item) => item.profileId === selectedProfileId.value)
))
const createOpen = ref(false)
const createdProfileName = ref<string>()
const initializationMode = ref('TEMPLATE:HOME_CHARGING_SURVEY_INSTALL')
const form = reactive<CreateProjectFulfillmentProfileInput>({
  matchPriority: 0,
  profileCode: '',
  profileName: '',
  serviceProductCode: '',
  description: '',
})
const createCommand = useCreateProjectFulfillmentCommand(() => projectId.value)
const simulationOpen = ref(false)
const simulationLoading = ref(false)
const simulationError = ref<string>()
const simulationResult = ref<ProjectFulfillmentMatchResult>()
const simulationForm = reactive({
  brandCode: 'BYD_OCEAN',
  provinceCode: '370000',
  serviceProductCode: 'HOME_CHARGING_SURVEY_INSTALL',
})
const initializationOptions = computed(() => [
  {
    value: 'TEMPLATE:HOME_CHARGING_SURVEY_INSTALL',
    label: '家充勘测安装标准流程（需后续绑定运行资产）',
  },
  {
    value: 'TEMPLATE:BLANK',
    label: '空白配置（适合新服务场景）',
  },
  ...(profiles.data.value ?? []).map((profile) => ({
    value: `COPY:${profile.profileId}`,
    label: `复制“${profile.profileName}”及其运行绑定`,
  })),
])

const columns: TableColumnsType<ProjectFulfillmentProfileSummary> = [
  { title: '履约方案', key: 'profile', width: 280 },
  { title: '匹配优先级', key: 'priority', width: 110 },
  { title: '状态', key: 'status', width: 100 },
  { title: '流程阶段', key: 'stages', width: 100 },
  { title: '表单 / 资料', key: 'assets', width: 130 },
  { title: '当前版本', key: 'version', width: 120 },
  { title: '最近更新', key: 'updatedAt', width: 150 },
  { title: '操作', key: 'action', width: 90 },
]

const statusPresentation = {
  ACTIVE: { color: 'success', label: '已发布' },
  DRAFT: { color: 'warning', label: '草稿' },
  RETIRED: { color: 'default', label: '已归档' },
  SUSPENDED: { color: 'error', label: '已暂停' },
} as const

function presentStatus(status: unknown) {
  const normalized = String(status) as keyof typeof statusPresentation
  return statusPresentation[normalized] ?? { color: 'default', label: '状态异常' }
}

function serviceProductLabel(code: string) {
  if (code === 'HOME_CHARGING_SURVEY_INSTALL') return '家充勘测安装服务'
  return '项目服务产品'
}

watch(
  () => profiles.data.value,
  (items) => {
    if (!items?.length) {
      selectedProfileId.value = undefined
      return
    }
    const requested = typeof route.query.profileId === 'string' ? route.query.profileId : undefined
    selectedProfileId.value = items.some((item) => item.profileId === requested)
      ? requested
      : items[0]?.profileId
  },
  { immediate: true },
)

function selectProfile(profileId: string) {
  selectedProfileId.value = profileId
  void router.replace({ query: { profileId } })
}

function resetCreateForm() {
  form.profileName = ''
  form.profileCode = ''
  form.serviceProductCode = ''
  form.matchPriority = 0
  form.description = ''
  initializationMode.value = 'TEMPLATE:HOME_CHARGING_SURVEY_INSTALL'
  createCommand.reset()
}

function closeCreate() {
  createOpen.value = false
  resetCreateForm()
}

function openSimulation() {
  simulationForm.serviceProductCode = selectedSummary.value?.serviceProductCode
    ?? 'HOME_CHARGING_SURVEY_INSTALL'
  simulationResult.value = undefined
  simulationError.value = undefined
  simulationOpen.value = true
}

function closeSimulation() {
  simulationOpen.value = false
  simulationResult.value = undefined
  simulationError.value = undefined
}

async function runSimulation() {
  simulationLoading.value = true
  simulationError.value = undefined
  simulationResult.value = undefined
  try {
    simulationResult.value = await simulateProjectFulfillmentMatch(projectId.value, {
      brandCode: simulationForm.brandCode.trim().toUpperCase() || undefined,
      provinceCode: simulationForm.provinceCode.trim() || undefined,
      serviceProductCode: simulationForm.serviceProductCode.trim().toUpperCase(),
    })
  } catch (error) {
    simulationError.value = error instanceof Error ? error.message : '模拟匹配失败'
  } finally {
    simulationLoading.value = false
  }
}

async function createProfile() {
  const copyFromProfileId = initializationMode.value.startsWith('COPY:')
    ? initializationMode.value.slice('COPY:'.length)
    : undefined
  const templateCode = initializationMode.value.startsWith('TEMPLATE:')
    ? initializationMode.value.slice('TEMPLATE:'.length) as 'BLANK' | 'HOME_CHARGING_SURVEY_INSTALL'
    : undefined
  const created = await createCommand.mutateAsync({
    copyFromProfileId,
    matchPriority: Number(form.matchPriority ?? 0),
    profileCode: form.profileCode?.trim().toUpperCase(),
    profileName: form.profileName.trim(),
    serviceProductCode: form.serviceProductCode.trim().toUpperCase(),
    templateCode,
    description: form.description?.trim() || undefined,
  })
  createdProfileName.value = created.profileName
  selectedProfileId.value = created.profileId
  closeCreate()
  await router.replace({ query: { profileId: created.profileId } })
}
</script>

<template>
  <Page
    :title="project.data.value ? `${project.data.value.projectName} · 履约配置` : '项目履约配置'"
    description="配置当前项目的服务产品、履约流程、表单资料、SLA、派单与审核规则。"
    content-class="project-fulfillment-page"
  >
    <template #extra>
      <Space>
        <RouterLink :to="`/projects/${projectId}`"><Button>返回项目详情</Button></RouterLink>
        <Button @click="openSimulation">模拟匹配</Button>
        <Button type="primary" @click="createOpen = true">新建履约方案</Button>
      </Space>
    </template>

    <div v-if="createdProfileName" class="product-notice success">
      已创建履约方案“{{ createdProfileName }}”，请继续完成草稿配置和发布校验。
    </div>
    <PageError v-if="profiles.isError.value" :detail="profiles.error.value?.message ?? '履约配置加载失败'" />

    <div v-else class="project-fulfillment-layout">
      <Card class="project-fulfillment-directory" :bordered="false" title="履约方案">
        <Table
          row-key="profileId"
          size="middle"
          :columns="columns"
          :data-source="profiles.data.value ?? []"
          :loading="profiles.isLoading.value"
          :pagination="false"
          :custom-row="(record: ProjectFulfillmentProfileSummary) => ({
            onClick: () => selectProfile(record.profileId),
            class: record.profileId === selectedProfileId ? 'selected-fulfillment-row' : '',
          })"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'profile'">
              <div class="table-primary-cell">
                <strong>{{ record.profileName }}</strong>
                <span>{{ serviceProductLabel(record.serviceProductCode) }} · {{ record.profileCode }}</span>
              </div>
            </template>
            <template v-else-if="column.key === 'priority'">{{ record.matchPriority }}</template>
            <template v-else-if="column.key === 'status'">
              <Tag :color="presentStatus(record.status).color">
                {{ presentStatus(record.status).label }}
              </Tag>
            </template>
            <template v-else-if="column.key === 'stages'">{{ record.stageCount }} 个</template>
            <template v-else-if="column.key === 'assets'">{{ record.formCount }} 份 / {{ record.evidenceCount }} 项</template>
            <template v-else-if="column.key === 'version'">{{ record.activeVersion ? `V${record.activeVersion}` : '尚未发布' }}</template>
            <template v-else-if="column.key === 'updatedAt'">{{ formatDateTime(record.updatedAt) }}</template>
            <template v-else-if="column.key === 'action'">
              <Button type="link" size="small" @click.stop="selectProfile(record.profileId)">查看</Button>
            </template>
          </template>
          <template #emptyText>
            <div class="workbench-empty-state">
              <strong>项目尚未建立履约方案</strong>
              <span>创建第一套方案后，才能配置流程、表单、SLA 与派单规则。</span>
            </div>
          </template>
        </Table>
      </Card>

      <Card class="project-fulfillment-detail" :bordered="false" title="配置概览">
        <PageError v-if="selectedProfile.isError.value" :detail="selectedProfile.error.value?.message ?? '配置详情加载失败'" />
        <div v-else-if="selectedProfile.isLoading.value" class="page-loading">正在加载配置详情…</div>
        <template v-else-if="selectedProfile.data.value">
          <header class="fulfillment-detail-heading">
            <div>
              <span>{{ serviceProductLabel(selectedProfile.data.value.serviceProductCode) }}</span>
              <h2>{{ selectedProfile.data.value.profileName }}</h2>
              <p>{{ selectedProfile.data.value.profileCode }} · 优先级 {{ selectedProfile.data.value.matchPriority }}</p>
              <p>{{ selectedProfile.data.value.description || '尚未填写方案说明' }}</p>
            </div>
            <Tag :color="presentStatus(selectedProfile.data.value.status).color">
              {{ presentStatus(selectedProfile.data.value.status).label }}
            </Tag>
          </header>
          <dl class="fulfillment-detail-facts">
            <div><dt>当前版本</dt><dd>{{ selectedProfile.data.value.activeVersion ? `V${selectedProfile.data.value.activeVersion}` : '尚未发布' }}</dd></div>
            <div><dt>活动草稿</dt><dd>{{ selectedProfile.data.value.draftRevisionId ? '存在未发布草稿' : '无草稿' }}</dd></div>
            <div><dt>生效时间</dt><dd>{{ selectedProfile.data.value.activeEffectiveFrom ? formatDateTime(selectedProfile.data.value.activeEffectiveFrom) : '尚未生效' }}</dd></div>
            <div><dt>最近更新</dt><dd>{{ formatDateTime(selectedProfile.data.value.updatedAt) }}</dd></div>
          </dl>
          <section v-if="selectedSummary" class="fulfillment-module-overview">
            <header>
              <div><h3>配置模块</h3><p>从业务模块理解当前履约方案，所有模块统一形成不可变发布版本。</p></div>
              <Tag v-if="selectedProfile.data.value.draftRevisionId" color="warning">存在待发布草稿</Tag>
            </header>
            <div class="fulfillment-module-grid">
              <article>
                <div class="module-index">01</div>
                <div><strong>流程设计</strong><p>{{ selectedSummary.stageCount }} 个履约阶段</p><span>{{ selectedSummary.workflowSummary || '尚未生成流程摘要' }}</span></div>
                <RouterLink v-if="selectedProfile.data.value.draftRevisionId" :to="`/projects/${projectId}/fulfillment/${selectedProfile.data.value.profileId}/draft`">配置流程</RouterLink>
              </article>
              <article>
                <div class="module-index">02</div>
                <div><strong>表单与资料</strong><p>{{ selectedSummary.formCount }} 份表单 · {{ selectedSummary.evidenceCount }} 项资料</p><span>随履约阶段冻结引用关系</span></div>
              </article>
              <article>
                <div class="module-index">03</div>
                <div><strong>SLA 与预约</strong><p>{{ selectedSummary.slaSummary || '尚未配置时效规则' }}</p><span>统一使用项目服务日历计算</span></div>
              </article>
              <article>
                <div class="module-index">04</div>
                <div><strong>版本与发布</strong><p>{{ selectedProfile.data.value.activeVersion ? `当前 V${selectedProfile.data.value.activeVersion}` : '尚无生效版本' }}</p><span>校验、比较影响后原子发布</span></div>
                <RouterLink v-if="selectedProfile.data.value.draftRevisionId" :to="`/projects/${projectId}/fulfillment/${selectedProfile.data.value.profileId}/publish`">发布准备</RouterLink>
              </article>
            </div>
          </section>
          <section class="fulfillment-next-actions">
            <h3>当前配置范围</h3>
            <p>流程、表单资料、SLA、派单与审核规则必须作为完整履约版本统一校验和发布，不能单独生效。</p>
            <div class="fulfillment-scope-list">
              <span>流程设计</span><span>表单与资料</span><span>SLA 与预约</span><span>网点与资源</span><span>审核与整改</span><span>版本与发布</span>
            </div>
            <RouterLink
              v-if="selectedProfile.data.value.draftRevisionId"
              class="fulfillment-edit-link"
              :to="`/projects/${projectId}/fulfillment/${selectedProfile.data.value.profileId}/draft`"
            >
              进入结构化草稿编辑
            </RouterLink>
          </section>
        </template>
        <div v-else class="workbench-empty-state">
          <strong>请选择一套履约方案</strong>
          <span>选择后可查看当前版本、草稿与可执行配置动作。</span>
        </div>
      </Card>
    </div>

    <Drawer :open="createOpen" width="520" title="新建履约方案" @close="closeCreate">
      <p class="create-user-intro">履约方案用于定义一种服务产品的完整履约规则。创建后仍需完成校验和发布，才会用于新工单。</p>
      <PageError v-if="createCommand.isError.value" :detail="createCommand.error.value?.message ?? '履约方案创建失败'" />
      <Form layout="vertical">
        <Form.Item label="方案名称" required>
          <Input v-model:value="form.profileName" placeholder="例如：家充勘测与安装" :maxlength="200" />
        </Form.Item>
        <Form.Item label="服务产品编码" required>
          <Input v-model:value="form.serviceProductCode" placeholder="例如：HOME_CHARGING_INSTALL" :maxlength="96" />
        </Form.Item>
        <Form.Item label="方案编码" required>
          <Input v-model:value="form.profileCode" placeholder="例如：BYD_SHANDONG_HOME_STANDARD" :maxlength="96" />
          <small class="field-hint">方案编码在项目内唯一；同一服务产品可以配置多套不同适用范围的方案。</small>
        </Form.Item>
        <Form.Item label="匹配优先级" required>
          <Input v-model:value="form.matchPriority" type="number" :min="-10000" :max="10000" />
          <small class="field-hint">数字越大越优先；同优先级时选择适用条件更具体的方案。</small>
        </Form.Item>
        <Form.Item label="初始化方式" required>
          <Select
            v-model:value="initializationMode"
            :options="initializationOptions"
          />
          <small class="field-hint">
            复制现有方案会在同一项目范围内原子复制流程文档、Workflow 与 Bundle 运行绑定。
          </small>
        </Form.Item>
        <Form.Item label="方案说明">
          <Input.TextArea v-model:value="form.description" :rows="4" placeholder="说明适用业务范围和履约目标" />
        </Form.Item>
      </Form>
      <template #footer>
        <div class="drawer-footer">
          <Button @click="closeCreate">取消</Button>
          <Button
            type="primary"
            :disabled="!form.profileName.trim() || !form.profileCode?.trim() || !form.serviceProductCode.trim()"
            :loading="createCommand.isPending.value"
            @click="createProfile"
          >
            创建方案
          </Button>
        </div>
      </template>
    </Drawer>

    <Drawer :open="simulationOpen" width="560" title="履约方案匹配模拟器" @close="closeSimulation">
      <p class="create-user-intro">使用与正式建单相同的适用范围、匹配优先级和规则具体度算法；模拟不会创建工单或运行实例。</p>
      <PageError v-if="simulationError" :detail="simulationError" />
      <Form layout="vertical">
        <Form.Item label="服务产品编码" required>
          <Input v-model:value="simulationForm.serviceProductCode" :maxlength="96" />
        </Form.Item>
        <Form.Item label="客户品牌编码">
          <Input v-model:value="simulationForm.brandCode" placeholder="例如：BYD_OCEAN" :maxlength="64" />
        </Form.Item>
        <Form.Item label="省级行政区编码">
          <Input v-model:value="simulationForm.provinceCode" placeholder="例如：370000" :maxlength="6" />
        </Form.Item>
      </Form>
      <section v-if="simulationResult" class="fulfillment-simulation-result">
        <header>
          <div><span>唯一命中</span><h3>{{ simulationResult.profileName }}</h3></div>
          <Tag color="success">V{{ simulationResult.fulfillmentVersion }}</Tag>
        </header>
        <dl>
          <div><dt>方案编码</dt><dd>{{ simulationResult.profileCode }}</dd></div>
          <div><dt>匹配优先级</dt><dd>{{ simulationResult.matchPriority }}</dd></div>
          <div><dt>规则具体度</dt><dd>{{ simulationResult.matchSpecificity }}</dd></div>
          <div><dt>配置包版本</dt><dd>{{ simulationResult.configurationBundleVersion }}</dd></div>
        </dl>
        <div class="fulfillment-simulation-reasons">
          <strong>命中解释</strong>
          <Tag v-for="reason in simulationResult.matchExplanation" :key="reason" color="processing">{{ reason }}</Tag>
        </div>
      </section>
      <template #footer>
        <div class="drawer-footer">
          <Button @click="closeSimulation">关闭</Button>
          <Button
            type="primary"
            :disabled="!simulationForm.serviceProductCode.trim()"
            :loading="simulationLoading"
            @click="runSimulation"
          >
            开始模拟
          </Button>
        </div>
      </template>
    </Drawer>
  </Page>
</template>
