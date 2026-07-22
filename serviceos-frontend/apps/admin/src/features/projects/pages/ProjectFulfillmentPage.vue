<script setup lang="ts">
import type {
  CreateProjectFulfillmentProfileInput,
  ProjectFulfillmentProfileSummary,
} from '@serviceos/api-client'
import type { TableColumnsType } from '@serviceos/design-system'

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
const createOpen = ref(false)
const createdProfileName = ref<string>()
const form = reactive<CreateProjectFulfillmentProfileInput>({
  profileName: '',
  serviceProductCode: '',
  templateCode: 'HOME_CHARGING_SURVEY_INSTALL',
  description: '',
})
const createCommand = useCreateProjectFulfillmentCommand(() => projectId.value)

const columns: TableColumnsType<ProjectFulfillmentProfileSummary> = [
  { title: '履约方案', key: 'profile', width: 240 },
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
  form.serviceProductCode = ''
  form.templateCode = 'HOME_CHARGING_SURVEY_INSTALL'
  form.description = ''
  createCommand.reset()
}

function closeCreate() {
  createOpen.value = false
  resetCreateForm()
}

async function createProfile() {
  const created = await createCommand.mutateAsync({
    profileName: form.profileName.trim(),
    serviceProductCode: form.serviceProductCode.trim().toUpperCase(),
    templateCode: form.templateCode,
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
                <span>{{ serviceProductLabel(record.serviceProductCode) }}</span>
              </div>
            </template>
            <template v-else-if="column.key === 'status'">
              <Tag :color="presentStatus(record.status).color">
                {{ presentStatus(record.status).label }}
              </Tag>
            </template>
            <template v-else-if="column.key === 'stages'">{{ record.stageCount }} 个</template>
            <template v-else-if="column.key === 'assets'">{{ record.formCount }} 份 / {{ record.evidenceCount }} 项</template>
            <template v-else-if="column.key === 'version'">{{ record.activeVersion ?? '尚未发布' }}</template>
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
              <p>{{ selectedProfile.data.value.description || '尚未填写方案说明' }}</p>
            </div>
            <Tag :color="presentStatus(selectedProfile.data.value.status).color">
              {{ presentStatus(selectedProfile.data.value.status).label }}
            </Tag>
          </header>
          <dl class="fulfillment-detail-facts">
            <div><dt>当前版本</dt><dd>{{ selectedProfile.data.value.activeVersion ?? '尚未发布' }}</dd></div>
            <div><dt>活动草稿</dt><dd>{{ selectedProfile.data.value.draftRevisionId ? '存在未发布草稿' : '无草稿' }}</dd></div>
            <div><dt>生效时间</dt><dd>{{ selectedProfile.data.value.activeEffectiveFrom ? formatDateTime(selectedProfile.data.value.activeEffectiveFrom) : '尚未生效' }}</dd></div>
            <div><dt>最近更新</dt><dd>{{ formatDateTime(selectedProfile.data.value.updatedAt) }}</dd></div>
          </dl>
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
        <Form.Item label="初始化方式" required>
          <Select
            v-model:value="form.templateCode"
            :options="[
              { value: 'HOME_CHARGING_SURVEY_INSTALL', label: '家充勘测安装标准流程' },
              { value: 'BLANK', label: '空白配置' },
            ]"
          />
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
            :disabled="!form.profileName.trim() || !form.serviceProductCode.trim()"
            :loading="createCommand.isPending.value"
            @click="createProfile"
          >
            创建方案
          </Button>
        </div>
      </template>
    </Drawer>
  </Page>
</template>
