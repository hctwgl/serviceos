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
          </li>
        </ul>
        <p v-else data-testid="workbench-capacity-empty">暂无容量计数</p>
      </div>
    </template>
  </section>
</template>
