<script setup lang="ts">
import type { AdminWorkOrderDirectoryItem } from '@serviceos/api-client'
import { loadAdminWorkOrders } from '@serviceos/api-client'
import { useQuery } from '@tanstack/vue-query'
import {
  Button,
  CalendarOutlined,
  DownloadOutlined,
  Input,
  SearchOutlined,
  Select,
  SettingOutlined,
} from '@serviceos/design-system'
import { computed, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import PageError from '../components/PageError.vue'
import StatusPill from '../components/StatusPill.vue'
import { formatDateTime } from '../presenters/work-order'

const route = useRoute()
const initialProjectId = typeof route.query.projectId === 'string' ? route.query.projectId : undefined
const keyword = ref('')
const project = ref<string | undefined>(initialProjectId)
const status = ref<string>()
const slaRisk = ref<string>()
const applied = ref({ q: '', projectId: initialProjectId, status: undefined as string | undefined, slaRisk: undefined as string | undefined })

const query = useQuery({
  queryKey: computed(() => ['work-orders', applied.value]),
  queryFn: () => loadAdminWorkOrders({ ...applied.value, limit: 20 }),
})

function search() {
  applied.value = { q: keyword.value.trim(), projectId: project.value, status: status.value, slaRisk: slaRisk.value }
}

function riskTone(row: AdminWorkOrderDirectoryItem): 'red' | 'orange' | 'green' {
  if (row.slaLevel === 'BREACHED') return 'red'
  if (row.slaLevel === 'RISK') return 'orange'
  return 'green'
}
</script>

<template>
  <div class="directory-page">
    <div class="page-heading inline">
      <div><p class="breadcrumb">工单运营 / 工单中心</p><h1>工单中心</h1><p>统一查看工单状态、责任、预约与 SLA 风险。</p></div>
      <div class="heading-actions"><Button><SettingOutlined /> 自定义列</Button><Button><DownloadOutlined /> 导出</Button></div>
    </div>

    <PageError v-if="query.isError.value" :detail="query.error.value?.message ?? '工单目录加载失败'" />
    <section v-else class="directory-panel">
      <div class="business-views">
        <button class="active" type="button">我的待办 <b>{{ query.data.value?.queueSummary.priorityCount ?? '—' }}</b></button>
        <button type="button">SLA 风险 <b>{{ query.data.value?.queueSummary.slaRiskCount ?? '—' }}</b></button>
        <button type="button">异常阻塞 <b>{{ query.data.value?.queueSummary.exceptionCount ?? '—' }}</b></button>
        <button type="button">等待外部处理 <b>{{ query.data.value?.queueSummary.waitingExternalCount ?? '—' }}</b></button>
        <button type="button">待派责任网点 <b>{{ query.data.value?.queueSummary.unassignedCount ?? '—' }}</b></button>
      </div>
      <form class="filter-bar" @submit.prevent="search">
        <Input v-model:value="keyword" placeholder="搜索工单编号、客户、项目" allow-clear><template #prefix><SearchOutlined /></template></Input>
        <Select v-model:value="project" placeholder="项目" allow-clear :options="query.data.value?.projectOptions.map((item) => ({ value: item.id, label: item.name })) ?? []" />
        <Select v-model:value="status" placeholder="工单状态" allow-clear :options="[{ value: 'RECEIVED', label: '待受理' }, { value: 'ACTIVE', label: '进行中' }, { value: 'FULFILLED', label: '已完成' }]" />
        <Select v-model:value="slaRisk" placeholder="SLA 风险" allow-clear :options="[{ value: 'BREACHED', label: '已超时' }, { value: 'NEAR', label: '即将超时' }]" />
        <Button html-type="submit" type="primary">查询</Button>
        <Button type="text"><CalendarOutlined /> 保存视图</Button>
      </form>

      <div class="data-table-wrap">
        <table class="business-table">
          <thead><tr><th><input type="checkbox" aria-label="全选" /></th><th>工单编号</th><th>客户</th><th>项目 / 服务</th><th>当前阶段</th><th>责任网点</th><th>责任师傅</th><th>更新时间</th><th>SLA 风险</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="row in query.data.value?.items ?? []" :key="row.id">
              <td><input type="checkbox" :aria-label="`选择 ${row.orderCode}`" /></td>
              <td><RouterLink :to="`/work-orders/${row.id}`">{{ row.orderCode }}</RouterLink></td>
              <td><strong>{{ row.customerName || '数据不完整' }}</strong><small>{{ row.customerPhone || '联系方式未提供' }}</small></td>
              <td><strong>{{ row.projectName || '数据不完整' }}</strong><small>{{ row.clientName }} · {{ row.serviceName }}</small></td>
              <td>{{ row.stageName || '数据不完整' }}</td>
              <td>{{ row.networkName || '待分配' }}</td>
              <td>{{ row.technicianName || '待分配' }}</td>
              <td>{{ formatDateTime(row.updatedAt) }}</td>
              <td><StatusPill :tone="riskTone(row)" :label="row.slaLabel" /></td>
              <td><StatusPill :tone="row.dataComplete ? 'blue' : 'red'" :label="row.dataComplete ? (row.statusName || '数据不完整') : '数据不完整'" /></td>
              <td><RouterLink :to="`/work-orders/${row.id}`">查看</RouterLink></td>
            </tr>
          </tbody>
        </table>
        <div v-if="query.isLoading.value" class="table-loading">正在加载工单…</div>
        <div v-else-if="!query.data.value?.items.length" class="empty-state"><h3>暂无符合条件的工单</h3><p>调整筛选条件后重新查询。</p></div>
      </div>
      <footer class="table-footer"><span>共 {{ query.data.value?.totalCount ?? 0 }} 条</span><div><Button disabled>上一页</Button><Button type="primary">1</Button><Button :disabled="!query.data.value?.nextCursor">下一页</Button></div></footer>
    </section>
  </div>
</template>
