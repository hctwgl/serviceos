<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { Alert, Button, Input, Select, Space, Table, Tag } from 'ant-design-vue'
import ListPageLayout from '../patterns/templates/ListPageLayout.vue'
import DedicatedFlowLayout from '../patterns/templates/DedicatedFlowLayout.vue'
import SummaryStrip, { type SummaryStripItem } from '../patterns/SummaryStrip.vue'
import {
  listAdminUserDirectory,
  registerSecurityPrincipal,
  type AdminUserDirectoryPage,
  type PrincipalPersona,
} from '../api/securityPrincipals'
import { safeAccessDeniedMessage } from '../api/client'
import { statusLabel } from '../product/statusLabels'
import { formatDateTimeDisplay } from '../presentation/date-time.presenter'

const router = useRouter()
const loading = ref(false)
const busy = ref(false)
const error = ref<string | null>(null)
const createError = ref<string | null>(null)
const query = ref('')
const status = ref<string | undefined>(undefined)
const page = ref<AdminUserDirectoryPage | null>(null)
const createOpen = ref(false)
const createDisplayName = ref('')
const createEmployeeNumber = ref('')
const createPersona = ref<PrincipalPersona['personaType'] | undefined>('INTERNAL_EMPLOYEE')

const summaryItems = computed<SummaryStripItem[]>(() => {
  const items = page.value?.items ?? []
  const active = items.filter((item) => item.status === 'ACTIVE').length
  const disabled = items.filter((item) => item.status === 'DISABLED').length
  const withOrg = items.filter((item) => item.organizationSummary && item.organizationSummary !== '无任职').length
  return [
    { key: 'page', label: '本页用户', value: String(items.length) },
    { key: 'active', label: '启用', value: String(active), tone: 'success' },
    { key: 'disabled', label: '停用', value: String(disabled), tone: disabled ? 'warning' : 'default' },
    { key: 'org', label: '有组织任职', value: String(withOrg), tone: 'info' },
  ]
})

const columns = [
  { title: '姓名', dataIndex: 'displayName', key: 'displayName' },
  { title: '登录账号 / 工号', dataIndex: 'employeeNumber', key: 'employeeNumber' },
  { title: '所属组织', key: 'organizationSummary' },
  { title: '角色摘要', key: 'roleSummary' },
  { title: '最近登录', key: 'lastLoginAt' },
  { title: '状态', key: 'status' },
  { title: '类型', key: 'type' },
  { title: '更新时间', key: 'updatedAt' },
  { title: '操作', key: 'actions' },
]

