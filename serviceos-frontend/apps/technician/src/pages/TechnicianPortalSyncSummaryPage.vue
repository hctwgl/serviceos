<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import {
  getTechnicianSyncSummary,
  type TechnicianPortalSyncSummary,
} from '../api/technicianPortal'
import { userFacingError } from '../api/client'
import { formatDateTime } from '../product/labels'

const props = defineProps<{ technicianContextId: string | null }>()
const summary = ref<TechnicianPortalSyncSummary | null>(null)
const error = ref<string | null>(null)
const loading = ref(false)

const attentionCount = computed(() => {
  if (!summary.value) return 0
  return summary.value.pendingFeedItemCount + summary.value.tombstoneCount
})

async function load() {
  if (!props.technicianContextId) {
    summary.value = null
    error.value = '请选择 TECHNICIAN 上下文'
    loading.value = false
    return
  }
  loading.value = true
  try {
    summary.value = await getTechnicianSyncSummary(props.technicianContextId)
    error.value = null
  } catch (err) {
    summary.value = null
    error.value = userFacingError(err, '同步摘要加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})
watch(
  () => props.technicianContextId,
  () => {
    void load()
  },
)
</script>

<template>
  <section data-testid="technician-portal-sync-summary" class="sync-page">
    <header class="top">
      <div>
        <p class="eyebrow">同步与冲突</p>
        <h2>同步中心</h2>
        <p class="hint">
          H5 仅展示服务端同步摘要与责任变化提示；不承诺离线命令队列、后台上传或杀进程恢复。
        </p>
      </div>
      <button type="button" class="ghost" data-testid="technician-sync-refresh" @click="load">刷新</button>
    </header>

    <p v-if="loading && !summary" data-testid="technician-sync-loading">正在加载同步摘要…</p>
    <p v-else-if="error" data-testid="technician-portal-error">{{ error }}</p>
    <template v-else-if="summary">
      <section class="attention" data-testid="technician-sync-attention">
        <strong>{{ attentionCount }}</strong>
        <span>项需要关注（待处理任务 + 失效/改派 Tombstone）</span>
      </section>

      <dl data-testid="technician-sync-summary-meta" class="meta">
        <div>
          <dt>所属网点</dt>
          <dd data-testid="technician-sync-network-id">{{ summary.networkId }}</dd>
        </div>
        <div>
          <dt>统计时间</dt>
          <dd>
            <span data-testid="technician-sync-as-of">{{ summary.asOf }}</span>
            <span class="muted">（{{ formatDateTime(summary.asOf) }}）</span>
          </dd>
        </div>
      </dl>

      <div class="cards" data-testid="technician-sync-summary-counts">
        <article class="card">
          <h3>待处理 Feed</h3>
          <p class="count">
            <RouterLink to="/technician-portal/task-feed" data-testid="technician-sync-feed-deeplink">
              {{ summary.pendingFeedItemCount }}
            </RouterLink>
          </p>
          <p class="muted">打开今日任务继续处理</p>
        </article>
        <article class="card">
          <h3>预约窗口</h3>
          <p class="count">
            <RouterLink to="/technician-portal/schedule" data-testid="technician-sync-schedule-deeplink">
              {{ summary.appointmentWindowCount }}
            </RouterLink>
          </p>
          <p class="muted">查看今日/未来预约</p>
        </article>
        <article class="card" data-tone="warning">
          <h3>失效 / 改派（Tombstone）</h3>
          <p class="count">
            <RouterLink to="/technician-portal/task-feed" data-testid="technician-sync-tombstone-deeplink">
              {{ summary.tombstoneCount }}
            </RouterLink>
          </p>
          <p class="muted">责任变化后本地成果不得静默覆盖；请回到任务列表确认</p>
        </article>
      </div>

      <section class="guidance" data-testid="technician-sync-conflict-guidance">
        <h3>冲突与恢复指引</h3>
        <ul>
          <li>任务已改派：停止提交，联系网点；本地未同步成果需隔离确认。</li>
          <li>数据版本变化：刷新任务详情后重新确认，不自动最后写入覆盖。</li>
          <li>权限/责任失效：停止上传；重新领取有效任务。</li>
          <li>已幂等接受：以服务器结果为准并清理重复待办。</li>
        </ul>
        <p class="muted gap-note">
          UI_DATA_GAP：离线 OfflineCommand 队列、冲突命令明细、尝试次数与服务器结果绑定读模型尚未对 H5 正式交付；本页只展示在线摘要，不伪造离线队列。
        </p>
      </section>
    </template>
  </section>
</template>

<style scoped>
.sync-page {
  display: grid;
  gap: 12px;
}
.top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}
.eyebrow {
  margin: 0 0 4px;
  color: var(--sos-primary-600);
  font-size: 12px;
  letter-spacing: 0.08em;
}
.top h2 {
  margin: 0 0 4px;
  font-size: 22px;
}
.hint,
.muted,
.gap-note {
  color: var(--sos-color-text-tertiary);
  font-size: 0.9rem;
}
.attention {
  display: flex;
  gap: 10px;
  align-items: center;
  border: 1px solid var(--sos-color-status-warning-border, #ffe58f);
  background: var(--sos-color-status-warning-bg, #fffbe6);
  border-radius: 12px;
  padding: 12px;
}
.attention strong {
  font-size: 28px;
  color: var(--sos-color-status-warning-fg, #d48806);
}
.meta {
  margin: 0;
  display: grid;
  gap: 0.35rem;
}
.meta dd {
  margin: 0 0 0.25rem;
  word-break: break-all;
}
.cards {
  display: grid;
  gap: 10px;
}
.card {
  border: 1px solid var(--sos-color-border-default, #e5e7eb);
  border-radius: 12px;
  background: #fff;
  padding: 12px;
}
.card[data-tone='warning'] {
  border-color: var(--sos-color-status-warning-border, #ffe58f);
  background: var(--sos-color-status-warning-bg, #fffbe6);
}
.card h3 {
  margin: 0 0 6px;
  font-size: 14px;
}
.count {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
}
.guidance {
  border: 1px solid var(--sos-color-border-default, #e5e7eb);
  border-radius: 12px;
  background: #fff;
  padding: 12px;
}
.guidance h3 {
  margin: 0 0 8px;
  font-size: 15px;
}
.guidance ul {
  margin: 0;
  padding-left: 18px;
  color: var(--sos-color-text-secondary, #4b5563);
  font-size: 13px;
}
button.ghost {
  border: 1px solid var(--sos-color-border-default, #e5e7eb);
  background: #fff;
  border-radius: 10px;
  min-height: 40px;
  padding: 0 12px;
}
</style>
