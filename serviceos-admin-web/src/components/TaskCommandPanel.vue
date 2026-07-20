<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  assignTaskCandidates,
  claimHumanTask,
  completeHumanTask,
  releaseHumanTask,
  startHumanTask,
  type InputVersionRef,
  type TaskAllowedAction,
  type TaskAllowedActions,
} from '../api/tasks'

const props = defineProps<{
  taskId: string
  allowedActions: TaskAllowedActions
  preparedInputs?: InputVersionRef[]
}>()

const emit = defineEmits<{ executed: [] }>()

const busy = ref(false)
const message = ref<string | null>(null)
const error = ref<string | null>(null)
const reasonCode = ref('OPERATOR_RELEASE')
const resultRef = ref('')
const resultDigest = ref('')
const dualInputJson = ref('')
const candidatePrincipalIds = ref('')
const assignSourceId = ref('admin-manual-assign')

const actions = computed(() => props.allowedActions.actions)

watch(
  () => props.allowedActions.resourceVersion,
  () => {
    message.value = null
    error.value = null
  },
)

watch(
  () => props.preparedInputs,
  (inputs) => {
    const form = inputs?.find((input) => input.kind === 'FORM_SUBMISSION')
    const evidence = inputs?.find((input) => input.kind === 'EVIDENCE_SET_SNAPSHOT')
    const primary = form ?? evidence
    if (primary) {
      resultRef.value = primary.ref
      resultDigest.value = primary.digest
    }
    // 双输入 Task 的主引用必须保持 FormSubmission，并同时提交精确的两份版本引用。
    // 单输入时清空该字段，避免把旧 Task 的双引用 JSON 带入后续命令。
    dualInputJson.value =
      form && evidence ? JSON.stringify([form, evidence], null, 2) : ''
  },
  { deep: true },
)

async function run(action: TaskAllowedAction) {
  busy.value = true
  message.value = null
  error.value = null
  const version = props.allowedActions.resourceVersion
  try {
    if (action.code === 'task.claim') {
      const result = await claimHumanTask(props.taskId, version)
      message.value = `已领取，version=${result.data.version}`
    } else if (action.code === 'task.start') {
      const result = await startHumanTask(props.taskId, version)
      message.value = `已启动，version=${result.data.version}`
    } else if (action.code === 'task.release') {
      if (!/^[A-Z][A-Z0-9_]{1,99}$/.test(reasonCode.value)) {
        throw new Error('reasonCode 必须匹配 ^[A-Z][A-Z0-9_]{1,99}$')
      }
      const result = await releaseHumanTask(props.taskId, version, {
        reasonCode: reasonCode.value,
      })
      message.value = `已释放，version=${result.data.version}`
    } else if (action.code === 'task.complete') {
      if (!resultRef.value.trim() || !/^[0-9a-f]{64}$/.test(resultDigest.value)) {
        throw new Error('complete 需要 resultRef 与 64 位 hex resultDigest')
      }
      let inputVersionRefs: InputVersionRef[] | undefined
      if (dualInputJson.value.trim()) {
        inputVersionRefs = JSON.parse(dualInputJson.value) as typeof inputVersionRefs
      }
      const result = await completeHumanTask(props.taskId, version, {
        resultRef: resultRef.value.trim(),
        resultDigest: resultDigest.value.trim(),
        inputVersionRefs,
      })
      message.value = `已完成，version=${result.data.version}`
    } else {
      throw new Error(`未知动作 ${action.code}`)
    }
    emit('executed')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '命令执行失败'
  } finally {
    busy.value = false
  }
}

async function assignCandidates() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    const ids = candidatePrincipalIds.value
      .split(/[,\s]+/)
      .map((v) => v.trim())
      .filter(Boolean)
    if (ids.length === 0) throw new Error('需要至少一个 candidatePrincipalId')
    const result = await assignTaskCandidates(props.taskId, props.allowedActions.resourceVersion, {
      candidatePrincipalIds: ids,
      sourceType: 'MANUAL',
      sourceId: assignSourceId.value.trim() || 'admin-manual-assign',
    })
    message.value = `已冻结候选 batch=${result.data.assignmentBatchId} / count=${result.data.candidateCount} / version=${result.data.taskVersion}`
    emit('executed')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '分配候选失败'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="panel">
    <p class="meta">
      仅渲染服务端返回的动作；命令携带 Idempotency-Key 与 If-Match="{{ allowedActions.resourceVersion }}"。
    </p>
    <div class="actions">
      <button
        v-for="action in actions"
        :key="action.code"
        type="button"
        :disabled="busy"
        @click="run(action)"
      >
        {{ action.label }}
      </button>
    </div>

    <div v-if="actions.some((a) => a.code === 'task.release')" class="fields">
      <label>
        release reasonCode
        <input v-model="reasonCode" />
      </label>
    </div>

    <div
      v-if="actions.some((a) => a.code === 'task.complete') || (preparedInputs?.length ?? 0) > 0"
      class="fields"
    >
      <label>
        resultRef
        <input
          v-model="resultRef"
          aria-label="resultRef"
          placeholder="form-submission://... 或 evidence-set-snapshot://..."
        />
      </label>
      <label>
        resultDigest
        <input v-model="resultDigest" aria-label="resultDigest" placeholder="64 hex digest" />
      </label>
      <label>
        inputVersionRefs JSON（双引用可选）
        <textarea
          v-model="dualInputJson"
          rows="4"
          placeholder='[{"kind":"FORM_SUBMISSION","ref":"...","digest":"..."}]'
        />
      </label>
    </div>

    <div class="fields">
      <label>
        assign-candidates principalIds
        <input v-model="candidatePrincipalIds" placeholder="user-1,user-2" />
      </label>
      <label>
        sourceId
        <input v-model="assignSourceId" />
      </label>
      <button type="button" :disabled="busy" @click="assignCandidates">assign-candidates</button>
    </div>

    <p v-if="message" class="ok">{{ message }}</p>
    <p v-if="error" class="error">{{ error }}</p>
  </div>
</template>

<style scoped>
.panel {
  display: grid;
  gap: 0.75rem;
}
.meta {
  margin: 0;
  color: #627d98;
  font-size: 0.85rem;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}
button {
  border: 0;
  background: #243b53;
  color: #fff;
  border-radius: 6px;
  padding: 0.45rem 0.8rem;
  cursor: pointer;
}
button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.fields {
  display: grid;
  gap: 0.55rem;
}
label {
  display: grid;
  gap: 0.25rem;
  font-size: 0.85rem;
  color: #486581;
}
input,
textarea {
  border: 1px solid #bcccdc;
  border-radius: 6px;
  padding: 0.4rem 0.55rem;
  font-family: ui-monospace, monospace;
}
.ok {
  color: #054e31;
  margin: 0;
}
.error {
  color: #9b1c1c;
  margin: 0;
}
</style>
