<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { getNetworkPortalWorkbench, type NetworkPortalWorkbench } from '../api/networkPortal'

const props = defineProps<{ networkContextId: string | null }>()
const data = ref<NetworkPortalWorkbench | null>(null)
const error = ref<string | null>(null)

async function load() {
  if (!props.networkContextId) {
    data.value = null
    error.value = '请选择 NETWORK 上下文'
    return
  }
  try {
    data.value = await getNetworkPortalWorkbench(props.networkContextId)
    error.value = null
  } catch (err) {
    data.value = null
    error.value = err instanceof Error ? err.message : '工作台加载失败'
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
    <h2>本网点工作台</h2>
    <p v-if="error" data-testid="network-portal-error">{{ error }}</p>
    <template v-else-if="data">
      <p class="as-of" data-testid="network-workbench-as-of">统计时间：{{ data.asOf }}</p>
      <ul data-testid="network-workbench-counts">
        <li>
          <RouterLink to="/network-portal/work-orders" data-testid="workbench-active-work-orders">
            ACTIVE 工单：{{ data.activeWorkOrderCount }}
          </RouterLink>
        </li>
        <li>
          <RouterLink to="/network-portal/tasks" data-testid="workbench-active-tasks">
            ACTIVE 任务：{{ data.activeTaskCount }}
          </RouterLink>
        </li>
        <li>
          <RouterLink to="/network-portal/technicians" data-testid="workbench-active-technicians">
            ACTIVE 师傅：{{ data.activeTechnicianCount }}
          </RouterLink>
        </li>
        <li v-if="typeof data.unassignedTechnicianTaskCount === 'number'">
          <RouterLink to="/network-portal/tasks" data-testid="workbench-unassigned-count">
            待指派任务：{{ data.unassignedTechnicianTaskCount }}
          </RouterLink>
        </li>
        <li v-if="typeof data.openCorrectionCaseCount === 'number'">
          <RouterLink to="/network-portal/corrections" data-testid="workbench-correction-count">
            待处理整改：{{ data.openCorrectionCaseCount }}
          </RouterLink>
        </li>
        <li v-if="typeof data.openOperationalExceptionCount === 'number'">
          <RouterLink to="/network-portal/exceptions" data-testid="workbench-exception-count">
            待处理异常：{{ data.openOperationalExceptionCount }}
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
            SLA 风险（开放）：{{ data.slaSummary.openCount }}
          </span>
          <span class="muted"> / </span>
          <span data-testid="workbench-sla-breached-count">
            已超时：{{ data.slaSummary.breachedCount }}
          </span>
          <p class="hint">
            需 NETWORK <code>sla.read</code>。仅统计本网点 ACTIVE 任务上 RUNNING/BREACHED
            （breached ⊆ open）。无 SLA 详情表或深链。
          </p>
        </li>
      </ul>
      <div data-testid="network-workbench-capacity">
        <h3>
          <RouterLink to="/network-portal/capacity" data-testid="workbench-capacity-deeplink">
            容量
          </RouterLink>
        </h3>
        <ul v-if="data.capacity.length">
          <li
            v-for="row in data.capacity"
            :key="row.capacityCounterId"
            :data-testid="`workbench-capacity-${row.businessType}`"
          >
            {{ row.businessType }}：占用 {{ row.occupiedUnits }} / 上限 {{ row.maxUnits }}
            （可用 {{ row.availableUnits }}，v{{ row.version }}）
            <span class="muted" data-testid="workbench-capacity-updated-at">
              · 更新时间 {{ row.updatedAt }}
            </span>
          </li>
        </ul>
        <p v-else data-testid="workbench-capacity-empty">暂无容量计数</p>
      </div>
    </template>
  </section>
</template>

<style scoped>
.as-of,
.muted,
.hint {
  color: #5b6573;
  font-size: 0.9rem;
}
.hint {
  margin: 0.25rem 0 0;
}
</style>
