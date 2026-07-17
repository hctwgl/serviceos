<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import {
  getMe,
  listMeCapabilities,
  listMeContexts,
  type MeCapabilities,
  type MeContext,
  type MeProfile,
} from '../api/me'

const props = defineProps<{ technicianContextId: string | null }>()
const profile = ref<MeProfile | null>(null)
const activeContext = ref<MeContext | null>(null)
const capabilities = ref<MeCapabilities | null>(null)
const error = ref<string | null>(null)
const loading = ref(false)

async function load() {
  if (!props.technicianContextId) {
    profile.value = null
    activeContext.value = null
    capabilities.value = null
    error.value = '请选择 TECHNICIAN 上下文'
    return
  }
  loading.value = true
  try {
    const me = await getMe()
    profile.value = me
    const contextsResult = await listMeContexts()
    activeContext.value =
      contextsResult.data.contexts.find(
        (context) => context.contextId === props.technicianContextId,
      ) ?? null
    const caps = await listMeCapabilities(
      props.technicianContextId,
      contextsResult.data.contextVersion,
    )
    capabilities.value = caps.data
    error.value = null
  } catch (err) {
    profile.value = null
    activeContext.value = null
    capabilities.value = null
    error.value = err instanceof Error ? err.message : '我的页加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})
watch(() => props.technicianContextId, () => {
  void load()
})
</script>

<template>
  <section data-testid="technician-portal-me" data-page-id="TECHNICIAN.ME">
    <header class="top">
      <div>
        <h2>我的</h2>
        <p class="hint">
          M219：消费 Accepted `/me`、`/me/contexts`、`/me/capabilities`；不提供资质/设备/消息。
        </p>
      </div>
      <button type="button" :disabled="loading" data-testid="technician-me-refresh" @click="load">
        刷新
      </button>
    </header>
    <p v-if="error" data-testid="technician-portal-error">{{ error }}</p>
    <p v-else-if="loading" data-testid="technician-me-loading">加载中…</p>
    <template v-else-if="profile">
      <h3>档案</h3>
      <dl data-testid="technician-me-profile">
        <div>
          <dt>principalId</dt>
          <dd data-testid="technician-me-principal-id">{{ profile.principalId }}</dd>
        </div>
        <div>
          <dt>tenantId</dt>
          <dd data-testid="technician-me-tenant-id">{{ profile.tenantId }}</dd>
        </div>
        <div>
          <dt>displayName</dt>
          <dd data-testid="technician-me-display-name">{{ profile.displayName }}</dd>
        </div>
        <div>
          <dt>contextVersion</dt>
          <dd>{{ profile.contextVersion }}</dd>
        </div>
        <div>
          <dt>asOf</dt>
          <dd data-testid="technician-me-as-of">{{ profile.asOf }}</dd>
        </div>
      </dl>

      <h3>Personas</h3>
      <ul data-testid="technician-me-personas">
        <li v-for="persona in profile.personas" :key="persona.id">
          {{ persona.personaType }} · {{ persona.status }}
          （{{ persona.validFrom }} → {{ persona.validTo ?? '—' }}）
        </li>
      </ul>
      <p v-if="profile.personas.length === 0">暂无 persona</p>

      <h3>当前 TECHNICIAN 上下文</h3>
      <dl v-if="activeContext" data-testid="technician-me-context">
        <div>
          <dt>contextId</dt>
          <dd data-testid="technician-me-context-id">{{ activeContext.contextId }}</dd>
        </div>
        <div>
          <dt>portal</dt>
          <dd>{{ activeContext.portal }}</dd>
        </div>
        <div>
          <dt>personaType</dt>
          <dd>{{ activeContext.personaType }}</dd>
        </div>
        <div>
          <dt>scopeType</dt>
          <dd>{{ activeContext.scopeType }}</dd>
        </div>
        <div>
          <dt>scopeRef</dt>
          <dd data-testid="technician-me-scope-ref">{{ activeContext.scopeRef }}</dd>
        </div>
        <div>
          <dt>version</dt>
          <dd>{{ activeContext.version }}</dd>
        </div>
        <div>
          <dt>networkIds</dt>
          <dd>{{ activeContext.scopeSummary.networkIds.join(', ') || '—' }}</dd>
        </div>
        <div>
          <dt>projectIds</dt>
          <dd>{{ activeContext.scopeSummary.projectIds.join(', ') || '—' }}</dd>
        </div>
        <div>
          <dt>organizationIds</dt>
          <dd>{{ activeContext.scopeSummary.organizationIds.join(', ') || '—' }}</dd>
        </div>
      </dl>
      <p v-else data-testid="technician-me-context-missing">未匹配到当前 TECHNICIAN 上下文</p>

      <h3>Capabilities</h3>
      <dl v-if="capabilities" data-testid="technician-me-capabilities">
        <div>
          <dt>contextVersion</dt>
          <dd>{{ capabilities.contextVersion }}</dd>
        </div>
        <div>
          <dt>asOf</dt>
          <dd>{{ capabilities.asOf }}</dd>
        </div>
      </dl>
      <ul v-if="capabilities" data-testid="technician-me-capability-codes">
        <li
          v-for="code in capabilities.capabilityCodes"
          :key="code"
          :data-testid="`technician-me-capability-${code}`"
        >
          {{ code }}
        </li>
      </ul>
      <p
        v-if="capabilities && capabilities.capabilityCodes.length === 0"
        data-testid="technician-me-capabilities-empty"
      >
        当前上下文无 capabilityCodes
      </p>
    </template>
  </section>
</template>

<style scoped>
.top {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: flex-start;
}
.hint {
  color: #5b6573;
  font-size: 0.9rem;
}
dl {
  display: grid;
  gap: 0.35rem;
  margin: 0.75rem 0 1.25rem;
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
ul {
  padding-left: 1.1rem;
}
</style>
