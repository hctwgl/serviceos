<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  getAuthorizedProject,
  listAuthorizedProjectScopeRevisions,
  reviseProjectScopeRelations,
  type ProjectDetail,
  type ProjectScopeRelationRevisionPage,
} from '../api/projectDetail'
import { recordRecentVisit } from '../recent/recordRecentVisit'
import QueueTable from './QueueTable.vue'

const route = useRoute()
const projectId = computed(() => String(route.params.id ?? ''))

const loading = ref(false)
const busy = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)
const detail = ref<ProjectDetail | null>(null)
const revisions = ref<ProjectScopeRelationRevisionPage | null>(null)
const revisionCursor = ref<string | undefined>()
const regionCodes = ref('')
const networkIds = ref('')
const reason = ref('ADMIN_SCOPE_ADJUST')

async function loadDetail() {
  loading.value = true
  error.value = null
  try {
    detail.value = await getAuthorizedProject(projectId.value)
    await loadRevisions()
    const latest = revisions.value?.items?.[0]
    if (latest) {
      regionCodes.value = latest.regionCodes.join(',')
      networkIds.value = latest.networkIds.join(',')
    } else if (detail.value.project.regionCodes || detail.value.project.networkIds) {
      regionCodes.value = (detail.value.project.regionCodes ?? []).join(',')
      networkIds.value = (detail.value.project.networkIds ?? []).join(',')
    }
    recordRecentVisit({
      resourceType: 'PROJECT',
      resourceId: projectId.value,
      pageId: 'ADMIN.PROJECT.DETAIL',
      displayRef: detail.value.project.name || detail.value.project.code,
    })
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载项目详情失败'
    detail.value = null
    revisions.value = null
  } finally {
    loading.value = false
  }
}

async function loadRevisions(next?: string) {
  const page = await listAuthorizedProjectScopeRevisions(projectId.value, {
    cursor: next,
    limit: '20',
  })
  revisions.value = page
  revisionCursor.value = page.nextCursor ?? undefined
}

async function reviseScope() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    const aggregateVersion =
      revisions.value?.items?.[0]?.aggregateVersion ?? detail.value?.project.version
    if (!aggregateVersion) throw new Error('缺少 aggregateVersion')
    const result = await reviseProjectScopeRelations(projectId.value, aggregateVersion, {
      regionCodes: regionCodes.value
        .split(/[,\s]+/)
        .map((v) => v.trim())
        .filter(Boolean),
      networkIds: networkIds.value
        .split(/[,\s]+/)
        .map((v) => v.trim())
        .filter(Boolean),
      reason: reason.value.trim(),
    })
    message.value = `已修订范围 revision=${result.data.revisionId} / v${result.data.aggregateVersion}`
    await loadDetail()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '修订范围失败'
  } finally {
    busy.value = false
  }
}

const revisionRows = computed(() =>
  (revisions.value?.items ?? []).map((item) => ({
    revisionId: item.revisionId,
    aggregateVersion: item.aggregateVersion,
    revisedAt: item.revisedAt,
    reason: item.reason,
    regionCodes: item.regionCodes.join(', '),
    networkIds: item.networkIds.join(', '),
  })),
)

watch(projectId, () => {
  if (projectId.value) void loadDetail()
})
onMounted(() => {
  if (projectId.value) void loadDetail()
})
</script>

<template>
  <section class="detail">
    <header class="top">
      <div>
        <h2>项目详情</h2>
        <p class="meta">{{ projectId }}</p>
      </div>
      <button type="button" :disabled="loading" @click="loadDetail">刷新</button>
    </header>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-else-if="loading">加载中…</p>

    <template v-else-if="detail">
      <article class="card">
        <h3>{{ detail.project.name }}</h3>
        <dl>
          <div><dt>code</dt><dd>{{ detail.project.code }}</dd></div>
          <div><dt>clientId</dt><dd>{{ detail.project.clientId }}</dd></div>
          <div><dt>status</dt><dd>{{ detail.project.status }}</dd></div>
          <div><dt>startsOn</dt><dd>{{ detail.project.startsOn }}</dd></div>
          <div><dt>endsOn</dt><dd>{{ detail.project.endsOn || '—' }}</dd></div>
          <div><dt>version</dt><dd>{{ detail.project.version }}</dd></div>
          <div><dt>asOf</dt><dd>{{ detail.asOf }}</dd></div>
        </dl>
      </article>

      <article class="card form">
        <h3>整组修订 REGION/NETWORK</h3>
        <p class="hint">空数组表示终止该类型全部当前关系；If-Match 使用当前 scope aggregateVersion。</p>
        <label>regionCodes<input v-model="regionCodes" placeholder="R1,R2" /></label>
        <label>networkIds<input v-model="networkIds" placeholder="N1,N2" /></label>
        <label>reason<input v-model="reason" /></label>
        <button type="button" :disabled="busy" @click="reviseScope">revise-scope-relations</button>
        <p v-if="message" class="ok">{{ message }}</p>
      </article>

      <QueueTable
        title="范围修订历史"
        :columns="['revisionId', 'aggregateVersion', 'revisedAt', 'reason', 'regionCodes', 'networkIds']"
        :rows="revisionRows"
        :loading="false"
        :error="null"
        :as-of="revisions?.asOf"
        :next-cursor="revisionCursor ?? null"
        @refresh="loadRevisions()"
        @next="loadRevisions(revisionCursor)"
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
.card {
  background: #fff;
  border-radius: 12px;
  padding: 1rem 1.15rem;
  box-shadow: 0 1px 3px rgb(16 42 67 / 8%);
}
.form {
  display: grid;
  gap: 0.55rem;
}
.hint {
  margin: 0;
  color: #627d98;
  font-size: 0.85rem;
}
label {
  display: grid;
  gap: 0.25rem;
  font-size: 0.85rem;
  color: #486581;
}
input {
  border: 1px solid #bcccdc;
  border-radius: 6px;
  padding: 0.4rem 0.65rem;
  font-family: ui-monospace, monospace;
}
dl {
  margin: 0;
  display: grid;
  gap: 0.5rem;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}
dt {
  font-size: 0.78rem;
  color: #627d98;
}
dd {
  margin: 0.1rem 0 0;
}
.error {
  color: #9b1c1c;
}
.ok {
  color: #054e31;
  margin: 0;
}
button {
  border: 1px solid #bcccdc;
  background: #243b53;
  color: #fff;
  border-color: #243b53;
  border-radius: 6px;
  padding: 0.4rem 0.75rem;
  cursor: pointer;
}
</style>
