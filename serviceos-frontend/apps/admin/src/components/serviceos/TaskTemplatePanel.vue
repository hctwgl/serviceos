<script setup lang="ts">
import { Tag } from '@serviceos/design-system'
import { computed, ref, watch } from 'vue'

export type TaskTemplateItem = {
  id: string
  name: string
  owner: string
  input: string
  output: string
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

const selectedId = ref<string>()
const selectedTask = computed(() => props.tasks.find((task) => task.id === selectedId.value))

watch(
  () => props.tasks,
  (tasks) => {
    if (!tasks.some((task) => task.id === selectedId.value)) selectedId.value = tasks[0]?.id
  },
  { immediate: true },
)

</script>

<template>
  <section class="sos-task-template-panel">
    <header class="sos-section-heading">
      <div>
        <span class="sos-eyebrow">任务模板</span>
        <h3>任务模板设计</h3>
        <span>每个任务对应一个责任交付，不把任务降级为字段列表。</span>
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
          <div><span>{{ selectedTask.stage }}</span><h4>{{ selectedTask.name }}</h4></div>
          <Tag :color="editable ? 'processing' : 'default'">{{ editable ? '活动草稿' : '方案事实' }}</Tag>
        </header>
        <dl class="sos-task-template-facts">
          <div><dt>责任角色</dt><dd>{{ selectedTask.owner }}</dd></div>
          <div><dt>SLA</dt><dd>{{ selectedTask.sla }}</dd></div>
          <div><dt>输入</dt><dd>{{ selectedTask.input }}</dd></div>
          <div><dt>输出</dt><dd>{{ selectedTask.output }}</dd></div>
          <div><dt>关联表单</dt><dd>{{ selectedTask.form }}</dd></div>
          <div><dt>证据要求</dt><dd>{{ selectedTask.evidence }}</dd></div>
          <div class="span-2"><dt>完成条件</dt><dd>{{ selectedTask.completion }}</dd></div>
        </dl>
        <p class="sos-editor-note">{{ editable ? '编辑仍需进入活动草稿，由方案命令统一保存。' : '当前接口未提供独立任务模板编辑命令，以上内容来自方案阶段与编译预览。' }}</p>
      </div>
    </div>
  </section>
</template>
