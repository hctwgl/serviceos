<script setup lang="ts">
import { computed, ref } from 'vue'
import { Input, SearchOutlined } from '@serviceos/design-system'
import PageError from '../../../components/PageError.vue'
import StatusPill from '../../../components/StatusPill.vue'
import { presentConfigurationStatus, presentProjectPeriod, presentProjectStatus } from '../presenters/client-project-directory'
import { useClientProjectDirectoryQuery } from '../queries/use-client-project-directory-query'

const directory = useClientProjectDirectoryQuery()
const keyword = ref('')
const projects = computed(() => {
  const normalized = keyword.value.trim().toLowerCase()
  const items = directory.data.value?.projects ?? []
  if (!normalized) return items
  return items.filter((item) => `${item.projectName} ${item.clientName ?? ''} ${item.regionNames.join(' ')}`.toLowerCase().includes(normalized))
})
const activeCount = computed(() => directory.data.value?.projects.filter((item) => item.status === 'ACTIVE').length ?? 0)
const draftCount = computed(() => directory.data.value?.projects.filter((item) => item.configurationStatus === 'DRAFT' || item.configurationStatus === 'UNPUBLISHED_CHANGES').length ?? 0)
</script>

<template>
  <div class="resource-page">
    <div class="page-heading inline"><div><p class="breadcrumb">客户与项目 / 项目管理</p><h1>项目管理</h1><p>查看项目服务周期、区域范围、参与网点及履约配置准备情况。</p></div></div>
    <PageError v-if="directory.isError.value" :detail="directory.error.value?.message ?? '项目目录加载失败'" />
    <template v-else>
      <section class="resource-summary-grid project-summary"><div><span>全部项目</span><strong>{{ directory.data.value?.projects.length ?? 0 }}</strong></div><div><span>运行中</span><strong>{{ activeCount }}</strong></div><div><span>存在草稿</span><strong>{{ draftCount }}</strong></div><div><span>合作客户</span><strong>{{ directory.data.value?.clients.filter((item) => item.status === 'ACTIVE').length ?? 0 }}</strong></div></section>
      <section class="directory-panel">
        <div class="resource-filter"><Input v-model:value="keyword" placeholder="搜索项目、客户或服务区域" allow-clear><template #prefix><SearchOutlined /></template></Input></div>
        <div class="data-table-wrap">
          <table class="business-table project-table">
            <thead><tr><th>项目</th><th>客户</th><th>服务周期</th><th>服务区域</th><th>参与网点</th><th>履约配置</th><th>项目状态</th></tr></thead>
            <tbody><tr v-for="item in projects" :key="item.id" :class="{ 'data-incomplete-row': !item.dataComplete }"><td><RouterLink class="table-link" :to="`/projects/${item.id}`"><strong>{{ item.projectName }}</strong></RouterLink><small>{{ item.projectCode }}</small><em v-if="!item.dataComplete">{{ item.dataProblem }}</em></td><td>{{ item.clientName ?? '数据不完整' }}</td><td>{{ presentProjectPeriod(item.startsOn, item.endsOn) }}</td><td>{{ item.regionNames.join('、') || '数据不完整' }}</td><td>{{ item.networkCount }} 个</td><td><StatusPill :tone="presentConfigurationStatus(item.configurationStatus).tone" :label="presentConfigurationStatus(item.configurationStatus).label" /></td><td><StatusPill :tone="presentProjectStatus(item.status).tone" :label="presentProjectStatus(item.status).label" /></td></tr></tbody>
          </table>
          <div v-if="directory.isLoading.value" class="table-loading">正在加载项目…</div>
          <div v-else-if="!projects.length" class="empty-state"><h3>暂无符合条件的项目</h3><p>请调整搜索条件。</p></div>
        </div>
      </section>
    </template>
  </div>
</template>
