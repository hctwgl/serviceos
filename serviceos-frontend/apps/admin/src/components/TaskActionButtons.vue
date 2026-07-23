<script setup lang="ts">
import type { TaskAllowedAction, WorkOrderWorkspaceTask } from '@serviceos/api-client'
import { claimTask, completeTask, releaseTask, startTask } from '@serviceos/api-client'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { Button, Input, message, Modal, Radio } from '@serviceos/design-system'
import { ref, watch } from 'vue'
import PageError from './PageError.vue'

/**
 * 工单工作区「允许操作」统一动作入口。
 * dispatch.assignment.manage 交给页面打开 AssignmentDrawer；
 * task.claim/start/release/complete 按契约携带幂等键与 If-Match 版本头，
 * 陈旧版本由后端 409/412 失败关闭，前端只展示中文错误、不静默重试。
 */
const props = defineProps<{
  actions: TaskAllowedAction[]
  task: WorkOrderWorkspaceTask | null
  serviceAssignmentSummary: { networkId: string | null; technicianId: string | null } | null
}>()
const emit = defineEmits<{ manageAssignment: [] }>()

const queryClient = useQueryClient()

const releaseOpen = ref(false)
const releaseReason = ref<string>()
const releaseReasons = [
  { value: 'OPERATOR_RELEASE', label: '暂时无法继续处理' },
  { value: 'RESPONSIBILITY_TRANSFER', label: '需要转交其他人员' },
  { value: 'TASK_CONTEXT_CHANGED', label: '工单情况已经变化' },
]

const completeOpen = ref(false)
const resultRef = ref('')
const resultDigest = ref('')
const digestTouched = ref(false)

async function sha256Hex(text: string): Promise<string> {
  // eslint 环境未声明浏览器全局对象，统一经 globalThis 访问 Web Crypto。
  const digest = await globalThis.crypto.subtle.digest(
    'SHA-256',
    new globalThis.TextEncoder().encode(text),
  )
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, '0')).join('')
}

// resultDigest 默认跟随 resultRef 自动重算；用户手动编辑摘要后不再覆盖。
watch(resultRef, (value) => {
  if (digestTouched.value) return
  if (!value) {
    resultDigest.value = ''
    return
  }
  void sha256Hex(value).then((hex) => {
    if (!digestTouched.value) resultDigest.value = hex
  })
})

function errorDetail(error: unknown): string {
  // ApiProblem 的 message 已经过 presentProblem 中文化；其余异常按技术错误处理。
  return error instanceof Error ? error.message : '操作未能完成，请稍后重试'
}

async function refreshWorkspace() {
  await queryClient.invalidateQueries({ queryKey: ['work-order-workspace'] })
}

const simpleCommand = useMutation({
  mutationFn: async (code: 'task.claim' | 'task.start') => {
    if (!props.task) throw new Error('当前没有可操作的进行中任务')
    return code === 'task.claim'
      ? claimTask(props.task.taskId, props.task.version)
      : startTask(props.task.taskId, props.task.version)
  },
  onSuccess: async (_receipt, code) => {
    message.success(code === 'task.claim' ? '任务领取成功' : '任务已启动')
    await refreshWorkspace()
  },
  onError: (error) => message.error(errorDetail(error)),
})

const releaseCommand = useMutation({
  mutationFn: async () => {
    if (!props.task) throw new Error('当前没有可操作的进行中任务')
    if (!releaseReason.value) throw new Error('请选择释放原因')
    return releaseTask(props.task.taskId, props.task.version, releaseReason.value)
  },
  onSuccess: async () => {
    message.success('任务已释放，退回待领取状态')
    releaseOpen.value = false
    await refreshWorkspace()
  },
})

const completeCommand = useMutation({
  mutationFn: async () => {
    if (!props.task) throw new Error('当前没有可操作的进行中任务')
    const ref = resultRef.value.trim()
    if (!ref) throw new Error('请填写结果引用（resultRef）')
    if (!/^[0-9a-f]{64}$/.test(resultDigest.value)) {
      throw new Error('结果摘要必须是 64 位小写 sha256 十六进制')
    }
    return completeTask(props.task.taskId, props.task.version, {
      resultRef: ref,
      resultDigest: resultDigest.value,
    })
  },
  onSuccess: async () => {
    message.success('任务已完成')
    completeOpen.value = false
    await refreshWorkspace()
  },
})

