<script setup lang="ts">
import { Input, SearchOutlined } from '@serviceos/design-system'
import { computed, ref } from 'vue'
import PageError from '../../../components/PageError.vue'
import StatusPill from '../../../components/StatusPill.vue'
import { formatDateTime } from '../../../presenters/work-order'
import { useRoleDirectoryQuery } from '../queries/use-role-directory-query'

const keyword = ref('')
type RoleItem = NonNullable<ReturnType<typeof useRoleDirectoryQuery>['data']['value']>['items'][number]
const selectedRole = ref<RoleItem>()
const roles = useRoleDirectoryQuery()
const items = computed(() => {
  const normalized = keyword.value.trim().toLowerCase()
  if (!normalized) return roles.data.value?.items ?? []
  return (roles.data.value?.items ?? []).filter((item) =>
    `${item.name} ${item.code} ${item.description ?? ''}`.toLowerCase().includes(normalized),
  )
})
const summary = computed(() => ({
  total: roles.data.value?.items.length ?? 0,
  active: roles.data.value?.items.filter((item) => item.status === 'ACTIVE').length ?? 0,
  templates: roles.data.value?.items.filter((item) => item.kind === 'PLATFORM_TEMPLATE').length ?? 0,
  highRisk: roles.data.value?.items.filter((role) => role.permissions.some((item) => item.risk === 'HIGH' || item.risk === 'CRITICAL')).length ?? 0,
}))

function permissionTone(risk: 'NORMAL' | 'HIGH' | 'CRITICAL') {
  return risk === 'CRITICAL' ? 'red' : risk === 'HIGH' ? 'orange' : 'blue'
}
</script>

<template>
  <div class="role-directory-page">
    <div class="page-heading inline">
      <div>
        <h1>角色与授权</h1>
      </div>
    </div>

    <PageError v-if="roles.isError.value" :detail="roles.error.value?.message ?? '角色目录加载失败'" />
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
          <button v-for="role in items" :key="role.id" type="button" class="role-card" @click="selectedRole = role">
            <div class="role-card-heading">
              <div><strong>{{ role.name }}</strong><span>{{ role.code }}</span></div>
              <StatusPill :tone="role.status === 'ACTIVE' ? 'green' : 'gray'" :label="role.status === 'ACTIVE' ? '启用' : '停用'" />
            </div>
            <p>{{ role.description || '暂无角色说明' }}</p>
            <footer><span>{{ role.kind === 'PLATFORM_TEMPLATE' ? '平台角色模板' : '租户自定义角色' }}</span><b>{{ role.permissions.length }} 项权限</b></footer>
          </button>
        </div>
        <div v-if="roles.isLoading.value" class="table-loading">正在加载角色目录…</div>
        <div v-else-if="!items.length" class="empty-state"><h3>暂无符合条件的角色</h3><p>请调整搜索条件。</p></div>
      </section>

      <section v-if="selectedRole" class="role-detail-panel">
        <div class="role-detail-header">
          <div><p>当前角色</p><h2>{{ selectedRole.name }}</h2><span>{{ selectedRole.description || '暂无角色说明' }}</span></div>
          <button type="button" class="table-link" @click="selectedRole = undefined">关闭</button>
        </div>
        <div class="role-meta-grid">
          <div><span>角色编码</span><strong>{{ selectedRole.code }}</strong></div>
          <div><span>角色类型</span><strong>{{ selectedRole.kind === 'PLATFORM_TEMPLATE' ? '平台角色模板' : '租户自定义角色' }}</strong></div>
          <div><span>更新时间</span><strong>{{ formatDateTime(selectedRole.updatedAt) }}</strong></div>
        </div>
        <div class="permission-list">
          <div v-for="permission in selectedRole.permissions" :key="permission.code">
            <StatusPill :tone="permissionTone(permission.risk)" :label="permission.name" />
            <span>{{ permission.code }}</span>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>
