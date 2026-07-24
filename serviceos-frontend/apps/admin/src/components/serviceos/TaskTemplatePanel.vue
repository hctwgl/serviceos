<script setup lang="ts">
import { Input, Select, Tag } from '@serviceos/design-system'
import { computed, ref, watch } from 'vue'

export type TaskTemplateItem = {
  id: string
  name: string
  owner: string
  sla: string
  form: string
  evidence: string
  completion: string
  stage: string
}

const props = withDefaults(defineProps<{
  tasks: TaskTemplateItem[]
  editable?: boolean
}>(), {
  editable: false,
})

const emit = defineEmits<{
  'update:tasks': [tasks: TaskTemplateItem[]]
}>()

const selectedId = ref<string>()
const selectedTask = computed(() => props.tasks.find((task) => task.id === selectedId.value))

watch(
  () => props.tasks,
  (tasks) => {
    if (!tasks.some((task) => task.id === selectedId.value)) selectedId.value = tasks[0]?.id
  },
  { immediate: true },
)

const ownerOptions = [
  { value: '平台运营', label: '平台运营' },
  { value: '责任网点', label: '责任网点' },
  { value: '责任师傅', label: '责任师傅' },
  { value: '系统自动执行', label: '系统自动执行' },
]

function patchSelected(patch: Partial<TaskTemplateItem>) {
  if (!selectedTask.value) return
  emit('update:tasks', props.tasks.map((task) => (
    task.id === selectedTask.value?.id ? { ...task, ...patch } : task
  )))
}
</script>

<template>
  <section class="sos-task-template-panel">
    <header class="sos-section-heading">
      <div>
        <span class="sos-eyebrow">TASK TEMPLATES</span>
        <h3>任务模板设计</h3>
        <span>把每个履约节点落成可执行任务，负责人、SLA、表单和证据要求在同一处确认。</span>
      </div>
      <Tag color="processing">{{ tasks.length }} 个任务</Tag>
    </header>

    <div v-if="!tasks.length" class="sos-inline-empty">
      <strong>当前方案还没有任务模板</strong>
      <span>先在流程设计中配置履约节点，再为节点补充责任和完成条件。</span>
    </div>
    <div v-else class="sos-task-template-panel__workspace">
      <nav class="sos-task-template-panel__list" aria-label="任务模板目录">
        <button
          v-for="task in tasks"
          :key="task.id"
          type="button"
          :class="{ active: task.id === selectedId }"
          @click="selectedId = task.id"
        >
          <span class="sos-task-template-panel__list-index">{{ task.stage }}</span>
          <span><strong>{{ task.name }}</strong><small>{{ task.owner }} · {{ task.sla }}</small></span>
        </button>
      </nav>

      <div v-if="selectedTask" class="sos-task-template-panel__detail">
        <header>
          <div><span>当前任务</span><h4>{{ selectedTask.name }}</h4></div>
          <Tag :color="editable ? 'processing' : 'default'">{{ editable ? '草稿编辑态' : '只读预览' }}</Tag>
        </header>
        <div class="sos-form-grid">
          <label><span>任务名称</span><Input :value="selectedTask.name" :disabled="!editable" @update:value="patchSelected({ name: String($event) })" /></label>
          <label><span>负责人</span><Select :value="selectedTask.owner" :options="ownerOptions" :disabled="!editable" @update:value="patchSelected({ owner: String($event) })" /></label>
          <label><span>SLA 目标</span><Input :value="selectedTask.sla" :disabled="!editable" @update:value="patchSelected({ sla: String($event) })" /></label>
          <label><span>关联表单</span><Input :value="selectedTask.form" :disabled="!editable" @update:value="patchSelected({ form: String($event) })" /></label>
          <label><span>证据要求</span><Input :value="selectedTask.evidence" :disabled="!editable" @update:value="patchSelected({ evidence: String($event) })" /></label>
          <label class="span-2"><span>完成条件</span><Input.TextArea :value="selectedTask.completion" :rows="3" :disabled="!editable" @update:value="patchSelected({ completion: String($event) })" /></label>
        </div>
        <p class="sos-editor-note">{{ editable ? '修改会作用于当前浏览器中的草稿编辑态；保存仍由方案草稿命令统一提交。' : '当前接口未提供独立任务模板保存命令，页面展示绑定到方案版本的任务事实。' }}</p>
      </div>
    </div>
  </section>
</template>
