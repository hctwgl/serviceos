<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { getNetworkPortalWorkbench, type NetworkPortalWorkbench } from '../api/networkPortal'
import { formatDateTime, safeProblemMessage } from '@serviceos/web-core'
import PageState from '../components/PageState.vue'

const props = defineProps<{ networkContextId: string | null }>()
const data = ref<NetworkPortalWorkbench | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

async function load() {
  if (!props.networkContextId) {
    data.value = null
    error.value = '请选择网点上下文'
    loading.value = false
    return
  }
  loading.value = true
  try {
    data.value = await getNetworkPortalWorkbench(props.networkContextId)
    error.value = null
  } catch (err) {
    data.value = null
    error.value = safeProblemMessage(err)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})
watch(() => props.networkContextId, () => {
  void load()
})
</script>

<template>
  <section data-testid="network-portal-workbench">
    <header class="hero">
      <div>
        <h2>本网点工作台</h2>
        <p class="subtitle">今天需要安排什么：接单、派师傅、预约、复核与整改。</p>
      </div>
      <button type="button" data-testid="network-workbench-reload" @click="load">刷新</button>
    </header>

    <PageState v-if="loading" kind="loading" />
    <PageState
      v-else-if="error"
      kind="error"
      :description="error"
      @reload="load"
    />
    <template v-else-if="data">
      <p class="as-of" data-testid="network-workbench-as-of">
        统计时间：{{ formatDateTime(data.asOf) }}
      </p>
      <ul data-testid="network-workbench-counts" class="cards">
        <li>
          <RouterLink to="/network-portal/work-orders" data-testid="workbench-active-work-orders">
            处理中工单：{{ data.activeWorkOrderCount }}
          </RouterLink>
          <p class="hint">下一步：查看待安排工单</p>
        </li>
        <li>
          <RouterLink to="/network-portal/tasks" data-testid="workbench-active-tasks">
            处理中任务：{{ data.activeTaskCount }}
          </RouterLink>
          <p class="hint">下一步：指派师傅或跟踪预约</p>
        </li>
        <li>
          <RouterLink to="/network-portal/technicians" data-testid="workbench-active-technicians">
            可接单师傅：{{ data.activeTechnicianCount }}
          </RouterLink>
        </li>
        <li v-if="typeof data.unassignedTechnicianTaskCount === 'number'">
          <RouterLink to="/network-portal/tasks" data-testid="workbench-unassigned-count">
            待指派师傅：{{ data.unassignedTechnicianTaskCount }}
          </RouterLink>
          <p class="hint">下一步：为任务选择服务师傅</p>
        </li>
        <li v-if="typeof data.openCorrectionCaseCount === 'number'">
          <RouterLink to="/network-portal/corrections" data-testid="workbench-correction-count">
            待处理整改：{{ data.openCorrectionCaseCount }}
          </RouterLink>
        </li>
        <li v-if="typeof data.openOperationalExceptionCount === 'number'">
          <RouterLink to="/network-portal/exceptions" data-testid="workbench-exception-count">
            网点异常：{{ data.openOperationalExceptionCount }}
          </RouterLink>
        </li>
        <li v-if="typeof data.pendingQualificationCount === 'number'">
          <RouterLink
            to="/network-portal/qualifications"
            data-testid="workbench-qualification-count"
          >
            待审资质：{{ data.pendingQualificationCount }}
          </RouterLink>
        </li>
        <li v-if="data.slaSummary" data-testid="workbench-sla-summary">
          <span data-testid="workbench-sla-open-count">
            服务时效风险：{{ data.slaSummary.openCount }}
          </span>
          <span class="muted"> / </span>
          <span data-testid="workbench-sla-breached-count">
            已超时：{{ data.slaSummary.breachedCount }}
          </span>
          <p class="hint">优先处理已超时任务</p>
        </li>
      </ul>
      <div data-testid="network-workbench-capacity">
        <h3>
          <RouterLink to="/network-portal/capacity" data-testid="workbench-capacity-deeplink">
            师傅当前负载
          </RouterLink>
        </h3>
        <ul v-if="data.capacity.length">
          <li
            v-for="row in data.capacity"
            :key="row.capacityCounterId"
            :data-testid="`workbench-capacity-${row.businessType}`"
          >
            {{ row.businessType }}：占用 {{ row.occupiedUnits }} / 上限 {{ row.maxUnits }}
            （可用 {{ row.availableUnits }}）
            <span class="muted" data-testid="workbench-capacity-updated-at">
              · 更新时间 {{ formatDateTime(row.updatedAt) }}
            </span>
          </li>
        </ul>
        <PageState
          v-else
          kind="empty"
          guide="暂无容量计数。完成网点容量配置后将在此显示师傅负载。"
        />
      </div>
    </template>
  </section>
</template>

<style scoped>
.hero {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: flex-start;
}
.subtitle,
.as-of,
.muted,
.hint {
  color: #5b6573;
  font-size: 0.9rem;
}
.hint {
  margin: 0.25rem 0 0;
}
.cards {
  list-style: none;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 0.75rem;
}
.cards li {
  background: #fff;
  border-radius: 10px;
  padding: 0.85rem;
  box-shadow: 0 1px 2px rgb(16 42 67 / 8%);
}
button {
  border: 1px solid #bcccdc;
  background: #f0f4f8;
  border-radius: 6px;
  padding: 0.4rem 0.75rem;
  cursor: pointer;
}
</style>
