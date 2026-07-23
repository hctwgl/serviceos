<script setup lang="ts">
import type {
  AdminUserDirectoryItem,
  PrincipalPersonaType,
  RoleGrant,
  RoleGrantScopeType,
  RoleGrantStatus,
} from '@serviceos/api-client'
import {
  Button,
  Drawer,
  Input,
  message,
  SearchOutlined,
  Select,
  UserAddOutlined,
} from '@serviceos/design-system'
import { computed, ref, watch } from 'vue'
import PageError from '../../../components/PageError.vue'
import StatusPill from '../../../components/StatusPill.vue'
import { formatDateTime } from '../../../presenters/work-order'
import { useRegisterUserCommand } from '../commands/use-register-user-command'
import { useGrantRoleCommand, useRevokeRoleGrantCommand } from '../commands/use-role-grant-commands'
import { useActiveRolesQuery, useUserRoleGrantsQuery } from '../queries/use-role-grants-query'
import {
  useUserDirectoryQuery,
  type UserDirectoryFilters,
} from '../queries/use-user-directory-query'

const keyword = ref('')
const status = ref<string>()
const filters = ref<UserDirectoryFilters>({})
const directory = useUserDirectoryQuery(filters)
const createCommand = useRegisterUserCommand()
const createOpen = ref(false)
const displayName = ref('')
const employeeNumber = ref('')
const personaType = ref<PrincipalPersonaType>('INTERNAL_EMPLOYEE')
const createdName = ref<string>()
const selectedUser = ref<AdminUserDirectoryItem>()

const selectedUserId = computed(() => selectedUser.value?.id)
const roleGrants = useUserRoleGrantsQuery(selectedUserId)
const activeRoles = useActiveRolesQuery()
const grantCommand = useGrantRoleCommand(() => selectedUser.value?.id ?? '')
const revokeCommand = useRevokeRoleGrantCommand(() => selectedUser.value?.id ?? '')
const grantFormOpen = ref(false)
const grantRoleId = ref<string>()
const grantScopeType = ref<RoleGrantScopeType>('TENANT')
const grantScopeRef = ref('tenant-local')
const grantReason = ref('')
const revokeTarget = ref<RoleGrant>()
const revokeReason = ref('')

const items = computed(() => directory.data.value?.items ?? [])
const summary = computed(() => ({
  total: items.value.length,
  active: items.value.filter((item) => item.status === 'ACTIVE').length,
  disabled: items.value.filter((item) => item.status === 'DISABLED').length,
  organized: items.value.filter((item) => Boolean(item.organizationSummary)).length,
}))

const grantItems = computed(() => roleGrants.data.value?.items ?? [])
const roleNameById = computed(
  () => new Map((activeRoles.data.value ?? []).map((role) => [role.roleId, role.roleName])),
)
const roleOptions = computed(() =>
  (activeRoles.data.value ?? []).map((role) => ({
    value: role.roleId,
    label: `${role.roleName} · ${role.roleCode}`,
  })),
)
const canSubmitGrant = computed(() =>
  Boolean(
    grantRoleId.value &&
      (grantScopeType.value === 'TENANT' || grantScopeRef.value.trim()) &&
      grantReason.value.trim(),
  ),
)

const scopeTypeOptions: Array<{ value: RoleGrantScopeType; label: string; hint: string }> = [
  { value: 'TENANT', label: '全平台', hint: '固定为当前租户 tenant-local，授权覆盖租户内全部项目与网点。' },
  { value: 'PROJECT', label: '项目', hint: '填写项目标识，授权仅在该项目范围内生效。' },
  { value: 'NETWORK', label: '网点', hint: '填写网点标识，授权仅在该网点范围内生效。' },
  { value: 'REGION', label: '区域', hint: '填写标准行政区域编码，例如 310115。' },
]
const grantStatusMeta: Record<RoleGrantStatus, { tone: 'green' | 'orange' | 'red' | 'gray'; label: string }> = {
  PENDING_APPROVAL: { tone: 'orange', label: '待审批' },
  ACTIVE: { tone: 'green', label: '生效中' },
  REJECTED: { tone: 'red', label: '已拒绝' },
  REVOKED: { tone: 'gray', label: '已回收' },
}

