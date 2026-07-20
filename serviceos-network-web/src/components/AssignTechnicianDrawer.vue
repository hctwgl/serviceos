<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import {
  assignNetworkPortalTechnician,
  type NetworkPortalCapacityItem,
  type NetworkPortalTaskItem,
  type NetworkPortalTechnicianItem,
} from '../api/networkPortal'
import { statusLabel } from '../product/labels'
import { safeProblemMessage } from '@serviceos/web-core'

const props = defineProps<{
  open: boolean
  networkContextId: string
  task: NetworkPortalTaskItem | null
  technicians: NetworkPortalTechnicianItem[]
  capacity: NetworkPortalCapacityItem[]
}>()

const emit = defineEmits<{
  close: []
  assigned: []
}>()

const selectedTechnicianId = ref('')
const busy = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)
const search = ref('')

watch(
  () => props.open,
  (open) => {
    if (open) {
      selectedTechnicianId.value = ''
      error.value = null
      message.value = null
      search.value = ''
      busy.value = false
    }
  },
)

const capacityHint = computed(() => {
  const businessType = props.task?.businessType
  if (!businessType) {
    return props.capacity[0] ?? null
  }
  return props.capacity.find((row) => row.businessType === businessType) ?? props.capacity[0] ?? null
})

const filteredTechnicians = computed(() => {
  const q = search.value.trim()
  const list = props.technicians.filter(
    (tech) => tech.membershipStatus === 'ACTIVE' && tech.profileStatus === 'ACTIVE',
  )
  if (!q) return list
  return list.filter((tech) => tech.displayName.includes(q))
})

const selected = computed(() =>
  filteredTechnicians.value.find((tech) => tech.technicianProfileId === selectedTechnicianId.value),
)

const impactSummary = computed(() => {
  const cap = capacityHint.value
  const parts: string[] = []
  if (selected.value) {
    parts.push(`将指派给 ${selected.value.displayName}`)
  }
  if (cap) {
    parts.push(
      `产能口径（${statusLabel(cap.businessType)}）：占用 ${cap.occupiedUnits}/${cap.maxUnits}，可用 ${cap.availableUnits}`,
    )
    if (cap.availableUnits <= 0) {
      parts.push('当前业务类型产能已满，提交可能被服务端拒绝')
    }
  } else {
    parts.push('UI_DATA_GAP：服务端未返回对应业务类型产能明细')
  }
  parts.push('距离、日程冲突与推荐解释读模型尚未正式交付，不在前端猜测')
  return parts
})

