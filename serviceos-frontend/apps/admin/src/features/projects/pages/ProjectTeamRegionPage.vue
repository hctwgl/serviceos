<script setup lang="ts">
import type {
  ProjectPositionCode,
  ProjectRegionPersonnelAssignment,
  ProjectTeamMember,
} from '@serviceos/api-client'
import type { TableColumnsType } from '@serviceos/design-system'

import {
  Button,
  Card,
  Checkbox,
  Drawer,
  Empty,
  Form,
  Input,
  Select,
  Space,
  Table,
  Tag,
  TeamOutlined,
} from '@serviceos/design-system'
import { Page } from '@vben/common-ui'
import { computed, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import PageError from '../../../components/PageError.vue'
import { formatDateTime } from '../../../presenters/work-order'
import {
  useAddProjectTeamMemberCommand,
  useAssignProjectRegionPersonnelCommand,
} from '../commands/use-project-team-commands'
import {
  useProjectPersonnelMatchQuery,
  useProjectTeamQuery,
} from '../queries/use-project-team-query'

const route = useRoute()
const projectId = computed(() => String(route.params.id ?? ''))
const workspaceQuery = useProjectTeamQuery(projectId)
const workspace = computed(() => workspaceQuery.data.value)
const selectedRegionCode = ref('')
const matchQuery = useProjectPersonnelMatchQuery(projectId, selectedRegionCode)
const addMemberCommand = useAddProjectTeamMemberCommand(() => projectId.value)
const assignCommand = useAssignProjectRegionPersonnelCommand(() => projectId.value)

const memberDrawerOpen = ref(false)
const assignmentDrawerOpen = ref(false)
const selectedCandidate = ref<string>()
const successMessage = ref('')
const assignmentForm = reactive({
  regionCode: '',
  positionCode: 'CUSTOMER_SERVICE_MANAGER' as ProjectPositionCode,
  principalId: '',
  expectedCurrentAssignmentId: null as string | null,
  allowInheritance: true,
  reason: '',
})

const positions: Array<{ code: ProjectPositionCode; name: string; description: string }> = [
  { code: 'CUSTOMER_SERVICE_MANAGER', name: '客服经理', description: '负责客户沟通、服务协调与问题跟进' },
  { code: 'PROJECT_MANAGER', name: '项目经理', description: '负责项目履约结果与重大异常协调' },
  { code: 'PROJECT_ASSISTANT', name: '项目助理', description: '负责日常资料、进度和跨方协同' },
]

const memberColumns: TableColumnsType<ProjectTeamMember> = [
  { title: '项目成员', key: 'person', width: 220 },
  { title: '员工编号', key: 'employeeNumber', width: 130 },
  { title: '加入时间', key: 'validFrom', width: 170 },
  { title: '状态', key: 'status', width: 90 },
]

const assignmentColumns: TableColumnsType<ProjectRegionPersonnelAssignment> = [
  { title: '行政区域', key: 'region', width: 180 },
  { title: '项目岗位', key: 'position', width: 140 },
  { title: '负责人', key: 'person', width: 180 },
  { title: '下级继承', key: 'inheritance', width: 110 },
  { title: '变更原因', key: 'reason', width: 250 },
  { title: '操作', key: 'action', width: 90, fixed: 'right' },
]

const memberOptions = computed(() => (workspace.value?.members ?? [])
  .filter((item) => item.dataComplete && item.displayName)
  .map((item) => ({ value: item.principalId, label: `${item.displayName}${item.employeeNumber ? ` · ${item.employeeNumber}` : ''}` })))

const candidateOptions = computed(() => (workspace.value?.candidates ?? [])
  .filter((item) => !item.alreadyMember)
  .map((item) => ({ value: item.principalId, label: `${item.displayName}${item.employeeNumber ? ` · ${item.employeeNumber}` : ''}` })))

const regionOptions = computed(() => (workspace.value?.regions ?? []).map((item) => ({
  value: item.code,
  label: `${item.name} · ${regionLevelLabel(item.level)}`,
})))

const matchByPosition = computed(() => new Map(
  (matchQuery.data.value?.matches ?? []).map((item) => [item.position, item]),
))

watch(workspace, (value) => {
  if (!selectedRegionCode.value && value?.regions.length) {
    const preferred = value.regions.find((item) => item.level === 'DISTRICT') ?? value.regions[0]
    selectedRegionCode.value = preferred?.code ?? ''
  }
}, { immediate: true })

function regionLevelLabel(level: string) {
  if (level === 'COUNTRY') return '全国'
  if (level === 'PROVINCE') return '省级'
  if (level === 'CITY') return '市级'
  return '区县'
}

function openAssignment(
  position: ProjectPositionCode,
  regionCode = selectedRegionCode.value,
  current?: ProjectRegionPersonnelAssignment,
) {
  assignmentForm.regionCode = regionCode
  assignmentForm.positionCode = position
  assignmentForm.principalId = current?.principalId ?? ''
  assignmentForm.expectedCurrentAssignmentId = current?.assignmentId ?? null
  assignmentForm.allowInheritance = current?.allowInheritance ?? true
  assignmentForm.reason = ''
  assignCommand.reset()
  assignmentDrawerOpen.value = true
}

function openExistingAssignment(record: unknown) {
  const current = record as ProjectRegionPersonnelAssignment
  openAssignment(current.position, current.regionCode, current)
}

async function addMember() {
  if (!selectedCandidate.value) return
  const result = await addMemberCommand.mutateAsync(selectedCandidate.value)
  successMessage.value = `已将${result.displayName ?? '所选人员'}加入项目团队。`
  selectedCandidate.value = undefined
  memberDrawerOpen.value = false
}

async function saveAssignment() {
  const result = await assignCommand.mutateAsync({
    regionCode: assignmentForm.regionCode,
    positionCode: assignmentForm.positionCode,
    principalId: assignmentForm.principalId,
    expectedCurrentAssignmentId: assignmentForm.expectedCurrentAssignmentId,
    allowInheritance: assignmentForm.allowInheritance,
    reason: assignmentForm.reason.trim(),
  })
  successMessage.value = `${result.regionName}的${result.positionName}已设置为${result.displayName ?? '数据不完整'}。`
  selectedRegionCode.value = assignmentForm.regionCode
  assignmentDrawerOpen.value = false
}
</script>

<template>
  <Page
    :title="workspace ? `${workspace.projectName} · 项目团队与区域分工` : '项目团队与区域分工'"
    description="按项目和标准行政区域维护客服经理、项目经理、项目助理，供新工单创建时形成稳定人员快照。"
    content-class="project-team-page"
  >
    <template #extra>
      <Space>
        <RouterLink :to="`/projects/${projectId}`"><Button>返回项目详情</Button></RouterLink>
        <Button
          v-if="workspace?.allowedActions.includes('ADD_MEMBER')"
          @click="memberDrawerOpen = true"
        >
          添加项目成员
        </Button>
        <Button
          v-if="workspace?.allowedActions.includes('ASSIGN_REGION_PERSONNEL')"
          type="primary"
          :disabled="!selectedRegionCode"
          @click="openAssignment('CUSTOMER_SERVICE_MANAGER')"
        >
          设置区域负责人
        </Button>
      </Space>
    </template>

    <PageError
      v-if="workspaceQuery.isError.value"
      :detail="workspaceQuery.error.value?.message ?? '项目团队与区域分工加载失败'"
    />
    <div v-else-if="workspaceQuery.isLoading.value" class="page-loading">正在加载项目团队与区域分工…</div>
    <template v-else-if="workspace">
      <div v-if="successMessage" class="product-notice success">{{ successMessage }}</div>

      <div class="project-team-summary">
        <Card :bordered="false">
          <span>当前项目成员</span><strong>{{ workspace.members.length }}</strong><small>可承担项目固定岗位</small>
        </Card>
        <Card :bordered="false">
          <span>已配置分工</span><strong>{{ workspace.assignments.length }}</strong><small>行政区与岗位组合</small>
        </Card>
        <Card :bordered="false">
          <span>服务行政区</span><strong>{{ workspace.regions.length }}</strong><small>含项目范围内下级区域</small>
        </Card>
        <Card :bordered="false">
          <span>数据更新时间</span><b>{{ formatDateTime(workspace.asOf) }}</b><small>目标页面状态为准</small>
        </Card>
      </div>

      <div class="project-team-layout">
        <main class="project-team-main">
          <Card class="project-team-card" :bordered="false" title="区域岗位分工">
            <template #extra><Tag color="blue">最精确行政区优先</Tag></template>
            <div class="region-preview-toolbar">
              <div><strong>人员匹配预览</strong><span>选择工单地址所属行政区，检查实际命中的项目人员。</span></div>
              <Select
                v-model:value="selectedRegionCode"
                show-search
                option-filter-prop="label"
                :options="regionOptions"
                placeholder="选择行政区域"
              />
            </div>
            <PageError
              v-if="matchQuery.isError.value"
              :detail="matchQuery.error.value?.message ?? '人员匹配预览失败'"
            />
            <div v-else class="position-preview-grid">
              <article v-for="position in positions" :key="position.code">
                <header><span>{{ position.name }}</span><Tag v-if="matchByPosition.get(position.code)?.inherited" color="blue">上级区域继承</Tag></header>
                <template v-if="matchByPosition.get(position.code)">
                  <strong>{{ matchByPosition.get(position.code)?.displayName ?? '数据不完整' }}</strong>
                  <p>命中 {{ matchByPosition.get(position.code)?.matchedRegionName }}</p>
                </template>
                <template v-else>
                  <strong class="missing-person">人员待确认</strong>
                  <p>当前区域没有可用的{{ position.name }}分工</p>
                </template>
                <footer>
                  <span>{{ position.description }}</span>
                  <Button
                    v-if="workspace.allowedActions.includes('ASSIGN_REGION_PERSONNEL')"
                    type="link"
                    size="small"
                    @click="openAssignment(position.code)"
                  >
                    设置
                  </Button>
                </footer>
              </article>
            </div>

            <Table
              row-key="assignmentId"
              size="middle"
              :columns="assignmentColumns"
              :data-source="workspace.assignments"
              :pagination="false"
              :scroll="{ x: 950 }"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'region'">
                  <div class="table-primary-cell"><strong>{{ record.regionName }}</strong><span>{{ regionLevelLabel(record.regionLevel) }}</span></div>
                </template>
                <template v-else-if="column.key === 'position'"><Tag>{{ record.positionName }}</Tag></template>
                <template v-else-if="column.key === 'person'">
                  <span :class="{ 'missing-person': !record.dataComplete }">{{ record.displayName ?? '数据不完整' }}</span>
                </template>
                <template v-else-if="column.key === 'inheritance'">{{ record.allowInheritance ? '允许' : '不允许' }}</template>
                <template v-else-if="column.key === 'reason'">{{ record.changeReason }}</template>
                <template v-else-if="column.key === 'action'">
                  <Button
                    v-if="workspace.allowedActions.includes('ASSIGN_REGION_PERSONNEL')"
                    type="link"
                    size="small"
                    @click="openExistingAssignment(record)"
                  >
                    更换
                  </Button>
                </template>
              </template>
              <template #emptyText><Empty description="项目尚未配置区域岗位负责人" /></template>
            </Table>
          </Card>
        </main>

        <aside class="project-team-rail">
          <Card class="project-team-card" :bordered="false" title="项目成员">
            <Table
              row-key="memberId"
              size="small"
              :columns="memberColumns"
              :data-source="workspace.members"
              :pagination="false"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'person'">
                  <div class="team-member-cell"><span><TeamOutlined /></span><div><strong>{{ record.displayName ?? '数据不完整' }}</strong><small>项目成员</small></div></div>
                </template>
                <template v-else-if="column.key === 'employeeNumber'">{{ record.employeeNumber ?? '—' }}</template>
                <template v-else-if="column.key === 'validFrom'">{{ formatDateTime(record.validFrom) }}</template>
                <template v-else-if="column.key === 'status'"><Tag color="success">有效</Tag></template>
              </template>
              <template #emptyText><Empty description="项目尚未添加成员" /></template>
            </Table>
          </Card>
          <Card class="project-team-rule-card" :bordered="false" title="匹配规则">
            <ol>
              <li>按工单所属项目限定人员范围。</li>
              <li>按标准行政区编码优先匹配区县，再逐级向上。</li>
              <li>只有明确允许继承的上级分工可以命中。</li>
              <li>缺少岗位时进入“项目人员待确认”，绝不选择默认账号。</li>
            </ol>
          </Card>
        </aside>
      </div>
    </template>

    <Drawer v-model:open="memberDrawerOpen" title="添加项目成员" width="440">
      <Form layout="vertical" @submit.prevent="addMember">
        <Form.Item label="选择人员" required>
          <Select
            v-model:value="selectedCandidate"
            show-search
            option-filter-prop="label"
            :options="candidateOptions"
            placeholder="搜索姓名或员工编号"
          />
        </Form.Item>
        <div class="drawer-explanation">只有当前租户内有效人员可以加入项目。加入项目不等于授予系统权限。</div>
        <PageError v-if="addMemberCommand.isError.value" :detail="addMemberCommand.error.value?.message ?? '添加项目成员失败'" />
        <div class="drawer-actions">
          <Button @click="memberDrawerOpen = false">取消</Button>
          <Button type="primary" html-type="submit" :loading="addMemberCommand.isPending.value" :disabled="!selectedCandidate">确认添加</Button>
        </div>
      </Form>
    </Drawer>

    <Drawer v-model:open="assignmentDrawerOpen" title="设置区域岗位负责人" width="500">
      <Form layout="vertical" @submit.prevent="saveAssignment">
        <Form.Item label="行政区域" required>
          <Select v-model:value="assignmentForm.regionCode" show-search option-filter-prop="label" :options="regionOptions" />
        </Form.Item>
        <Form.Item label="项目岗位" required>
          <Select v-model:value="assignmentForm.positionCode" :options="positions.map((item) => ({ value: item.code, label: item.name }))" />
        </Form.Item>
        <Form.Item label="负责人" required>
          <Select v-model:value="assignmentForm.principalId" show-search option-filter-prop="label" :options="memberOptions" placeholder="选择当前项目成员" />
        </Form.Item>
        <Form.Item>
          <Checkbox v-model:checked="assignmentForm.allowInheritance">允许下级行政区在没有更精确配置时继承该负责人</Checkbox>
        </Form.Item>
        <Form.Item label="设置或变更原因" required>
          <Input.TextArea v-model:value="assignmentForm.reason" :maxlength="500" :rows="4" show-count placeholder="说明本次人员分工的业务原因" />
        </Form.Item>
        <PageError v-if="assignCommand.isError.value" :detail="assignCommand.error.value?.message ?? '设置区域负责人失败'" />
        <div class="drawer-actions">
          <Button @click="assignmentDrawerOpen = false">取消</Button>
          <Button type="primary" html-type="submit" :loading="assignCommand.isPending.value" :disabled="!assignmentForm.regionCode || !assignmentForm.principalId || !assignmentForm.reason.trim()">确认设置</Button>
        </div>
      </Form>
    </Drawer>
  </Page>
</template>
