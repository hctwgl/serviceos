<script setup lang="ts">
import { computed, ref } from 'vue'
import { Input, SearchOutlined } from '@serviceos/design-system'
import PageError from '../../../components/PageError.vue'
import StatusPill from '../../../components/StatusPill.vue'
import { formatDateTime } from '../../../presenters/work-order'
import { presentRegions } from '../presenters/resource-directory'
import { useResourceDirectoryQuery } from '../queries/use-resource-directory-query'

const directory = useResourceDirectoryQuery()
const keyword = ref('')
const items = computed(() => {
  const normalized = keyword.value.trim().toLowerCase()
  const networks = directory.data.value?.networks ?? []
  if (!normalized) return networks
  return networks.filter((item) => `${item.networkName} ${item.networkCode} ${item.partnerOrganizationName}`.toLowerCase().includes(normalized))
})
</script>

<template>
  <div class="resource-page">
    <div class="page-heading inline">
      <div><p class="breadcrumb">组织与资源 / 服务网点</p><h1>服务网点</h1><p>查看项目可用的合作网点、服务区域和在册师傅。网点不会进入企业内部组织层级。</p></div>
    </div>
    <PageError v-if="directory.isError.value" :detail="directory.error.value?.message ?? '服务网点目录加载失败'" />
    <template v-else>
      <section class="resource-summary-grid">
        <div><span>服务网点</span><strong>{{ directory.data.value?.networks.length ?? 0 }}</strong></div>
        <div><span>运行中</span><strong>{{ directory.data.value?.networks.filter((item) => item.status === 'ACTIVE').length ?? 0 }}</strong></div>
        <div><span>在册师傅</span><strong>{{ directory.data.value?.technicians.filter((item) => item.status === 'ACTIVE').length ?? 0 }}</strong></div>
        <div><span>待审核资质</span><strong>{{ directory.data.value?.technicians.reduce((total, item) => total + item.pendingQualificationCount, 0) ?? 0 }}</strong></div>
      </section>
      <section class="directory-panel">
        <div class="resource-filter"><Input v-model:value="keyword" placeholder="搜索网点名称、编码或合作公司" allow-clear><template #prefix><SearchOutlined /></template></Input></div>
        <div class="network-card-grid">
          <article v-for="network in items" :key="network.id" class="network-card">
            <header><div class="network-mark">网</div><div><h3>{{ network.networkName }}</h3><p>{{ network.partnerOrganizationName }}</p></div><StatusPill :tone="network.status === 'ACTIVE' ? 'green' : 'gray'" :label="network.status === 'ACTIVE' ? '运行中' : '已清退'" /></header>
            <dl><div><dt>网点编码</dt><dd>{{ network.networkCode }}</dd></div><div><dt>在册师傅</dt><dd>{{ network.activeTechnicianCount }} 人</dd></div><div><dt>更新时间</dt><dd>{{ formatDateTime(network.updatedAt) }}</dd></div></dl>
            <footer><span v-for="region in presentRegions(network.regionCodes)" :key="region">{{ region }}</span><em v-if="!network.regionCodes.length">尚未配置服务区域</em></footer>
          </article>
        </div>
        <div v-if="directory.isLoading.value" class="table-loading">正在加载服务网点…</div>
        <div v-else-if="!items.length" class="empty-state"><h3>暂无符合条件的服务网点</h3><p>请调整搜索条件或完善正式网点数据。</p></div>
      </section>
    </template>
  </div>
</template>
