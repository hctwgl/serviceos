<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  getTechnicianTaskDetail,
  type TechnicianPortalTaskDetail,
} from '../api/technicianPortal'

const props = defineProps<{ technicianContextId: string | null }>()
const route = useRoute()
const detail = ref<TechnicianPortalTaskDetail | null>(null)
const error = ref<string | null>(null)

async function load() {
  const taskId = String(route.params.id ?? '')
  if (!props.technicianContextId) {
    detail.value = null
    error.value = '请选择 TECHNICIAN 上下文'
    return
  }
  if (!taskId) {
    detail.value = null
    error.value = '缺少 taskId'
    return
  }
  try {
    detail.value = await getTechnicianTaskDetail(props.technicianContextId, taskId)
    error.value = null
  } catch (err) {
    detail.value = null
    error.value = err instanceof Error ? err.message : '任务详情加载失败'
  }
}

onMounted(() => {
  void load()
})
watch([() => props.technicianContextId, () => route.params.id], () => {
  void load()
})
</script>

<template>
  <section data-testid="technician-portal-task-detail" data-page-id="TECHNICIAN.TASK.DETAIL">
    <header class="top">
      <div>
        <RouterLink to="/technician-portal/task-feed">← 返回任务 Feed</RouterLink>
        <h2>任务详情</h2>
        <p class="hint">M244：当前 ACTIVE 责任任务的在线非 PII 详情与联系历史；执行授权仍以服务端命令为准。</p>
      </div>
      <button type="button" data-testid="technician-task-detail-refresh" @click="load">刷新</button>
    </header>

    <p v-if="error" class="error" data-testid="technician-task-detail-error">{{ error }}</p>
    <template v-else-if="detail">
      <dl class="summary" data-testid="technician-task-detail-summary">
        <div><dt>taskId</dt><dd data-testid="technician-task-detail-task-id">{{ detail.taskId }}</dd></div>
        <div><dt>workOrderId</dt><dd>{{ detail.workOrderId }}</dd></div>
        <div><dt>projectId</dt><dd>{{ detail.projectId ?? '—' }}</dd></div>
        <div><dt>状态</dt><dd data-testid="technician-task-detail-status">{{ detail.taskStatus }}</dd></div>
        <div><dt>阶段</dt><dd>{{ detail.stageCode }}</dd></div>
        <div><dt>任务类型</dt><dd>{{ detail.taskType }} / {{ detail.taskKind }}</dd></div>
        <div><dt>业务类型</dt><dd>{{ detail.businessType ?? '—' }}</dd></div>
        <div><dt>执行保护</dt><dd>{{ detail.executionGuarded ? '已保护，暂不可执行' : '未保护' }}</dd></div>
        <div><dt>资源版本</dt><dd>{{ detail.resourceVersion }}</dd></div>
        <div><dt>asOf</dt><dd>{{ detail.asOf }}</dd></div>
      </dl>

      <section class="appointments">
        <div class="section-title">
          <h3>预约摘要</h3>
          <RouterLink
            :to="{ path: '/technician-portal/schedule', query: { taskId: detail.taskId } }"
            data-testid="technician-task-detail-schedule-link"
          >
            查看日程
          </RouterLink>
        </div>
        <table v-if="detail.appointments.length > 0" data-testid="technician-task-detail-appointments">
          <thead>
            <tr><th>类型</th><th>状态</th><th>开始</th><th>结束</th><th>时区</th></tr>
          </thead>
          <tbody>
            <tr v-for="appointment in detail.appointments" :key="appointment.appointmentId">
              <td>{{ appointment.type }}</td>
              <td>{{ appointment.status }}</td>
              <td>{{ appointment.windowStart ?? '—' }}</td>
              <td>{{ appointment.windowEnd ?? '—' }}</td>
              <td>{{ appointment.timezone ?? '—' }}</td>
            </tr>
          </tbody>
        </table>
        <p v-else>暂无预约</p>
      </section>

      <section class="contacts">
        <div class="section-title">
          <h3>联系历史</h3>
          <span>仅安全事实摘要</span>
        </div>
        <table v-if="detail.contactAttempts.length > 0" data-testid="technician-task-detail-contact-attempts">
          <thead>
            <tr><th>渠道</th><th>结果</th><th>开始</th><th>结束</th><th>下次联系</th></tr>
          </thead>
          <tbody>
            <tr v-for="attempt in detail.contactAttempts" :key="attempt.contactAttemptId">
              <td>{{ attempt.channel }}</td>
              <td>{{ attempt.resultCode }}</td>
              <td>{{ attempt.startedAt }}</td>
              <td>{{ attempt.endedAt }}</td>
              <td>{{ attempt.nextContactAt ?? '—' }}</td>
            </tr>
          </tbody>
        </table>
        <p v-else data-testid="technician-task-detail-no-contact-attempts">暂无联系记录</p>
      </section>

      <p class="boundary" data-testid="technician-task-detail-boundary">
        本切片不返回地址、联系人、联系对象引用、自由文本、录音引用、操作者标识、表单值、资料文件、配置源码或离线工作包。
      </p>
    </template>
  </section>
</template>

<style scoped>
.top,
.section-title {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
}
.hint,
.boundary {
  color: #5b6573;
}
.error {
  color: #a11;
}
.summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 0.75rem;
  margin: 1rem 0;
}
.summary div {
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 0.75rem;
}
.summary dt {
  color: #5b6573;
  font-size: 0.8rem;
}
.summary dd {
  margin: 0.3rem 0 0;
  overflow-wrap: anywhere;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th,
td {
  border-bottom: 1px solid #e5e7eb;
  padding: 0.5rem;
  text-align: left;
}
.boundary {
  margin-top: 1rem;
  padding: 0.75rem;
  background: #f7f9fc;
}
</style>
