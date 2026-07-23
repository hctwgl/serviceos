<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
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

const actions = computed(() => props.allowedActions.actions)
const preparedResultInputs = computed(() => props.preparedInputs ?? [])
const completionReady = computed(() => preparedResultInputs.value.length > 0)

watch(
  () => props.allowedActions.resourceVersion,
  () => {
    message.value = null
    error.value = null
  },
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
      const form = preparedResultInputs.value.find((input) => input.kind === 'FORM_SUBMISSION')
      const evidence = preparedResultInputs.value.find(
        (input) => input.kind === 'EVIDENCE_SET_SNAPSHOT',
      )
      const primary = form ?? evidence
      if (!primary) {
        throw new Error('请先完成当前任务要求的表单或资料，再提交完成')
      }
      // 双输入任务必须提交同一次页面加载得到的精确版本引用，避免表单与资料跨版本拼接。
      const inputVersionRefs: InputVersionRef[] | undefined =
        form && evidence ? [form, evidence] : undefined
      const result = await completeHumanTask(props.taskId, version, {
        resultRef: primary.ref,
        resultDigest: primary.digest,
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

</script>

<template>
  <div class="panel">
    <p class="meta">这里只显示当前任务允许执行的业务操作。</p>
    <div class="actions">
      <button
        v-for="action in actions"
        :key="action.code"
        type="button"
        :disabled="busy || (action.code === 'task.complete' && !completionReady)"
        @click="run(action)"
      >
        {{ action.label }}
      </button>
    </div>

    <div v-if="actions.some((a) => a.code === 'task.release')" class="fields">
      <label>
        释放原因
        <select v-model="reasonCode">
          <option value="OPERATOR_RELEASE">暂时无法继续处理</option>
          <option value="RESPONSIBILITY_TRANSFER">需要转交其他人员</option>
          <option value="TASK_CONTEXT_CHANGED">工单情况已经变化</option>
        </select>
      </label>
    </div>

    <div
      v-if="actions.some((a) => a.code === 'task.complete') || (preparedInputs?.length ?? 0) > 0"
      class="completion-summary"
    >
      <strong>{{ completionReady ? '任务结果已准备' : '任务结果尚未准备完成' }}</strong>
      <span v-if="completionReady">
        已完成 {{ preparedResultInputs.length }} 项表单或资料要求，可以提交任务结果。
      </span>
      <span v-else>请先完成当前任务要求的表单和资料。</span>
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
textarea,
select {
  border: 1px solid #bcccdc;
  border-radius: 6px;
  padding: 0.4rem 0.55rem;
  font-family: ui-monospace, monospace;
}
.completion-summary {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid #d9dee7;
  border-radius: 6px;
  background: #f8fafc;
  color: #334155;
  font-size: 13px;
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
