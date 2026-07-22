<script setup lang="ts">
import type { AuthorizationRole } from '@serviceos/api-client'
import { Input, SearchOutlined } from '@serviceos/design-system'
import { computed, ref } from 'vue'
import PageError from '../../../components/PageError.vue'
import StatusPill from '../../../components/StatusPill.vue'
import { formatDateTime } from '../../../presenters/work-order'
import { useCapabilityDirectoryQuery, useRoleDirectoryQuery } from '../queries/use-role-directory-query'

const keyword = ref('')
const selectedRole = ref<AuthorizationRole>()
const roles = useRoleDirectoryQuery()
const capabilities = useCapabilityDirectoryQuery()
const capabilityMap = computed(() => new Map((capabilities.data.value ?? []).map((item) => [item.capabilityCode, item])))
const items = computed(() => {
  const normalized = keyword.value.trim().toLowerCase()
  if (!normalized) return roles.data.value?.items ?? []
  return (roles.data.value?.items ?? []).filter((item) =>
    `${item.roleName} ${item.roleCode} ${item.description ?? ''}`.toLowerCase().includes(normalized),
  )
})
const summary = computed(() => ({
  total: roles.data.value?.items.length ?? 0,
  active: roles.data.value?.items.filter((item) => item.roleStatus === 'ACTIVE').length ?? 0,
  templates: roles.data.value?.items.filter((item) => item.roleKind === 'PLATFORM_TEMPLATE').length ?? 0,
  highRisk: roles.data.value?.items.filter((role) => role.capabilityCodes.some((code) => {
    const risk = capabilityMap.value.get(code)?.riskLevel
    return risk === 'HIGH' || risk === 'CRITICAL'
  })).length ?? 0,
}))

function capabilityLabel(code: string) {
  return capabilityMap.value.get(code)?.capabilityName ?? '能力名称缺失'
}

function capabilityTone(code: string) {
  const risk = capabilityMap.value.get(code)?.riskLevel
  return risk === 'CRITICAL' ? 'red' : risk === 'HIGH' ? 'orange' : 'blue'
}
</script>

<template>
  <div class="role-directory-page">
    <div class="page-heading inline">
      <div>
        <p class="breadcrumb">系统管理 / 角色与授权</p>
        <h1>角色与授权</h1>
        <p>查看平台角色及其稳定能力组合。真正的授权范围由 RoleGrant 与服务端策略共同决定。</p>
      </div>
    </div>

    <PageError v-if="roles.isError.value || capabilities.isError.value" :detail="roles.error.value?.message ?? capabilities.error.value?.message ?? '角色目录加载失败'" />
    <template v-else>
      <section class="user-summary-grid" aria-label="角色概览">
        <div><span>全部角色</span><strong>{{ summary.total }}</strong></div>
        <div><span>启用角色</span><strong>{{ summary.active }}</strong></div>
        <div><span>平台角色模板</span><strong>{{ summary.templates }}</strong></div>
        <div><span>含高风险能力</span><strong>{{ summary.highRisk }}</strong></div>
      </section>

      <section class="directory-panel">
        <div class="role-filter-bar">
          <Input v-model:value="keyword" placeholder="搜索角色名称、编码或说明" allow-clear>
            <template #prefix><SearchOutlined /></template>
          </Input>
        </div>
        <div class="role-card-grid">
          <button v-for="role in items" :key="role.roleId" type="button" class="role-card" @click="selectedRole = role">
            <div class="role-card-heading">
              <div><strong>{{ role.roleName }}</strong><span>{{ role.roleCode }}</span></div>
              <StatusPill :tone="role.roleStatus === 'ACTIVE' ? 'green' : 'gray'" :label="role.roleStatus === 'ACTIVE' ? '启用' : '停用'" />
            </div>
            <p>{{ role.description || '暂无角色说明' }}</p>
            <footer><span>{{ role.roleKind === 'PLATFORM_TEMPLATE' ? '平台角色模板' : '租户自定义角色' }}</span><b>{{ role.capabilityCodes.length }} 项能力</b></footer>
          </button>
        </div>
        <div v-if="roles.isLoading.value" class="table-loading">正在加载角色目录…</div>
        <div v-else-if="!items.length" class="empty-state"><h3>暂无符合条件的角色</h3><p>请调整搜索条件。</p></div>
      </section>

      <section v-if="selectedRole" class="role-detail-panel">
        <div class="role-detail-header">
          <div><p>当前角色</p><h2>{{ selectedRole.roleName }}</h2><span>{{ selectedRole.description || '暂无角色说明' }}</span></div>
          <button type="button" class="table-link" @click="selectedRole = undefined">关闭</button>
        </div>
        <div class="role-meta-grid">
          <div><span>角色编码</span><strong>{{ selectedRole.roleCode }}</strong></div>
          <div><span>角色类型</span><strong>{{ selectedRole.roleKind === 'PLATFORM_TEMPLATE' ? '平台角色模板' : '租户自定义角色' }}</strong></div>
          <div><span>更新时间</span><strong>{{ formatDateTime(selectedRole.updatedAt) }}</strong></div>
        </div>
        <div class="capability-list">
          <div v-for="code in selectedRole.capabilityCodes" :key="code">
            <StatusPill :tone="capabilityTone(code)" :label="capabilityLabel(code)" />
            <span>{{ code }}</span>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>
