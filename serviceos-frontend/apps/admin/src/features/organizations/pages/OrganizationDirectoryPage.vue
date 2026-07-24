<script setup lang="ts">
import { computed, ref, watchEffect } from 'vue'
import PageError from '../../../components/PageError.vue'
import StatusPill from '../../../components/StatusPill.vue'
import { formatDateTime } from '../../../presenters/work-order'
import { useOrganizationDetailQuery, useOrganizationDirectoryQuery } from '../queries/use-organization-directory-query'

const directory = useOrganizationDirectoryQuery()
const selectedId = ref<string>()
const detail = useOrganizationDetailQuery(selectedId)

watchEffect(() => {
  if (!selectedId.value && directory.data.value?.items.length) selectedId.value = directory.data.value.items[0]?.id
})

const selectedOrganization = computed(() => detail.data.value?.organization)
const units = computed(() => detail.data.value?.units ?? [])
const unitNames = computed(() => new Map(units.value.map((unit) => [unit.id, unit.unitName])))
</script>

<template>
  <div class="organization-page">
    <div class="page-heading inline">
      <div>
        <h1>组织架构</h1>
      </div>
    </div>

    <PageError v-if="directory.isError.value" :detail="directory.error.value?.message ?? '组织目录加载失败'" />
    <div v-else class="organization-workspace">
      <aside class="organization-list-panel">
        <header><strong>企业组织</strong><span>{{ directory.data.value?.items.length ?? 0 }} 个</span></header>
        <button
          v-for="organization in directory.data.value?.items ?? []"
          :key="organization.id"
          type="button"
          :class="['organization-list-item', { active: organization.id === selectedId }]"
          @click="selectedId = organization.id"
        >
          <span class="organization-mark">{{ organization.name.slice(0, 1) }}</span>
          <span><strong>{{ organization.name }}</strong><small>{{ organization.code }}</small></span>
          <StatusPill :tone="organization.status === 'ACTIVE' ? 'green' : 'gray'" :label="organization.status === 'ACTIVE' ? '启用' : '停用'" />
        </button>
        <div v-if="directory.isLoading.value" class="table-loading">正在加载组织目录…</div>
      </aside>

      <main class="organization-detail-panel">
        <PageError v-if="detail.isError.value" :detail="detail.error.value?.message ?? '组织详情加载失败'" />
        <template v-else-if="selectedOrganization">
          <header class="organization-detail-heading">
            <div>
              <p>{{ selectedOrganization.authorityMode === 'LOCAL' ? 'ServiceOS 本地维护' : '外部组织目录同步' }}</p>
              <h2>{{ selectedOrganization.name }}</h2>
              <span>组织编码 {{ selectedOrganization.code }} · 更新于 {{ formatDateTime(selectedOrganization.updatedAt) }}</span>
            </div>
            <StatusPill :tone="selectedOrganization.status === 'ACTIVE' ? 'green' : 'gray'" :label="selectedOrganization.status === 'ACTIVE' ? '运行中' : '已停用'" />
          </header>

          <section class="organization-metrics">
            <div><span>组织单元</span><strong>{{ units.length }}</strong></div>
            <div><span>启用单元</span><strong>{{ units.filter((unit) => unit.status === 'ACTIVE').length }}</strong></div>
            <div><span>根级单元</span><strong>{{ units.filter((unit) => !unit.parentUnitId).length }}</strong></div>
          </section>

          <section class="organization-unit-section">
            <div class="section-title"><div><h3>部门与组织单元</h3><p>展示当前权威组织层级；移动和新增单元必须通过服务端并发版本校验。</p></div></div>
            <div class="data-table-wrap">
              <table class="business-table organization-unit-table">
                <thead><tr><th>组织单元</th><th>单元编码</th><th>上级单元</th><th>数据来源</th><th>状态</th><th>更新时间</th></tr></thead>
                <tbody>
                  <tr v-for="unit in units" :key="unit.id">
                    <td><strong>{{ unit.unitName }}</strong></td>
                    <td>{{ unit.unitCode }}</td>
                    <td>{{ unit.parentUnitId ? unitNames.get(unit.parentUnitId) || '上级名称缺失' : '根级单元' }}</td>
                    <td>{{ unit.sourceSystem || 'ServiceOS' }}</td>
                    <td><StatusPill :tone="unit.status === 'ACTIVE' ? 'green' : 'gray'" :label="unit.status === 'ACTIVE' ? '启用' : '停用'" /></td>
                    <td>{{ formatDateTime(unit.updatedAt) }}</td>
                  </tr>
                </tbody>
              </table>
              <div v-if="detail.isLoading.value" class="table-loading">正在加载组织层级…</div>
              <div v-else-if="!units.length" class="empty-state"><h3>尚未建立组织单元</h3><p>请先根据真实管理关系建立第一个部门。</p></div>
            </div>
          </section>
        </template>
      </main>
    </div>
  </div>
</template>
