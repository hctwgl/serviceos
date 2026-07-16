<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { getAuthorizedTask, type TaskDetail } from '../api/taskDetail'
import { listTaskExecutionAttempts, type TaskExecutionAttemptPage } from '../api/taskAttempts'
import { getTaskAllowedActions, type TaskAllowedActions } from '../api/tasks'
import TaskCommandPanel from '../components/TaskCommandPanel.vue'
import TaskFormsEvidencePanel from '../components/TaskFormsEvidencePanel.vue'
import QueueTable from './QueueTable.vue'

const route = useRoute()
const taskId = computed(() => String(route.params.id ?? ''))

const loading = ref(false)
const error = ref<string | null>(null)
const detail = ref<TaskDetail | null>(null)
const attempts = ref<TaskExecutionAttemptPage | null>(null)
const allowedActions = ref<TaskAllowedActions | null>(null)
const allowedError = ref<string | null>(null)
const preparedResultRef = ref('')
const preparedResultDigest = ref('')

function onPreparedComplete(payload: { resultRef: string; resultDigest: string }) {
  preparedResultRef.value = payload.resultRef
  preparedResultDigest.value = payload.resultDigest
}

async function load() {
  loading.value = true
  error.value = null
  allowedError.value = null
  try {
    const [task, attemptPage, actions] = await Promise.all([
      getAuthorizedTask(taskId.value),
      listTaskExecutionAttempts(taskId.value, { limit: '20' }),
      getTaskAllowedActions(taskId.value),
    ])
    detail.value = task
    attempts.value = attemptPage
    allowedActions.value = actions.data
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载任务详情失败'
    detail.value = null
    attempts.value = null
    allowedActions.value = null
  } finally {
    loading.value = false
  }
}

const attemptRows = computed(() =>
  (attempts.value?.items ?? []).map((item) => ({
    attemptNo: item.attemptNo,
    resultCode: item.resultCode,
    errorCode: item.errorCode,
    startedAt: item.startedAt,
    finishedAt: item.finishedAt,
    nextRetryAt: item.nextRetryAt,
  })),
)

watch(taskId, () => {
  if (taskId.value) void load()
})
onMounted(() => {
  if (taskId.value) void load()
})
</script>

<template>
  <section class="detail">
    <header class="top">
      <div>
        <h2>任务详情</h2>
        <p class="meta">{{ taskId }}</p>
      </div>
      <button type="button" :disabled="loading" @click="load">刷新</button>
    </header>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-else-if="loading">加载中…</p>

    <template v-else-if="detail">
      <div class="grid">
        <article class="card">
          <h3>当前事实</h3>
          <dl>
            <div><dt>类型</dt><dd>{{ detail.task.taskType }} / {{ detail.task.taskKind }}</dd></div>
            <div><dt>状态</dt><dd>{{ detail.task.status }}</dd></div>
            <div><dt>优先级</dt><dd>{{ detail.task.priority }}</dd></div>
            <div><dt>责任人</dt><dd>{{ detail.responsibleUserId || detail.task.claimedBy || '—' }}</dd></div>
            <div><dt>formRef</dt><dd>{{ detail.formRef || '—' }}</dd></div>
            <div><dt>version</dt><dd>{{ detail.task.version }}</dd></div>
            <div><dt>asOf</dt><dd>{{ detail.asOf }}</dd></div>
          </dl>
          <p v-if="detail.task.workOrderId" class="links">
            <RouterLink :to="{ name: 'ADMIN.WORKORDER.WORKSPACE', params: { id: detail.task.workOrderId } }">
              打开工单工作区
            </RouterLink>
          </p>
        </article>

        <article class="card">
          <h3>命令</h3>
          <p v-if="allowedError" class="error">{{ allowedError }}</p>
          <TaskCommandPanel
            v-else-if="allowedActions"
            :task-id="taskId"
            :allowed-actions="allowedActions"
            :prepared-result-ref="preparedResultRef"
            :prepared-result-digest="preparedResultDigest"
            @executed="load"
          />
          <p v-else>暂无允许动作</p>
        </article>
      </div>

      <TaskFormsEvidencePanel :task-id="taskId" @prepared-complete="onPreparedComplete" />

      <QueueTable
        title="执行 Attempt 历史"
        :columns="['attemptNo', 'resultCode', 'errorCode', 'startedAt', 'finishedAt', 'nextRetryAt']"
        :rows="attemptRows"
        :loading="false"
        :error="null"
        :as-of="attempts?.asOf"
        :next-cursor="attempts?.nextCursor ?? null"
        @refresh="load"
        @next="() => undefined"
      />
    </template>
  </section>
</template>

<style scoped>
.detail {
  display: grid;
  gap: 1rem;
}
.top {
  display: flex;
  justify-content: space-between;
}
.meta {
  margin: 0.25rem 0 0;
  color: #627d98;
  font-family: ui-monospace, monospace;
  font-size: 0.85rem;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 1rem;
}
.card {
  background: #fff;
  border-radius: 12px;
  padding: 1rem 1.15rem;
  box-shadow: 0 1px 3px rgb(16 42 67 / 8%);
}
dl {
  margin: 0;
  display: grid;
  gap: 0.5rem;
}
dt {
  font-size: 0.78rem;
  color: #627d98;
}
dd {
  margin: 0.1rem 0 0;
}
.links {
  margin-top: 0.75rem;
}
.error {
  color: #9b1c1c;
}
button {
  border: 1px solid #bcccdc;
  background: #f0f4f8;
  border-radius: 6px;
  padding: 0.4rem 0.75rem;
  cursor: pointer;
}
</style>