async function submit() {
  if (!props.task || !selectedTechnicianId.value || !props.networkContextId) return
  busy.value = true
  error.value = null
  message.value = null
  try {
    const receipt = await assignNetworkPortalTechnician(props.networkContextId, props.task.taskId, {
      technicianAssigneeId: selectedTechnicianId.value,
      businessType: props.task.businessType?.trim() || 'INSTALLATION',
    })
    message.value = `指派已生效：师傅服务分配已激活（${receipt.data.technicianServiceAssignmentId.slice(0, 8)}…）`
    emit('assigned')
  } catch (err) {
    error.value = safeProblemMessage(err)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div
    v-if="open"
    class="assign-drawer-backdrop"
    data-testid="assign-technician-drawer"
    @click.self="emit('close')"
  >
    <aside class="assign-drawer" role="dialog" aria-label="分配师傅">
      <header class="assign-drawer__header">
        <div>
          <h2>分配师傅</h2>
          <p v-if="task" class="muted">
            任务 {{ statusLabel(task.taskType || '') || '现场任务' }}
            · 工单
            <RouterLink :to="`/network-portal/work-orders/${task.workOrderId}`">
              打开工作区
            </RouterLink>
          </p>
        </div>
        <button type="button" class="ghost" @click="emit('close')">关闭</button>
      </header>

      <label class="field">
        <span>搜索师傅</span>
        <input v-model="search" type="search" placeholder="按姓名筛选" data-testid="assign-drawer-search" />
      </label>

      <div class="candidate-list" data-testid="assign-drawer-candidates">
        <button
          v-for="tech in filteredTechnicians"
          :key="tech.technicianProfileId"
          type="button"
          class="candidate-card"
          :class="{ selected: selectedTechnicianId === tech.technicianProfileId }"
          :data-testid="`assign-candidate-${tech.technicianProfileId}`"
          @click="selectedTechnicianId = tech.technicianProfileId"
        >
          <strong>{{ tech.displayName }}</strong>
          <span class="muted">
            {{ statusLabel(tech.profileStatus) }} · 关系 {{ statusLabel(tech.membershipStatus) }}
          </span>
          <span v-if="capacityHint" class="muted">
            网点产能可用 {{ capacityHint.availableUnits }} / {{ capacityHint.maxUnits }}
          </span>
        </button>
        <p v-if="!filteredTechnicians.length" class="muted" data-testid="assign-drawer-empty">
          当前无可分配师傅。请确认师傅关系为 ACTIVE，或检查资质与产能。
        </p>
      </div>

      <section class="impact" data-testid="assign-drawer-impact">
        <h3>影响与冲突摘要</h3>
        <ul>
          <li v-for="(line, index) in impactSummary" :key="index">{{ line }}</li>
        </ul>
      </section>

      <footer class="assign-drawer__footer">
        <button type="button" class="ghost" @click="emit('close')">取消</button>
        <button
          type="button"
          class="primary"
          data-testid="assign-drawer-submit"
          :disabled="busy || !selectedTechnicianId || !task"
          @click="submit"
        >
          {{ busy ? '分配处理中…' : '确认分配' }}
        </button>
      </footer>
      <p v-if="error" class="error" data-testid="assign-drawer-error">{{ error }}</p>
      <p v-if="message" class="ok" data-testid="assign-drawer-message">{{ message }}</p>
    </aside>
  </div>
</template>

<style scoped>
.assign-drawer-backdrop {
  position: fixed;
  inset: 0;
  background: rgb(15 23 42 / 35%);
  display: flex;
  justify-content: flex-end;
  z-index: 40;
}
.assign-drawer {
  width: min(440px, 100%);
  height: 100%;
  background: var(--sos-color-surface-card);
  border-left: 1px solid var(--sos-color-border-default);
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow: auto;
}
.assign-drawer__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}
.assign-drawer__header h2 {
  margin: 0 0 4px;
  font-size: 18px;
}
.field {
  display: grid;
  gap: 6px;
  font-size: 13px;
}
.field input {
  border: 1px solid var(--sos-color-border-default);
  border-radius: 6px;
  padding: 8px 10px;
}
.candidate-list {
  display: grid;
  gap: 8px;
  flex: 1;
}
.candidate-card {
  text-align: left;
  display: grid;
  gap: 4px;
  padding: 12px;
  border: 1px solid var(--sos-color-border-default);
  border-radius: var(--sos-radius-md);
  background: var(--sos-color-surface-subtle);
  cursor: pointer;
}
.candidate-card.selected {
  border-color: var(--sos-primary-600);
  background: var(--sos-primary-100);
}
.impact {
  border: 1px solid var(--sos-color-border-light);
  border-radius: var(--sos-radius-md);
  padding: 12px;
  background: var(--sos-color-surface-subtle);
}
.impact h3 {
  margin: 0 0 8px;
  font-size: 14px;
}
.impact ul {
  margin: 0;
  padding-left: 18px;
  color: var(--sos-color-text-secondary);
  font-size: 13px;
}
.assign-drawer__footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
button.primary {
  background: var(--sos-primary-600);
  color: #fff;
  border: 1px solid var(--sos-primary-600);
  border-radius: 6px;
  padding: 8px 14px;
  cursor: pointer;
}
button.primary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
button.ghost {
  background: #fff;
  border: 1px solid var(--sos-color-border-default);
  border-radius: 6px;
  padding: 8px 14px;
  cursor: pointer;
}
.muted {
  color: var(--sos-color-text-tertiary);
  font-size: 12px;
  margin: 0;
}
.error {
  color: var(--sos-color-status-critical-fg);
  margin: 0;
}
.ok {
  color: var(--sos-color-status-success-fg);
  margin: 0;
}
</style>
