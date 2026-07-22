<script setup lang="ts">
import { computed, ref } from 'vue'
import { Input, SearchOutlined } from '@serviceos/design-system'
import PageError from '../../../components/PageError.vue'
import StatusPill from '../../../components/StatusPill.vue'
import { useClientProjectDirectoryQuery } from '../queries/use-client-project-directory-query'

const directory = useClientProjectDirectoryQuery()
const keyword = ref('')
const clients = computed(() => {
  const normalized = keyword.value.trim().toLowerCase()
  const items = directory.data.value?.clients ?? []
  if (!normalized) return items
  return items.filter((item) => `${item.clientName} ${item.brandNames.join(' ')}`.toLowerCase().includes(normalized))
})
</script>

<template>
  <div class="resource-page">
    <div class="page-heading inline"><div><p class="breadcrumb">客户与项目 / 客户品牌</p><h1>客户品牌</h1><p>管理租户内合作车企及其品牌目录，项目通过稳定客户编码建立业务归属。</p></div></div>
    <PageError v-if="directory.isError.value" :detail="directory.error.value?.message ?? '客户品牌目录加载失败'" />
    <section v-else class="directory-panel">
      <div class="resource-filter"><Input v-model:value="keyword" placeholder="搜索客户或品牌名称" allow-clear><template #prefix><SearchOutlined /></template></Input></div>
      <div class="client-card-grid">
        <article v-for="client in clients" :key="client.clientCode" class="client-card">
          <header><div class="client-logo">{{ client.clientName.slice(0, 1) }}</div><div><h3>{{ client.clientName }}</h3><p>合作客户</p></div><StatusPill :tone="client.status === 'ACTIVE' ? 'green' : 'gray'" :label="client.status === 'ACTIVE' ? '合作中' : '已停用'" /></header>
          <div class="client-project-count"><strong>{{ client.projectCount }}</strong><span>个项目</span></div>
          <footer><span v-for="brand in client.brandNames" :key="brand">{{ brand }}</span><em v-if="!client.brandNames.length">尚未登记品牌</em></footer>
        </article>
      </div>
      <div v-if="directory.isLoading.value" class="table-loading">正在加载客户品牌…</div>
      <div v-else-if="!clients.length" class="empty-state"><h3>暂无符合条件的客户</h3><p>请调整搜索条件。</p></div>
    </section>
  </div>
</template>
