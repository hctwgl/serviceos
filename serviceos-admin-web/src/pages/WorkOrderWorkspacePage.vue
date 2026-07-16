<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  getWorkOrderActivitySummary,
  getWorkOrderWorkspace,
  getWorkOrderWorkspaceSection,
  type SectionCode,
  type WorkOrderActivitySummary,
  type WorkOrderWorkspace,
  type WorkOrderWorkspaceSection,
} from '../api/workspace'

const route = useRoute()
const workOrderId = computed(() => String(route.params.id ?? ''))

const loading = ref(false)
const error = ref<string | null>(null)
const workspace = ref<WorkOrderWorkspace | null>(null)
const activity = ref<WorkOrderActivitySummary | null>(null)
const activeSection = ref<SectionCode>('TASKS')
const sectionLoading = ref(false)
const sectionError = ref<string | null>(null)
const sectionData = ref<WorkOrderWorkspaceSection | null>(null)

const sections: SectionCode[] = [
  'TASKS',
  'TIMELINE_AUDIT',
  'APPOINTMENTS_VISITS',
  'FORMS_EVIDENCE',
  'REVIEWS_CORRECTIONS',
  'INTEGRATION',
]

async function loadWorkspace() {
  loading.value = true
  error.value = null
  try {
    const [ws, act] = await Promise.all([
      getWorkOrderWorkspace(workOrderId.value),
      getWorkOrderActivitySummary(workOrderId.value),
    ])
    workspace.value = ws
    activity.value = act
    const firstAvailable = sections.find(
      (code) => ws.sectionAvailability[code] === 'AVAILABLE' || ws.sectionAvailability[code] === 'EMPTY',
    )
    activeSection.value = firstAvailable ?? 'TASKS'
    await loadSection(activeSection.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载工作区失败'
    workspace.value = null
    activity.value = null
  } finally {
    loading.value = false
  }
}

async function loadSection(section: SectionCode) {
  activeSection.value = section
  sectionLoading.value = true
  sectionError.value = null
  try {
    sectionData.value = await getWorkOrderWorkspaceSection(workOrderId.value, section, { limit: '20' })
  } catch (err) {
    sectionError.value = err instanceof Error ? err.message : '加载区块失败'
    sectionData.value = null
  } finally {
    sectionLoading.value = false
  }
}

const sectionPreview = computed(() => {
  const data = sectionData.value
  if (!data) return '—'
  const payload =
    data.tasks ??
    data.timeline ??
    data.appointmentsVisits ??
    data.formsEvidence ??
    data.reviewsCorrections ??
    data.integration
  return JSON.stringify(payload, null, 2)
})

watch(workOrderId, () => {
  if (workOrderId.value) {
    void loadWorkspace()
  }
})

onMounted(() => {
  if (workOrderId.value) {
    void loadWorkspace()
  }
})
</script>

<template>
  <section class="workspace">
    <header class="top">
      <div>
        <h2>工单工作区</h2>
        <p class="meta">{{ workOrderId }}</p>
      </div>
      <button type="button" :disabled="loading" @click="loadWorkspace">刷新</button>
    </header>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-else-if="loading">加载中…</p>

    <template v-else-if="workspace">
      <div class="grid">
        <article class="card">
          <h3>概览</h3>
          <dl>
            <div><dt>状态</dt><dd>{{ workspace.header.status }}</dd></div>
            <div><dt>项目</dt><dd>{{ workspace.header.projectId }}</dd></div>
            <div><dt>外部单号</dt><dd>{{ workspace.header.externalOrderCode || '—' }}</dd></div>
            <div><dt>时间线 freshness</dt><dd>{{ workspace.timelineFreshnessStatus }}</dd></div>
            <div><dt>asOf</dt><dd>{{ workspace.meta.asOf }}</dd></div>
            <div><dt>allowed-actions</dt><dd>{{ workspace.allowedActionLink || '—' }}</dd></div>
          </dl>
        </article>

        <article class="card">
          <h3>当前任务 / 责任 / SLA / 异常</h3>
          <dl>
            <div>
              <dt>当前任务</dt>
              <dd v-if="workspace.currentTaskSummary">
                {{ workspace.currentTaskSummary.taskType }} /
                {{ workspace.currentTaskSummary.status }}
                <small>{{ workspace.currentTaskSummary.taskId }}</small>
              </dd>
              <dd v-else>—</dd>
            </div>
            <div>
              <dt>服务责任</dt>
              <dd v-if="workspace.serviceAssignmentSummary">
                network {{ String(workspace.serviceAssignmentSummary.networkId ?? '—') }} /
                tech {{ String(workspace.serviceAssignmentSummary.technicianId ?? '—') }}
              </dd>
              <dd v-else>不可用或缺失权</dd>
            </div>
            <div>
              <dt>SLA</dt>
              <dd v-if="workspace.slaSummary">
                open {{ Number(workspace.slaSummary.openCount ?? 0) }} /
                breached {{ Number(workspace.slaSummary.breachedCount ?? 0) }}
              </dd>
              <dd v-else>不可用或缺失权</dd>
            </div>
            <div>
              <dt>异常</dt>
              <dd v-if="workspace.exceptionSummary">
                open {{ Number(workspace.exceptionSummary.openCount ?? 0) }}
              </dd>
              <dd v-else>不可用或缺失权</dd>
            </div>
          </dl>
        </article>

        <article class="card">
          <h3>最近活动</h3>
          <ul v-if="activity?.items?.length">
            <li v-for="(item, index) in activity.items" :key="index">
              <strong>{{ item.eventType || item.type || 'event' }}</strong>
              <span>{{ item.occurredAt || '—' }}</span>
              <small>{{ item.resourceType }} {{ item.resourceId }}</small>
            </li>
          </ul>
          <p v-else>暂无活动摘要</p>
        </article>
      </div>

      <article class="card sections">
        <h3>按需区块</h3>
        <div class="tabs">
          <button
            v-for="code in sections"
            :key="code"
            type="button"
            :class="{ active: activeSection === code }"
            :disabled="workspace.sectionAvailability[code] === 'UNAVAILABLE'"
            @click="loadSection(code)"
          >
            {{ code }}
            <em>{{ workspace.sectionAvailability[code] || '?' }}</em>
          </button>
        </div>
        <p v-if="sectionError" class="error">{{ sectionError }}</p>
        <p v-else-if="sectionLoading">区块加载中…</p>
        <pre v-else>{{ sectionPreview }}</pre>
      </article>
    </template>
  </section>
</template>

<style scoped>
.workspace {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
.meta {
  margin: 0.25rem 0 0;
  color: #627d98;
  font-family: ui-monospace, monospace;
  font-size: 0.85rem;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 1rem;
}
.card {
  background: #fff;
  border-radius: 12px;
  padding: 1rem 1.15rem;
  box-shadow: 0 1px 3px rgb(16 42 67 / 8%);
}
h2,
h3 {
  margin: 0 0 0.75rem;
}
dl {
  margin: 0;
  display: grid;
  gap: 0.55rem;
}
dt {
  font-size: 0.78rem;
  color: #627d98;
}
dd {
  margin: 0.1rem 0 0;
}
dd small {
  display: block;
  color: #829ab1;
  font-family: ui-monospace, monospace;
}
ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.55rem;
}
li {
  display: grid;
  gap: 0.15rem;
}
li span,
li small {
  color: #627d98;
  font-size: 0.85rem;
}
.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin-bottom: 0.75rem;
}
.tabs button {
  border: 1px solid #bcccdc;
  background: #f0f4f8;
  border-radius: 999px;
  padding: 0.35rem 0.7rem;
  cursor: pointer;
}
.tabs button.active {
  background: #243b53;
  color: #fff;
  border-color: #243b53;
}
.tabs button em {
  font-style: normal;
  margin-left: 0.35rem;
  opacity: 0.75;
  font-size: 0.75rem;
}
.tabs button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
pre {
  margin: 0;
  max-height: 420px;
  overflow: auto;
  background: #f0f4f8;
  border-radius: 8px;
  padding: 0.75rem;
  font-size: 0.8rem;
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
