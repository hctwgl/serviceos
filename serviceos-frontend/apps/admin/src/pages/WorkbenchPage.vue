<script setup lang="ts">
import { loadAdminWorkbench } from '@serviceos/api-client'
import { currentIdentity } from '@serviceos/auth-context'
import { useQuery } from '@tanstack/vue-query'
import { RightOutlined } from '@serviceos/design-system'
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import PageError from '../components/PageError.vue'

const query = useQuery({ queryKey: ['admin-workbench'], queryFn: loadAdminWorkbench })
const summary = computed(() => query.data.value)
const identity = currentIdentity()
const queues = computed(() => [
  { label: '待派责任网点', count: summary.value?.dispatchCount, to: '/work-orders?view=dispatch', hint: '确认服务区域与网点产能后完成派单' },
  { label: '待审核', count: summary.value?.reviewCount, to: '/work-orders?view=review', hint: '检查表单资料并给出审核结论' },
  { label: '待整改', count: summary.value?.correctionCount, to: '/work-orders?view=correction', hint: '跟进整改责任人和截止时间' },
  { label: '异常阻塞', count: summary.value?.exceptionCount, to: '/work-orders?view=exceptions', hint: '处理阻塞履约的运营异常' },
])
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
        <div class="queue-content">
          <div class="queue-toolbar">
            <div>
              <h2>需要立即处理</h2>
              <p>已按 SLA 风险、预约时间和责任状态排序。</p>
            </div>
            <RouterLink to="/work-orders">进入工单中心 <RightOutlined /></RouterLink>
          </div>
          <div class="workbench-queue-list">
            <RouterLink v-for="item in queues" :key="item.label" :to="item.to" class="workbench-queue-row">
              <div><strong>{{ item.label }}</strong><small>{{ item.hint }}</small></div>
              <b>{{ item.count ?? '—' }}</b><RightOutlined />
            </RouterLink>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>
