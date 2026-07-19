<script setup lang="ts">
import { statusLabel } from '../product/labels'
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import {
  getNetworkPortalQualification,
  type NetworkPortalQualificationItem,
} from '../api/networkPortal'

const props = defineProps<{ networkContextId: string | null }>()
const route = useRoute()
const qualificationId = computed(() => String(route.params.id ?? ''))
const detail = ref<NetworkPortalQualificationItem | null>(null)
const error = ref<string | null>(null)
const loading = ref(false)

async function load() {
  if (!props.networkContextId) {
    detail.value = null
    error.value = '请选择 NETWORK 上下文'
    return
  }
  if (!qualificationId.value) {
    detail.value = null
    error.value = '缺少 qualificationId'
    return
  }
  loading.value = true
  try {
    detail.value = await getNetworkPortalQualification(
      props.networkContextId,
      qualificationId.value,
    )
    error.value = null
  } catch (err) {
    detail.value = null
    error.value = err instanceof Error ? err.message : '资质详情加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})
watch(
  () => [props.networkContextId, qualificationId.value] as const,
  () => {
    void load()
  },
)
</script>

<template>
  <section
    data-testid="network-portal-qualification-detail"
    data-page-id="NETWORK.QUALIFICATION"
  >
    <header class="top">
      <div>
        <h2>资质详情</h2>
        <p class="meta" data-testid="qualification-detail-id">{{ qualificationId }}</p>
      </div>
      <div class="actions">
        <RouterLink
          to="/network-portal/qualifications"
          data-testid="qualification-back-to-list"
        >
          返回列表
        </RouterLink>
        <button
          type="button"
          :disabled="loading"
          data-testid="qualification-detail-refresh"
          @click="load"
        >
          刷新
        </button>
      </div>
    </header>
    <p class="hint">只读详情（复用 M205 GET）；网点不能自批，不提供 decide/approve。</p>
    <p v-if="error" data-testid="network-portal-error">{{ error }}</p>
    <p v-else-if="loading" data-testid="qualification-detail-loading">加载中…</p>
    <dl v-else-if="detail" data-testid="qualification-detail-fields">
      <div><dt>状态</dt><dd data-testid="qualification-detail-status">{{ detail.status ? statusLabel(detail.status) : '—' }}</dd></div>
      <div><dt>qualificationCode</dt><dd>{{ detail.qualificationCode }}</dd></div>
      <div><dt>technicianProfileId</dt><dd>{{ detail.technicianProfileId }}</dd></div>
      <div><dt>validFrom</dt><dd>{{ detail.validFrom }}</dd></div>
      <div><dt>validTo</dt><dd>{{ detail.validTo ?? '—' }}</dd></div>
      <div><dt>submittedBy</dt><dd>{{ detail.submittedBy }}</dd></div>
      <div><dt>submittedAt</dt><dd>{{ detail.submittedAt }}</dd></div>
      <div><dt>decidedBy</dt><dd data-testid="qualification-detail-decided-by">{{ detail.decidedBy ?? '—' }}</dd></div>
      <div><dt>decidedAt</dt><dd>{{ detail.decidedAt ?? '—' }}</dd></div>
      <div>
        <dt>decisionReason</dt>
        <dd data-testid="qualification-detail-decision-reason">
          {{ detail.decisionReason ?? '—' }}
        </dd>
      </div>
      <div><dt>version</dt><dd data-testid="qualification-detail-version">{{ detail.version }}</dd></div>
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
.actions {
  display: flex;
  gap: 0.75rem;
  align-items: center;
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
