<script setup lang="ts">
import { computed, ref } from 'vue'
import { Input, SearchOutlined } from '@serviceos/design-system'
import PageError from '../../../components/PageError.vue'
import StatusPill from '../../../components/StatusPill.vue'
import { formatDateTime } from '../../../presenters/work-order'
import { presentClientKinds, presentQualifications } from '../presenters/resource-directory'
import { useResourceDirectoryQuery } from '../queries/use-resource-directory-query'

const directory = useResourceDirectoryQuery()
const keyword = ref('')
const items = computed(() => {
  const normalized = keyword.value.trim().toLowerCase()
  const technicians = directory.data.value?.technicians ?? []
  if (!normalized) return technicians
  return technicians.filter((item) => `${item.displayName} ${item.networkNames.join(' ')}`.toLowerCase().includes(normalized))
})
</script>

<template>
  <div class="resource-page">
    <div class="page-heading inline"><h1>师傅档案</h1></div>
    <PageError v-if="directory.isError.value" :detail="directory.error.value?.message ?? '师傅档案加载失败'" />
    <section v-else class="directory-panel">
      <div class="resource-filter"><Input v-model:value="keyword" placeholder="搜索师傅姓名或服务网点" allow-clear><template #prefix><SearchOutlined /></template></Input></div>
      <div class="data-table-wrap">
        <table class="business-table technician-table">
          <thead><tr><th>师傅</th><th>服务网点</th><th>有效资质</th><th>现场作业端</th><th>待审核</th><th>档案状态</th><th>更新时间</th></tr></thead>
          <tbody>
            <tr v-for="item in items" :key="item.id">
              <td><strong>{{ item.displayName }}</strong></td>
              <td>{{ item.networkNames.join('、') || '尚未绑定服务网点' }}</td>
              <td>{{ presentQualifications(item.approvedQualificationCodes).join('、') || '暂无有效资质' }}</td>
              <td>{{ presentClientKinds(item.supportedClientKinds).join('、') || '尚未声明' }}</td>
              <td><StatusPill :tone="item.pendingQualificationCount ? 'orange' : 'green'" :label="item.pendingQualificationCount ? `${item.pendingQualificationCount} 项` : '无'" /></td>
              <td><StatusPill :tone="item.status === 'ACTIVE' ? 'green' : 'gray'" :label="item.status === 'ACTIVE' ? '启用' : '停用'" /></td>
              <td>{{ formatDateTime(item.updatedAt) }}</td>
            </tr>
          </tbody>
        </table>
        <div v-if="directory.isLoading.value" class="table-loading">正在加载师傅档案…</div>
        <div v-else-if="!items.length" class="empty-state"><h3>暂无符合条件的师傅</h3><p>请调整搜索条件。</p></div>
      </div>
    </section>
  </div>
</template>
