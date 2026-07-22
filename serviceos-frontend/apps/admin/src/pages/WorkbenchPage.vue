<script setup lang="ts">
import type { AdminWorkOrderDirectoryItem } from '@serviceos/api-client'
import { loadAdminWorkbench, loadAdminWorkOrders } from '@serviceos/api-client'
import { currentIdentity } from '@serviceos/auth-context'
import { useQuery } from '@tanstack/vue-query'
import {
  Button,
  CalendarOutlined,
  DownloadOutlined,
  Input,
  SearchOutlined,
  Select,
} from '@serviceos/design-system'
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import PageError from '../components/PageError.vue'
import StatusPill from '../components/StatusPill.vue'
import { formatDateTime } from '../presenters/work-order'

const query = useQuery({ queryKey: ['admin-workbench'], queryFn: loadAdminWorkbench })
const keyword = ref('')
const projectId = ref<string>()
const status = ref<string>()
const slaRisk = ref<string>()
const applied = ref<Record<string, string | number | undefined>>({ limit: 8 })
const tasks = useQuery({
  queryKey: computed(() => ['admin-workbench-priority-items', applied.value]),
  queryFn: () => loadAdminWorkOrders(applied.value),
})
const summary = computed(() => query.data.value)
const identity = currentIdentity()

function search() {
  applied.value = {
    limit: 8,
    q: keyword.value.trim() || undefined,
    projectId: projectId.value,
    status: status.value,
    slaRisk: slaRisk.value,
  }
}

function riskTone(row: AdminWorkOrderDirectoryItem): 'red' | 'orange' | 'green' {
  if (row.slaLevel === 'BREACHED') return 'red'
  if (row.slaLevel === 'RISK') return 'orange'
  return 'green'
}
</script>

<template>
  <div class="workbench-page">
    <div class="page-heading">
      <div>
        <p class="breadcrumb">工作台</p>
        <h1>工单中心 / 工作台</h1>
        <p>欢迎回来，{{ identity.displayName }}！以下是当前授权范围内的关键任务与运营概况。</p>
      </div>
    </div>

    <PageError v-if="query.isError.value" :detail="query.error.value?.message ?? '工作台数据加载失败'" />
    <template v-else>
      <section class="metric-grid" aria-label="今日概览">
        <RouterLink class="metric-card urgent" to="/work-orders?view=priority">
          <span>今日优先处理</span><strong>{{ summary?.priorityCount ?? '—' }}</strong>
          <small>其中 {{ summary?.slaRiskCount ?? '—' }} 单存在 SLA 风险</small>
        </RouterLink>
        <RouterLink class="metric-card" to="/work-orders?view=review">
          <span>待审核</span><strong>{{ summary?.reviewCount ?? '—' }}</strong>
          <small>当前待处理审核任务</small>
        </RouterLink>
        <RouterLink class="metric-card" to="/work-orders?view=correction">
          <span>待整改</span><strong>{{ summary?.correctionCount ?? '—' }}</strong>
          <small>需要及时跟进</small>
        </RouterLink>
        <RouterLink class="metric-card" to="/work-orders?view=dispatch">
          <span>待派单</span><strong>{{ summary?.dispatchCount ?? '—' }}</strong>
          <small>等待平台分配责任网点</small>
        </RouterLink>
      </section>

      <section class="work-queue-panel">
        <div class="queue-tabs">
          <RouterLink to="/work-orders?view=mine" class="active">我的待办 <b>{{ summary?.priorityCount ?? '—' }}</b></RouterLink>
          <RouterLink to="/work-orders?view=sla">SLA 风险 <b>{{ summary?.slaRiskCount ?? '—' }}</b></RouterLink>
          <RouterLink to="/work-orders?view=exceptions">异常阻塞 <b>{{ summary?.exceptionCount ?? '—' }}</b></RouterLink>
          <RouterLink to="/work-orders?view=external">等待外部处理 <b>{{ summary?.waitingExternalCount ?? '—' }}</b></RouterLink>
          <RouterLink to="/work-orders?view=unassigned">待派责任网点 <b>{{ summary?.unassignedCount ?? '—' }}</b></RouterLink>
        </div>
        <form class="workbench-filter-bar" @submit.prevent="search">
          <Input v-model:value="keyword" placeholder="搜索工单编号 / 客户 / 项目" allow-clear>
            <template #prefix><SearchOutlined /></template>
          </Input>
          <Select
            v-model:value="projectId"
            placeholder="项目"
            allow-clear
            :options="tasks.data.value?.projectOptions.map((item) => ({ value: item.id, label: item.name })) ?? []"
          />
          <Select v-model:value="status" placeholder="工单状态" allow-clear :options="[{ value: 'RECEIVED', label: '待受理' }, { value: 'ACTIVE', label: '进行中' }, { value: 'FULFILLED', label: '已完成' }]" />
          <Select v-model:value="slaRisk" placeholder="SLA 风险" allow-clear :options="[{ value: 'BREACHED', label: '已超时' }, { value: 'NEAR', label: '即将超时' }]" />
          <Button html-type="submit" type="primary">查询</Button>
          <Button><CalendarOutlined /> 保存视图</Button>
          <Button><DownloadOutlined /> 导出</Button>
        </form>

        <div class="workbench-table-wrap">
          <table class="business-table workbench-table">
            <thead>
              <tr><th><input type="checkbox" aria-label="全选" /></th><th>工单编号</th><th>客户</th><th>项目</th><th>当前阶段</th><th>责任网点</th><th>责任师傅</th><th>更新时间</th><th>SLA 风险</th><th>状态</th><th>操作</th></tr>
            </thead>
            <tbody>
              <tr v-for="row in tasks.data.value?.items ?? []" :key="row.id">
                <td><input type="checkbox" :aria-label="`选择 ${row.orderCode}`" /></td>
                <td><RouterLink :to="`/work-orders/${row.id}`">{{ row.orderCode }}</RouterLink></td>
                <td><strong>{{ row.customerName || '数据不完整' }}</strong><small>{{ row.clientName }}</small></td>
                <td><strong>{{ row.projectName || '数据不完整' }}</strong><small>{{ row.serviceName }}</small></td>
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
          <div v-if="tasks.isLoading.value" class="table-loading">正在加载优先工单…</div>
          <div v-else-if="!tasks.data.value?.items.length" class="empty-state"><h3>当前没有待处理工单</h3><p>请切换业务视图或调整筛选条件。</p></div>
        </div>
        <footer class="workbench-table-footer">
          <span>共 {{ tasks.data.value?.totalCount ?? 0 }} 条</span>
          <RouterLink to="/work-orders">进入工单中心</RouterLink>
        </footer>
      </section>
    </template>
  </div>
</template>
