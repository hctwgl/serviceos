<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { getEvidenceSetSnapshot, type EvidenceSetSnapshot } from '../api/formsEvidence'

const route = useRoute()
const snapshotId = computed(() => String(route.params.id ?? ''))
const loading = ref(false)
const error = ref<string | null>(null)
const detail = ref<EvidenceSetSnapshot | null>(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    detail.value = await getEvidenceSetSnapshot(snapshotId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载资料快照详情失败'
    detail.value = null
  } finally {
    loading.value = false
  }
}

watch(snapshotId, () => {
  if (snapshotId.value) void load()
})
onMounted(() => {
  if (snapshotId.value) void load()
})
</script>

<template>
  <section class="detail">
    <header class="top">
      <div>
        <h2>资料快照详情</h2>
        <p class="meta">{{ snapshotId }}</p>
      </div>
      <button type="button" :disabled="loading" @click="load">刷新</button>
    </header>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-else-if="loading">加载中…</p>
    <template v-else-if="detail">
      <article class="card">
        <dl>
          <div><dt>purpose</dt><dd>{{ detail.purpose }}</dd></div>
          <div>
            <dt>projectId</dt>
            <dd>
              <RouterLink
                :to="{ name: 'ADMIN.PROJECT.DETAIL', params: { id: detail.projectId } }"
              >
                {{ detail.projectId }}
              </RouterLink>
            </dd>
          </div>
          <div>
            <dt>taskId</dt>
            <dd>
              <RouterLink :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: detail.taskId } }">
                {{ detail.taskId }}
              </RouterLink>
            </dd>
          </div>
          <div><dt>memberCount</dt><dd>{{ detail.memberCount }}</dd></div>
          <div><dt>contentDigest</dt><dd>{{ detail.contentDigest }}</dd></div>
          <div><dt>resolutionId</dt><dd>{{ detail.resolutionId || '—' }}</dd></div>
          <div><dt>createdBy</dt><dd>{{ detail.createdBy || '—' }}</dd></div>
          <div><dt>createdAt</dt><dd>{{ detail.createdAt }}</dd></div>
        </dl>
        <p class="links evidence-snapshot-cross-links">
          <RouterLink
            :to="{ name: 'ADMIN.PROJECT.DETAIL', params: { id: detail.projectId } }"
          >
            打开项目 {{ detail.projectId }}
          </RouterLink>
          <RouterLink :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: detail.taskId } }">
            任务详情
          </RouterLink>
        </p>
      </article>
      <article v-if="detail.members?.length" class="card">
        <h3>成员资料项</h3>
        <p class="links evidence-snapshot-member-links">
          <RouterLink
            v-for="member in detail.members"
            :key="member.memberId"
            :to="{
              name: 'ADMIN.EVIDENCE_ITEM.DETAIL',
              params: { id: member.evidenceItemId },
            }"
          >
            打开资料项 {{ member.evidenceItemId }}
          </RouterLink>
        </p>
      </article>
      <pre class="dump">{{
        JSON.stringify(
          {
            members: detail.members ?? [],
            eligibilitySummary: detail.eligibilitySummary ?? null,
          },
          null,
          2,
        )
      }}</pre>
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
dl {
  margin: 0;
  display: grid;
  gap: 0.45rem;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}
dt {
  font-size: 0.78rem;
  color: #627d98;
}
dd {
  margin: 0.1rem 0 0;
  word-break: break-all;
}
.links {
  display: flex;
  gap: 0.75rem;
  margin-top: 0.75rem;
}
.dump {
  background: #f0f4f8;
  border-radius: 8px;
  padding: 0.75rem;
  overflow: auto;
  max-height: 320px;
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