watch(grantScopeType, (scopeType) => {
  // TENANT 范围的 scopeRef 由平台固定；切出时清空，避免把旧范围标识带入新类型。
  grantScopeRef.value = scopeType === 'TENANT' ? 'tenant-local' : ''
})

const scopeRefHint = computed(
  () => scopeTypeOptions.find((option) => option.value === grantScopeType.value)?.hint ?? '',
)

function scopeTypeLabel(scopeType: RoleGrantScopeType) {
  return scopeTypeOptions.find((option) => option.value === scopeType)?.label ?? scopeType
}

function grantRoleName(grant: RoleGrant) {
  return roleNameById.value.get(grant.roleId) ?? grant.roleCode
}

function search() {
  filters.value = {
    query: keyword.value.trim() || undefined,
    status: status.value,
  }
}

function resetForm() {
  displayName.value = ''
  employeeNumber.value = ''
  personaType.value = 'INTERNAL_EMPLOYEE'
  createCommand.reset()
}

function closeCreate() {
  createOpen.value = false
  resetForm()
}

async function createUser() {
  const normalizedName = displayName.value.trim()
  if (!normalizedName) return
  await createCommand.mutateAsync({
    displayName: normalizedName,
    employeeNumber: employeeNumber.value.trim() || null,
    personaType: personaType.value,
  })
  createdName.value = normalizedName
  closeCreate()
}

function openGrantForm() {
  grantFormOpen.value = true
  grantRoleId.value = undefined
  grantScopeType.value = 'TENANT'
  grantScopeRef.value = 'tenant-local'
  grantReason.value = ''
  revokeTarget.value = undefined
  grantCommand.reset()
}

function closeGrantForm() {
  grantFormOpen.value = false
  grantCommand.reset()
}

function openRevoke(grant: RoleGrant) {
  revokeTarget.value = grant
  revokeReason.value = ''
  grantFormOpen.value = false
  revokeCommand.reset()
}

async function submitGrant() {
  const roleId = grantRoleId.value
  if (!roleId || !canSubmitGrant.value) return
  const roleName = roleNameById.value.get(roleId) ?? '所选角色'
  const scopeRef = grantScopeType.value === 'TENANT' ? 'tenant-local' : grantScopeRef.value.trim()
  await grantCommand.mutateAsync({
    roleId,
    scopeType: grantScopeType.value,
    scopeRef,
    requestReason: grantReason.value.trim(),
  })
  message.success(`已授予角色“${roleName}”并生效。`)
  grantFormOpen.value = false
}

async function confirmRevoke() {
  const target = revokeTarget.value
  const reason = revokeReason.value.trim()
  if (!target || !reason) return
  await revokeCommand.mutateAsync({ grantId: target.grantId, version: target.version, reason })
  message.success(`已回收角色“${grantRoleName(target)}”的授权，立即失效。`)
  revokeTarget.value = undefined
}

function closeDetail() {
  selectedUser.value = undefined
  grantFormOpen.value = false
  revokeTarget.value = undefined
  grantCommand.reset()
  revokeCommand.reset()
}
</script>

