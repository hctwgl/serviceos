<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import {
  assignNetworkPortalTechnician,
  type NetworkPortalAssignCandidateItem,
  type NetworkPortalTaskItem,
} from '../api/networkPortal'
import { statusLabel } from '../product/labels'
import { safeProblemMessage } from '@serviceos/web-core'

const props = defineProps<{
  open: boolean
  networkContextId: string
  task: NetworkPortalTaskItem | null
  candidates: NetworkPortalAssignCandidateItem[]
  loadingCandidates?: boolean
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

const filteredCandidates = computed(() => {
  const q = search.value.trim()
  const list = props.candidates
  if (!q) return list
  return list.filter((tech) => tech.displayName.includes(q))
})

const selected = computed(() =>
  filteredCandidates.value.find((tech) => tech.technicianProfileId === selectedTechnicianId.value),
)

const impactSummary = computed(() => {
  const parts: string[] = []
  if (selected.value) {
    parts.push(`将指派给 ${selected.value.displayName}`)
    parts.push(`当前开放任务 ${selected.value.openTaskCount} 个`)
    parts.push(selected.value.qualificationSummary)
    if (
      selected.value.capacityAvailableUnits != null &&
      selected.value.capacityMaxUnits != null
    ) {
      parts.push(
        `网点产能可用 ${selected.value.capacityAvailableUnits}/${selected.value.capacityMaxUnits}`,
      )
    }
    for (const warning of selected.value.warnings) {
      parts.push(warning)
    }
  } else {
    parts.push('请选择一名可分配师傅')
  }
  parts.push('距离与日程冲突读模型尚未交付，不在前端猜测')
  return parts
})

async function submit() {
  if (!props.task || !selectedTechnicianId.value || !props.networkContextId) return
  if (selected.value && !selected.value.assignable) {
    error.value = '所选师傅当前不可分配'
    return
  }
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
        <p v-if="loadingCandidates" class="muted">正在加载候选摘要…</p>
        <button
          v-for="tech in filteredCandidates"
          :key="tech.technicianProfileId"
          type="button"
          class="candidate-card"
          :class="{
            selected: selectedTechnicianId === tech.technicianProfileId,
            blocked: !tech.assignable,
          }"
          :data-testid="`assign-candidate-${tech.technicianProfileId}`"
          :disabled="!tech.assignable"
          @click="selectedTechnicianId = tech.technicianProfileId"
        >
          <strong>{{ tech.displayName }}</strong>
          <span class="muted">
            {{ statusLabel(tech.profileStatus) }} · 关系 {{ statusLabel(tech.membershipStatus) }}
          </span>
          <span class="muted" data-testid="assign-candidate-open-tasks">
            开放任务 {{ tech.openTaskCount }}
          </span>
          <span class="muted" data-testid="assign-candidate-qualification">
            {{ tech.qualificationSummary }}
          </span>
          <span
            v-if="tech.capacityAvailableUnits != null && tech.capacityMaxUnits != null"
            class="muted"
            data-testid="assign-candidate-capacity"
          >
            网点产能可用 {{ tech.capacityAvailableUnits }} / {{ tech.capacityMaxUnits }}
          </span>
          <span v-if="tech.warnings.length" class="warn">
            {{ tech.warnings[0] }}
          </span>
        </button>
        <p
          v-if="!loadingCandidates && !filteredCandidates.length"
          class="muted"
          data-testid="assign-drawer-empty"
        >
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
          :disabled="busy || !selectedTechnicianId || !task || (selected && !selected.assignable)"
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
.candidate-card.blocked {
  opacity: 0.55;
  cursor: not-allowed;
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
.warn {
  color: var(--sos-color-status-warning-fg, #ad6800);
  font-size: 12px;
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
