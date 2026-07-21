<script setup lang="ts">
import { statusLabel } from '../product/labels'
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import {
  getNetworkPortalException,
  type NetworkPortalExceptionItem,
} from '../api/networkPortal'

const props = defineProps<{ networkContextId: string | null }>()
const route = useRoute()
const exceptionId = computed(() => String(route.params.id ?? ''))
const detail = ref<NetworkPortalExceptionItem | null>(null)
const error = ref<string | null>(null)
const loading = ref(false)

async function load() {
  if (!props.networkContextId) {
    detail.value = null
    error.value = '请选择 NETWORK 上下文'
    return
  }
  if (!exceptionId.value) {
    detail.value = null
    error.value = '缺少 exceptionId'
    return
  }
  loading.value = true
  try {
    detail.value = await getNetworkPortalException(props.networkContextId, exceptionId.value)
    error.value = null
  } catch (err) {
    detail.value = null
    error.value = err instanceof Error ? err.message : '异常详情加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})
watch(
  () => [props.networkContextId, exceptionId.value] as const,
  () => {
    void load()
  },
)
</script>

<template>
  <section
    data-testid="network-portal-exception-detail"
    data-page-id="NETWORK.EXCEPTION.QUEUE"
  >
    <header class="top">
      <div>
        <p class="eyebrow">异常中心</p>
        <h2>异常详情</h2>
        <p class="meta" data-testid="exception-detail-id">{{ exceptionId }}</p>
      </div>
      <div class="actions">
        <RouterLink to="/network-portal/exceptions" data-testid="exception-back-to-queue">
          返回队列
        </RouterLink>
        <RouterLink
          v-if="detail?.workOrderId"
          :to="`/network-portal/work-orders/${detail.workOrderId}`"
          data-testid="exception-detail-primary-workspace"
        >
          打开工单工作区
        </RouterLink>
        <button type="button" :disabled="loading" data-testid="exception-detail-refresh" @click="load">
          刷新
        </button>
      </div>
    </header>
    <p class="hint">
      深链到可执行业务动作；Portal 不提供 ACK/resolve 空操作。
    </p>
    <p v-if="error" data-testid="network-portal-error">{{ error }}</p>
    <p v-else-if="loading" data-testid="exception-detail-loading">加载中…</p>
    <dl v-else-if="detail" data-testid="exception-detail-fields">
      <div><dt>状态</dt><dd data-testid="exception-detail-status">{{ detail.status ? statusLabel(detail.status) : '—' }}</dd></div>
      <div><dt>severity</dt><dd data-testid="exception-detail-severity">{{ detail.severity ? statusLabel(detail.severity) : '—' }}</dd></div>
      <div><dt>errorCode</dt><dd data-testid="exception-detail-error-code">{{ detail.errorCode }}</dd></div>
      <div><dt>category</dt><dd>{{ detail.category }}</dd></div>
      <div><dt>sourceType</dt><dd>{{ detail.sourceType }}</dd></div>
      <div><dt>projectId</dt><dd>{{ detail.projectId ?? '—' }}</dd></div>
      <div>
        <dt>workOrderId</dt>
        <dd>
          <RouterLink
            v-if="detail.workOrderId"
            :to="`/network-portal/work-orders/${detail.workOrderId}`"
            data-testid="exception-detail-work-order-deeplink"
          >
            {{ detail.workOrderId }}
          </RouterLink>
          <span v-else>—</span>
        </dd>
      </div>
      <div>
        <dt>taskId</dt>
        <dd>
          <RouterLink
            v-if="detail.taskId"
            :to="{ path: '/network-portal/tasks', query: { taskId: detail.taskId } }"
            data-testid="exception-detail-task-deeplink"
          >
            {{ detail.taskId }}
          </RouterLink>
          <span v-else>—</span>
        </dd>
      </div>
      <div>
        <dt>handlingTaskId</dt>
        <dd>
          <RouterLink
            v-if="detail.handlingTaskId"
            :to="{ path: '/network-portal/tasks', query: { taskId: detail.handlingTaskId } }"
            data-testid="exception-detail-handling-task-deeplink"
          >
            {{ detail.handlingTaskId }}
          </RouterLink>
          <span v-else>—</span>
        </dd>
      </div>
      <div><dt>occurrenceCount</dt><dd>{{ detail.occurrenceCount }}</dd></div>
      <div><dt>openedAt</dt><dd>{{ detail.openedAt }}</dd></div>
      <div><dt>lastDetectedAt</dt><dd>{{ detail.lastDetectedAt }}</dd></div>
      <div><dt>resolvedAt</dt><dd>{{ detail.resolvedAt ?? '—' }}</dd></div>
      <div><dt>resolutionCode</dt><dd>{{ detail.resolutionCode ?? '—' }}</dd></div>
      <div>
        <dt>allowedActions</dt>
        <dd data-testid="exception-detail-allowed-actions">
          {{ detail.allowedActions.length ? detail.allowedActions.join(', ') : '[]' }}
        </dd>
      </div>
    </dl>
  </section>
</template>

<style scoped>
.top {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: flex-start;
}
.meta,
.hint {
  color: #5b6573;
  font-size: 0.9rem;
}
.eyebrow {
  margin: 0 0 4px;
  color: var(--sos-primary-600);
  font-size: 12px;
  letter-spacing: 0.08em;
}
.actions {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  flex-wrap: wrap;
}
dl {
  display: grid;
  gap: 0.35rem;
  margin: 1rem 0;
}
dt {
  font-size: 0.75rem;
  color: #5b6573;
}
dd {
  margin: 0 0 0.35rem;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.85rem;
  word-break: break-all;
}
</style>