function buttonType(code: string): 'primary' | 'default' {
  return code === 'dispatch.assignment.manage' || code.includes('SUBMIT') ? 'primary' : 'default'
}

function openComplete() {
  digestTouched.value = false
  const assignee =
    props.serviceAssignmentSummary?.technicianId ?? props.serviceAssignmentSummary?.networkId ?? ''
  resultRef.value = assignee ? `service-assignment://${assignee}` : ''
  completeOpen.value = true
}

function onAction(code: string) {
  if (code === 'dispatch.assignment.manage') {
    emit('manageAssignment')
    return
  }
  if (code === 'task.release') {
    releaseCommand.reset()
    releaseReason.value = undefined
    releaseOpen.value = true
    return
  }
  if (code === 'task.complete') {
    completeCommand.reset()
    openComplete()
    return
  }
  if (code === 'task.claim' || code === 'task.start') {
    simpleCommand.mutate(code)
    return
  }
  message.warning(`暂不支持的操作：${code}`)
}
</script>

<template>
  <Button
    v-for="action in actions"
    :key="action.code"
    :type="buttonType(action.code)"
    :disabled="!task && action.code !== 'dispatch.assignment.manage'"
    :loading="simpleCommand.isPending.value && simpleCommand.variables.value === action.code"
    @click="onAction(action.code)"
  >
    {{ action.label }}
  </Button>

  <Modal
    :open="releaseOpen"
    title="释放任务"
    ok-text="确认释放"
    cancel-text="取消"
    :confirm-loading="releaseCommand.isPending.value"
    :ok-button-props="{ disabled: !releaseReason }"
    @ok="releaseCommand.mutate()"
    @cancel="releaseOpen = false"
  >
    <p class="action-modal-intro">
      释放后任务退回待领取状态，请选择释放原因：
    </p>
    <Radio.Group
      v-model:value="releaseReason"
      class="release-reason-list"
    >
      <Radio
        v-for="reason in releaseReasons"
        :key="reason.value"
        :value="reason.value"
      >
        {{ reason.label }}<small class="reason-code">{{ reason.value }}</small>
      </Radio>
    </Radio.Group>
    <PageError
      v-if="releaseCommand.isError.value"
      :detail="errorDetail(releaseCommand.error.value)"
    />
  </Modal>

  <Modal
    :open="completeOpen"
    title="完成任务"
    ok-text="确认完成"
    cancel-text="取消"
    :confirm-loading="completeCommand.isPending.value"
    @ok="completeCommand.mutate()"
    @cancel="completeOpen = false"
  >
    <p class="action-modal-intro">
      结果引用用于回溯本次任务产出，结果摘要默认按结果引用自动计算 sha256，可按服务端约定修正。
    </p>
    <div class="complete-form">
      <label>
        <span>结果引用（resultRef）</span>
        <Input
          v-model:value="resultRef"
          placeholder="例如 service-assignment://师傅标识"
        />
      </label>
      <label>
        <span>结果摘要（resultDigest）</span>
        <Input
          v-model:value="resultDigest"
          placeholder="64 位 sha256 十六进制"
          @change="digestTouched = true"
        />
      </label>
    </div>
    <PageError
      v-if="completeCommand.isError.value"
      :detail="errorDetail(completeCommand.error.value)"
    />
  </Modal>
</template>

<style scoped>
.action-modal-intro {
  margin: 0 0 12px;
  color: var(--sos-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.release-reason-list {
  display: grid;
  gap: 10px;
  margin-bottom: 12px;
}

.reason-code {
  margin-left: 8px;
  color: var(--sos-text-muted);
  font-size: 11px;
}

.complete-form {
  display: grid;
  gap: 14px;
  margin-bottom: 12px;
}

.complete-form label {
  display: grid;
  gap: 7px;
}

.complete-form label > span {
  color: var(--sos-text-strong);
  font-size: 13px;
  font-weight: 600;
}
</style>