async function load(cursor?: string) {
  loading.value = true
  error.value = null
  try {
    page.value = await listAdminUserDirectory({
      query: query.value.trim() || undefined,
      status: status.value || undefined,
      cursor,
      limit: '20',
    })
  } catch (err) {
    page.value = null
    error.value = safeAccessDeniedMessage(err)
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  query.value = ''
  status.value = undefined
  void load()
}

async function submitCreate() {
  busy.value = true
  createError.value = null
  try {
    const created = await registerSecurityPrincipal({
      displayName: createDisplayName.value.trim(),
      employeeNumber: createEmployeeNumber.value.trim() || null,
      personaType: createPersona.value ?? null,
    })
    createOpen.value = false
    createDisplayName.value = ''
    createEmployeeNumber.value = ''
    createPersona.value = 'INTERNAL_EMPLOYEE'
    await router.push({ name: 'ADMIN.USER.DETAIL', params: { id: created.data.id } })
  } catch (err) {
    createError.value = safeAccessDeniedMessage(err)
  } finally {
    busy.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div data-testid="user-directory-page">
    <DedicatedFlowLayout
      v-if="createOpen"
      title="新建用户"
      description="登记平台主体档案。不设置密码；登录依赖企业 OIDC 身份绑定。"
      data-testid="user-create-flow"
    >
      <template #back>
        <Button type="text" @click="createOpen = false">返回用户列表</Button>
      </template>
      <template #sticky-secondary>
        <Button @click="createOpen = false">取消</Button>
      </template>
      <template #sticky-actions>
        <Button
          type="primary"
          :loading="busy"
          data-testid="user-create-submit"
          @click="submitCreate"
        >
          登记用户
        </Button>
      </template>
      <template #feedback>
        <Alert
          v-if="createError"
          type="error"
          show-icon
          :message="createError"
          style="margin-bottom: 12px"
        />
        <Alert
          type="info"
          show-icon
          message="本流程不保存密码，也不会伪造登录渠道。登记后请在用户详情完成组织任职、角色授权与 OIDC 绑定。"
          style="margin-bottom: 12px"
        />
      </template>
      <div class="create-form">
        <label>
          <span>姓名</span>
          <Input
            v-model:value="createDisplayName"
            aria-label="user displayName"
            placeholder="显示名"
            data-testid="user-create-display-name"
          />
        </label>
        <label>
          <span>工号 / 登录账号（可选）</span>
          <Input
            v-model:value="createEmployeeNumber"
            aria-label="user employeeNumber"
            placeholder="租户内唯一工号"
            data-testid="user-create-employee-number"
          />
        </label>
        <label>
          <span>初始 Persona</span>
          <Select
            v-model:value="createPersona"
            allow-clear
            style="width: 100%"
            aria-label="user personaType"
            data-testid="user-create-persona"
            :options="[
              { value: 'INTERNAL_EMPLOYEE', label: '内部员工' },
              { value: 'NETWORK_MEMBER', label: '网点成员' },
              { value: 'TECHNICIAN', label: '师傅' },
              { value: 'SERVICE_ACCOUNT', label: '服务账号' },
            ]"
          />
        </label>
      </div>
    </DedicatedFlowLayout>

    <ListPageLayout
      v-else
      title="用户管理"
      description="查询、查看和管理平台用户主体。创建与组织归属使用业务实体选择器，不直接输入内部 ID。"
      :loading="loading"
      :count-label="page ? `本页 ${page.items.length} 人` : undefined"
      @search="load()"
      @reset="resetFilters"
    >
      <template #primary-action>
        <Button type="primary" data-testid="user-directory-create" @click="createOpen = true">
          新建用户
        </Button>
      </template>
      <template #feedback>
        <Alert
          v-if="error"
          type="error"
          show-icon
          :message="error"
          data-testid="access-denied"
          style="margin-bottom: 12px"
        />
        <Alert
          type="info"
          show-icon
          message="组织/角色摘要与最近登录缺权时显示「—」。登录仅记录成功 OIDC 解析，不保存密码。"
          style="margin-bottom: 12px"
        />
      </template>

      <template #filters>
        <label class="filter">
          <span>姓名 / 工号</span>
          <Input
            v-model:value="query"
            allow-clear
            aria-label="user directory search"
            placeholder="搜索显示名或工号"
          />
        </label>
        <label class="filter">
          <span>状态</span>
          <Select
            v-model:value="status"
            allow-clear
            style="min-width: 160px"
            aria-label="user status filter"
            placeholder="全部"
            :options="[
              { value: 'ACTIVE', label: '启用' },
              { value: 'DISABLED', label: '停用' },
            ]"
          />
        </label>
      </template>

      <SummaryStrip :items="summaryItems" />

      <Table
        v-if="page"
        data-testid="user-directory-table"
        size="middle"
        :loading="loading"
        :pagination="false"
        :columns="columns"
        :data-source="page.items"
        :row-key="(row) => row.id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'employeeNumber'">
            {{ record.employeeNumber || '—' }}
          </template>
          <template v-else-if="column.key === 'organizationSummary'">
            <span data-testid="user-org-summary">{{ record.organizationSummary ?? '—' }}</span>
          </template>
          <template v-else-if="column.key === 'roleSummary'">
            <span data-testid="user-role-summary">{{ record.roleSummary ?? '—' }}</span>
          </template>
          <template v-else-if="column.key === 'lastLoginAt'">
            <span data-testid="user-last-login">
              {{ record.lastLoginAt ? formatDateTimeDisplay(record.lastLoginAt) : '—' }}
            </span>
          </template>
          <template v-else-if="column.key === 'status'">
            <Tag :color="record.status === 'ACTIVE' ? 'success' : 'default'">
              {{ statusLabel(record.status) }}
            </Tag>
          </template>
          <template v-else-if="column.key === 'type'">
            {{ statusLabel(record.type) }}
          </template>
          <template v-else-if="column.key === 'updatedAt'">
            {{ formatDateTimeDisplay(record.updatedAt) }}
          </template>
          <template v-else-if="column.key === 'actions'">
            <Space>
              <RouterLink :to="{ name: 'ADMIN.USER.DETAIL', params: { id: record.id } }">
                打开
              </RouterLink>
            </Space>
          </template>
        </template>
      </Table>
      <p v-else-if="!error && !loading" class="muted">暂无用户数据</p>

      <template #pagination>
        <Button
          v-if="page?.nextCursor"
          :loading="loading"
          data-testid="user-directory-next"
          @click="load(page.nextCursor!)"
        >
          下一页
        </Button>
        <span v-if="page?.asOf" class="muted">统计时间 {{ formatDateTimeDisplay(page.asOf) }}</span>
      </template>
    </ListPageLayout>
  </div>
</template>

<style scoped>
.filter,
.create-form label {
  display: grid;
  gap: 4px;
  font-size: 13px;
}
.create-form {
  display: grid;
  gap: 12px;
  max-width: 480px;
}
.muted {
  color: var(--sos-color-text-tertiary, #6b7280);
  font-size: 13px;
}
</style>