<template>
  <div class="user-directory-page">
    <div class="page-heading inline">
      <div>
        <p class="breadcrumb">
          系统管理 / 用户管理
        </p>
        <h1>用户管理</h1>
        <p>管理平台用户主体、组织归属和角色授权。登录身份由 Keycloak 或企业身份提供方维护。</p>
      </div>
      <div class="heading-actions">
        <Button
          type="primary"
          @click="createOpen = true"
        >
          <UserAddOutlined /> 新建用户
        </Button>
      </div>
    </div>

    <div
      v-if="createdName"
      class="product-notice success"
    >
      已登记用户“{{ createdName }}”。请继续配置组织任职、角色授权与 OIDC 身份绑定。
    </div>

    <PageError
      v-if="directory.isError.value"
      :detail="directory.error.value?.message ?? '用户目录加载失败'"
    />
    <template v-else>
      <section
        class="user-summary-grid"
        aria-label="用户概览"
      >
        <div><span>本页用户</span><strong>{{ summary.total }}</strong></div>
        <div><span>启用账号</span><strong>{{ summary.active }}</strong></div>
        <div><span>停用账号</span><strong>{{ summary.disabled }}</strong></div>
        <div><span>已配置组织任职</span><strong>{{ summary.organized }}</strong></div>
      </section>

      <section class="directory-panel user-directory-panel">
        <form
          class="user-filter-bar"
          @submit.prevent="search"
        >
          <Input
            v-model:value="keyword"
            placeholder="搜索姓名或工号"
            allow-clear
          >
            <template #prefix>
              <SearchOutlined />
            </template>
          </Input>
          <Select
            v-model:value="status"
            placeholder="用户状态"
            allow-clear
            :options="[{ value: 'ACTIVE', label: '启用' }, { value: 'DISABLED', label: '停用' }]"
          />
          <Button
            html-type="submit"
            type="primary"
          >
            查询
          </Button>
          <Button
            type="text"
            @click="keyword = ''; status = undefined; search()"
          >
            重置
          </Button>
        </form>

        <div class="data-table-wrap">
          <table class="business-table user-directory-table">
            <thead><tr><th>姓名</th><th>工号 / 登录账号</th><th>所属组织</th><th>角色摘要</th><th>最近登录</th><th>类型</th><th>状态</th><th>更新时间</th><th>操作</th></tr></thead>
            <tbody>
              <tr
                v-for="row in items"
                :key="row.id"
              >
                <td><strong>{{ row.displayName }}</strong></td>
                <td>{{ row.employeeNumber || '未设置' }}</td>
                <td>{{ row.organizationSummary || '尚未配置' }}</td>
                <td>{{ row.roleSummary || '尚未授权' }}</td>
                <td>{{ row.lastLoginAt ? formatDateTime(row.lastLoginAt) : '尚未登录' }}</td>
                <td>{{ row.type === 'USER' ? '人员账号' : '服务账号' }}</td>
                <td>
                  <StatusPill
                    :tone="row.status === 'ACTIVE' ? 'green' : 'gray'"
                    :label="row.status === 'ACTIVE' ? '启用' : '停用'"
                  />
                </td>
                <td>{{ formatDateTime(row.updatedAt) }}</td>
                <td>
                  <button
                    class="table-link"
                    type="button"
                    @click="selectedUser = row"
                  >
                    查看
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
          <div
            v-if="directory.isLoading.value"
            class="table-loading"
          >
            正在加载用户目录…
          </div>
          <div
            v-else-if="!items.length"
            class="empty-state"
          >
            <h3>暂无符合条件的用户</h3><p>调整查询条件，或登记第一个平台用户。</p>
          </div>
        </div>
        <footer class="table-footer">
          <span>统计时间 {{ directory.data.value?.asOf ? formatDateTime(directory.data.value.asOf) : '—' }}</span>
          <Button
            v-if="directory.data.value?.nextCursor"
            @click="filters = { ...filters, cursor: directory.data.value?.nextCursor ?? undefined }"
          >
            下一页
          </Button>
        </footer>
      </section>
    </template>

    <Drawer
      :open="createOpen"
      width="480"
      title="新建用户"
      placement="right"
      @close="closeCreate"
    >
      <div class="create-user-intro">
        ServiceOS 不保存登录密码。登记用户后，再为其配置组织任职、角色授权和 Keycloak 身份绑定。
      </div>
      <PageError
        v-if="createCommand.isError.value"
        :detail="createCommand.error.value?.message ?? '用户登记失败'"
      />
      <div class="create-user-form">
        <label><span>姓名</span><Input
          v-model:value="displayName"
          placeholder="例如：王晓梅"
          :maxlength="200"
        /></label>
        <label><span>工号 / 登录账号（可选）</span><Input
          v-model:value="employeeNumber"
          placeholder="例如：SO-OP-001"
          :maxlength="128"
        /></label>
        <label>
          <span>初始身份类型</span>
          <Select
            v-model:value="personaType"
            :options="[
              { value: 'INTERNAL_EMPLOYEE', label: '平台内部员工' },
              { value: 'NETWORK_MEMBER', label: '网点成员' },
              { value: 'TECHNICIAN', label: '服务师傅' },
              { value: 'SERVICE_ACCOUNT', label: '系统服务账号' },
            ]"
          />
        </label>
      </div>
      <template #footer>
        <div class="drawer-footer">
          <Button @click="closeCreate">
            取消
          </Button>
          <Button
            type="primary"
            :disabled="!displayName.trim()"
            :loading="createCommand.isPending.value"
            @click="createUser"
          >
            登记用户
          </Button>
        </div>
      </template>
    </Drawer>

    <Drawer
      :open="Boolean(selectedUser)"
      width="560"
      title="用户信息"
      placement="right"
      @close="closeDetail"
    >
      <template v-if="selectedUser">
        <div class="user-detail-heading">
          <div class="user-detail-avatar">
            {{ selectedUser.displayName.slice(0, 1) }}
          </div>
          <div>
            <h2>{{ selectedUser.displayName }}</h2>
            <p>{{ selectedUser.employeeNumber || '尚未设置工号或登录账号' }}</p>
          </div>
          <StatusPill
            :tone="selectedUser.status === 'ACTIVE' ? 'green' : 'gray'"
            :label="selectedUser.status === 'ACTIVE' ? '启用' : '停用'"
          />
        </div>
        <dl class="user-detail-list">
          <div><dt>账号类型</dt><dd>{{ selectedUser.type === 'USER' ? '人员账号' : '服务账号' }}</dd></div>
          <div><dt>所属组织</dt><dd>{{ selectedUser.organizationSummary || '尚未配置' }}</dd></div>
          <div><dt>角色授权</dt><dd>{{ selectedUser.roleSummary || '尚未授权' }}</dd></div>
          <div><dt>最近登录</dt><dd>{{ selectedUser.lastLoginAt ? formatDateTime(selectedUser.lastLoginAt) : '尚未登录' }}</dd></div>
          <div><dt>登记时间</dt><dd>{{ formatDateTime(selectedUser.createdAt) }}</dd></div>
          <div><dt>更新时间</dt><dd>{{ formatDateTime(selectedUser.updatedAt) }}</dd></div>
        </dl>

        <section
          class="role-grant-section"
          aria-label="角色授权"
        >
          <div class="role-grant-heading">
            <div>
              <h3>角色授权</h3>
              <p>授予需申请并审批，回收后立即失效，全程由服务端留痕审计。</p>
            </div>
            <Button
              v-if="!grantFormOpen"
              size="small"
              type="primary"
              @click="openGrantForm"
            >
              新增授权
            </Button>
          </div>

          <form
            v-if="grantFormOpen"
            class="grant-form"
            @submit.prevent="submitGrant"
          >
            <label>
              <span>角色</span>
              <Select
                v-model:value="grantRoleId"
                show-search
                option-filter-prop="label"
                :options="roleOptions"
                :loading="activeRoles.isLoading.value"
                placeholder="选择启用中的角色"
              />
            </label>
            <label>
              <span>授权范围</span>
              <Select
                v-model:value="grantScopeType"
                :options="scopeTypeOptions.map((option) => ({ value: option.value, label: option.label }))"
              />
            </label>
            <label>
              <span>范围标识</span>
              <Input
                v-model:value="grantScopeRef"
                :disabled="grantScopeType === 'TENANT'"
                :maxlength="128"
                placeholder="scopeRef"
              />
              <small class="field-hint">{{ scopeRefHint }}</small>
            </label>
            <label>
              <span>申请理由</span>
              <Input.TextArea
                v-model:value="grantReason"
                :maxlength="500"
                :rows="3"
                show-count
                placeholder="说明本次授权的业务原因（必填）"
              />
            </label>
            <p class="grant-form-note">
              提交后系统自动完成申请与审批；高风险角色按服务端策略可能要求他人审批，届时授权保持“待审批”状态。
            </p>
            <PageError
              v-if="grantCommand.isError.value"
              :detail="grantCommand.error.value?.message ?? '角色授权失败'"
            />
            <div class="drawer-actions">
              <Button @click="closeGrantForm">
                取消
              </Button>
              <Button
                type="primary"
                html-type="submit"
                :loading="grantCommand.isPending.value"
                :disabled="!canSubmitGrant"
              >
                确认授予
              </Button>
            </div>
          </form>

          <PageError
            v-if="roleGrants.isError.value"
            :detail="roleGrants.error.value?.message ?? '角色授权加载失败'"
          />
          <div
            v-else-if="roleGrants.isLoading.value"
            class="table-loading"
          >
            正在加载角色授权…
          </div>
          <div
            v-else-if="!grantItems.length"
            class="empty-state"
          >
            <h3>暂无角色授权</h3>
            <p>通过“新增授权”为该用户授予第一个角色。</p>
          </div>
          <table
            v-else
            class="business-table role-grant-table"
          >
            <thead><tr><th>角色</th><th>授权范围</th><th>状态</th><th>生效时间</th><th>操作</th></tr></thead>
            <tbody>
              <tr
                v-for="grant in grantItems"
                :key="grant.grantId"
              >
                <td><strong>{{ grantRoleName(grant) }}</strong><small>{{ grant.roleCode }}</small></td>
                <td>{{ scopeTypeLabel(grant.scopeType) }}<small>{{ grant.scopeRef }}</small></td>
                <td>
                  <StatusPill
                    :tone="grantStatusMeta[grant.grantStatus].tone"
                    :label="grantStatusMeta[grant.grantStatus].label"
                  />
                </td>
                <td>
                  {{ formatDateTime(grant.validFrom) }}
                  <small v-if="grant.validTo">至 {{ formatDateTime(grant.validTo) }}</small>
                </td>
                <td>
                  <button
                    v-if="grant.grantStatus === 'ACTIVE'"
                    class="table-link"
                    type="button"
                    :disabled="revokeCommand.isPending.value"
                    @click="openRevoke(grant)"
                  >
                    回收
                  </button>
                  <span v-else>—</span>
                </td>
              </tr>
            </tbody>
          </table>

          <div
            v-if="revokeTarget"
            class="revoke-confirm"
          >
            <p>
              确认回收“{{ grantRoleName(revokeTarget) }}”在{{ scopeTypeLabel(revokeTarget.scopeType) }}范围（{{ revokeTarget.scopeRef }}）的授权？
              回收后立即失效，操作将写入审计。
            </p>
            <Input.TextArea
              v-model:value="revokeReason"
              :maxlength="500"
              :rows="3"
              show-count
              placeholder="回收理由（必填）"
            />
            <PageError
              v-if="revokeCommand.isError.value"
              :detail="revokeCommand.error.value?.message ?? '回收授权失败'"
            />
            <div class="drawer-actions">
              <Button @click="revokeTarget = undefined">
                取消
              </Button>
              <Button
                type="primary"
                danger
                :loading="revokeCommand.isPending.value"
                :disabled="!revokeReason.trim()"
                @click="confirmRevoke"
              >
                确认回收
              </Button>
            </div>
          </div>
        </section>

        <div class="product-notice neutral">
          组织任职与 OIDC 身份绑定将在后续用户工作区中集中配置；本页不保存密码，也不使用前端菜单代替服务端授权。
        </div>
      </template>
    </Drawer>
  </div>
</template>
