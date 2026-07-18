<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  getNetworkPortalTechnicianMembership,
  type NetworkPortalMembershipItem,
} from '../api/networkPortal'

const props = defineProps<{ networkContextId: string | null }>()
const route = useRoute()
const membershipId = computed(() => String(route.params.id ?? ''))
const detail = ref<NetworkPortalMembershipItem | null>(null)
const error = ref<string | null>(null)
const loading = ref(false)

async function load() {
  if (!props.networkContextId) {
    detail.value = null
    error.value = '请选择 NETWORK 上下文'
    return
  }
  if (!membershipId.value) {
    detail.value = null
    error.value = '缺少 membershipId'
    return
  }
  loading.value = true
  try {
    detail.value = await getNetworkPortalTechnicianMembership(
      props.networkContextId,
      membershipId.value,
    )
    error.value = null
  } catch (err) {
    detail.value = null
    error.value = err instanceof Error ? err.message : '师傅关系详情加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})
watch(
  () => [props.networkContextId, membershipId.value] as const,
  () => {
    void load()
  },
)
</script>

<template>
  <section
    data-testid="network-portal-membership-detail"
    data-page-id="NETWORK.TECHNICIAN.LIST"
  >
    <header class="top">
      <div>
        <h2>师傅关系详情</h2>
        <p class="meta" data-testid="membership-detail-id">{{ membershipId }}</p>
      </div>
      <div class="actions">
        <RouterLink to="/network-portal/technicians" data-testid="membership-back-to-list">
          返回师傅列表
        </RouterLink>
        <button
          type="button"
          :disabled="loading"
          data-testid="membership-detail-refresh"
          @click="load"
        >
          刷新
        </button>
      </div>
    </header>
    <p class="hint">
      只读详情（复用 M206 GET）；终止写操作仍在师傅列表表单，使用本页 version 作为 If-Match 来源。
    </p>
    <p v-if="error" data-testid="network-portal-error">{{ error }}</p>
    <p v-else-if="loading" data-testid="membership-detail-loading">加载中…</p>
    <dl v-else-if="detail" data-testid="membership-detail-fields">
      <div><dt>status</dt><dd data-testid="membership-detail-status">{{ detail.status }}</dd></div>
      <div><dt>serviceNetworkId</dt><dd>{{ detail.serviceNetworkId }}</dd></div>
      <div><dt>technicianProfileId</dt><dd>{{ detail.technicianProfileId }}</dd></div>
      <div><dt>validFrom</dt><dd>{{ detail.validFrom }}</dd></div>
      <div><dt>validTo</dt><dd>{{ detail.validTo ?? '—' }}</dd></div>
      <div><dt>version</dt><dd data-testid="membership-detail-version">{{ detail.version }}</dd></div>
      <div><dt>createdAt</dt><dd>{{ detail.createdAt }}</dd></div>
      <div><dt>terminatedAt</dt><dd>{{ detail.terminatedAt ?? '—' }}</dd></div>
      <div><dt>terminateReason</dt><dd>{{ detail.terminateReason ?? '—' }}</dd></div>
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
