<script setup lang="ts">
import type { AdminUserDirectoryItem, PrincipalPersonaType } from '@serviceos/api-client'
import {
  Button,
  Drawer,
  Input,
  SearchOutlined,
  Select,
  UserAddOutlined,
} from '@serviceos/design-system'
import { computed, ref } from 'vue'
import PageError from '../../../components/PageError.vue'
import StatusPill from '../../../components/StatusPill.vue'
import { formatDateTime } from '../../../presenters/work-order'
import { useRegisterUserCommand } from '../commands/use-register-user-command'
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

const items = computed(() => directory.data.value?.items ?? [])
const summary = computed(() => ({
  total: items.value.length,
  active: items.value.filter((item) => item.status === 'ACTIVE').length,
  disabled: items.value.filter((item) => item.status === 'DISABLED').length,
  organized: items.value.filter((item) => Boolean(item.organizationSummary)).length,
}))

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
</script>

<template>
  <div class="user-directory-page">
    <div class="page-heading inline">
      <div>
        <p class="breadcrumb">系统管理 / 用户管理</p>
        <h1>用户管理</h1>
        <p>管理平台用户主体、组织归属和角色授权。登录身份由 Keycloak 或企业身份提供方维护。</p>
      </div>
      <div class="heading-actions">
        <Button type="primary" @click="createOpen = true"><UserAddOutlined /> 新建用户</Button>
      </div>
    </div>

    <div v-if="createdName" class="product-notice success">
      已登记用户“{{ createdName }}”。请继续配置组织任职、角色授权与 OIDC 身份绑定。
    </div>

    <PageError v-if="directory.isError.value" :detail="directory.error.value?.message ?? '用户目录加载失败'" />
    <template v-else>
      <section class="user-summary-grid" aria-label="用户概览">
        <div><span>本页用户</span><strong>{{ summary.total }}</strong></div>
        <div><span>启用账号</span><strong>{{ summary.active }}</strong></div>
        <div><span>停用账号</span><strong>{{ summary.disabled }}</strong></div>
        <div><span>已配置组织任职</span><strong>{{ summary.organized }}</strong></div>
      </section>

      <section class="directory-panel user-directory-panel">
        <form class="user-filter-bar" @submit.prevent="search">
          <Input v-model:value="keyword" placeholder="搜索姓名或工号" allow-clear>
            <template #prefix><SearchOutlined /></template>
          </Input>
          <Select
            v-model:value="status"
            placeholder="用户状态"
            allow-clear
            :options="[{ value: 'ACTIVE', label: '启用' }, { value: 'DISABLED', label: '停用' }]"
          />
          <Button html-type="submit" type="primary">查询</Button>
          <Button type="text" @click="keyword = ''; status = undefined; search()">重置</Button>
        </form>

        <div class="data-table-wrap">
          <table class="business-table user-directory-table">
            <thead><tr><th>姓名</th><th>工号 / 登录账号</th><th>所属组织</th><th>角色摘要</th><th>最近登录</th><th>类型</th><th>状态</th><th>更新时间</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="row in items" :key="row.id">
                <td><strong>{{ row.displayName }}</strong></td>
                <td>{{ row.employeeNumber || '未设置' }}</td>
                <td>{{ row.organizationSummary || '尚未配置' }}</td>
                <td>{{ row.roleSummary || '尚未授权' }}</td>
                <td>{{ row.lastLoginAt ? formatDateTime(row.lastLoginAt) : '尚未登录' }}</td>
                <td>{{ row.type === 'USER' ? '人员账号' : '服务账号' }}</td>
                <td><StatusPill :tone="row.status === 'ACTIVE' ? 'green' : 'gray'" :label="row.status === 'ACTIVE' ? '启用' : '停用'" /></td>
                <td>{{ formatDateTime(row.updatedAt) }}</td>
                <td><button class="table-link" type="button" @click="selectedUser = row">查看</button></td>
              </tr>
            </tbody>
          </table>
          <div v-if="directory.isLoading.value" class="table-loading">正在加载用户目录…</div>
          <div v-else-if="!items.length" class="empty-state"><h3>暂无符合条件的用户</h3><p>调整查询条件，或登记第一个平台用户。</p></div>
        </div>
        <footer class="table-footer">
          <span>统计时间 {{ directory.data.value?.asOf ? formatDateTime(directory.data.value.asOf) : '—' }}</span>
          <Button v-if="directory.data.value?.nextCursor" @click="filters = { ...filters, cursor: directory.data.value?.nextCursor ?? undefined }">下一页</Button>
        </footer>
      </section>
    </template>

    <Drawer :open="createOpen" width="480" title="新建用户" placement="right" @close="closeCreate">
      <div class="create-user-intro">
        ServiceOS 不保存登录密码。登记用户后，再为其配置组织任职、角色授权和 Keycloak 身份绑定。
      </div>
      <PageError v-if="createCommand.isError.value" :detail="createCommand.error.value?.message ?? '用户登记失败'" />
      <div class="create-user-form">
        <label><span>姓名</span><Input v-model:value="displayName" placeholder="例如：王晓梅" :maxlength="200" /></label>
        <label><span>工号 / 登录账号（可选）</span><Input v-model:value="employeeNumber" placeholder="例如：SO-OP-001" :maxlength="128" /></label>
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
          <Button @click="closeCreate">取消</Button>
          <Button type="primary" :disabled="!displayName.trim()" :loading="createCommand.isPending.value" @click="createUser">登记用户</Button>
        </div>
      </template>
    </Drawer>

    <Drawer
      :open="Boolean(selectedUser)"
      width="520"
      title="用户信息"
      placement="right"
      @close="selectedUser = undefined"
    >
      <template v-if="selectedUser">
        <div class="user-detail-heading">
          <div class="user-detail-avatar">{{ selectedUser.displayName.slice(0, 1) }}</div>
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
        <div class="product-notice neutral">
          组织任职、角色授权和 OIDC 身份绑定将在后续用户工作区中集中配置；本页不保存密码，也不使用前端菜单代替服务端授权。
        </div>
      </template>
    </Drawer>
  </div>
</template>
