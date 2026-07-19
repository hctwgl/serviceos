<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  disableSecurityPrincipal,
  enableSecurityPrincipal,
  getSecurityPrincipal,
  listPrincipalIdentityLinks,
  updateSecurityPrincipalProfile,
  type IdentityLink,
  type SecurityPrincipalDetail,
} from '../api/securityPrincipals'
import { listRoleGrants, type RoleGrant } from '../api/authorizationGovernance'
import { listOpenReassignmentWorkItems, type ReassignmentWorkItem } from '../api/organizations'
import { isConflictError, safeAccessDeniedMessage } from '../api/client'
import ImpactPanel from '../components/ImpactPanel.vue'
import VersionedCommandForm from '../components/VersionedCommandForm.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { statusLabel } from '../product/statusLabels'

const route = useRoute()
const principalId = computed(() => String(route.params.id ?? ''))

const loading = ref(false)
const busy = ref(false)
const denied = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)
const detail = ref<SecurityPrincipalDetail | null>(null)
const etag = ref<string | null>(null)
const identities = ref<IdentityLink[] | null>(null)
const grants = ref<RoleGrant[]>([])
const reassignments = ref<ReassignmentWorkItem[]>([])
const displayName = ref('')
const employeeNumber = ref('')
const lifecycleReason = ref('ADMIN_USER_CENTER')
const staleVersion = ref<number | null>(null)

const impactItems = computed(() => {
  if (!detail.value) return []
  const p = detail.value.principal
  return [
    `主体状态将变为 ${statusLabel(p.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE')}`,
    '停用后已签发 JWT 在下次请求立即失败关闭',
    `当前有效 Persona ${detail.value.personas.filter((x) => x.status === 'ACTIVE').length} 条`,
    `相关授权记录 ${grants.value.length} 条（启停不替代撤权）`,
  ]
})

async function load() {
  loading.value = true
  denied.value = false
  error.value = null
  try {
    const result = await getSecurityPrincipal(principalId.value)
    detail.value = result.data
    etag.value = result.etag
    displayName.value = result.data.principal.displayName
    employeeNumber.value = result.data.principal.employeeNumber ?? ''
    try {
      identities.value = await listPrincipalIdentityLinks(principalId.value)
    } catch {
      identities.value = null
    }
    try {
      const grantPage = await listRoleGrants({ principalId: principalId.value })
      grants.value = grantPage.items
    } catch {
      grants.value = []
    }
    try {
      const work = await listOpenReassignmentWorkItems()
      reassignments.value = work.items.filter((item) => item.principalId === principalId.value)
    } catch {
      reassignments.value = []
    }
  } catch (err) {
    detail.value = null
    denied.value = true
    error.value = safeAccessDeniedMessage(err)
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  if (!detail.value) return
  busy.value = true
  message.value = null
  error.value = null
  try {
    const version = staleVersion.value ?? detail.value.principal.version
    await updateSecurityPrincipalProfile(principalId.value, version, {
      displayName: displayName.value.trim(),
      employeeNumber: employeeNumber.value.trim() || null,
    })
    staleVersion.value = null
    message.value = '档案已更新，已重读权威状态'
    await load()
  } catch (err) {
    if (isConflictError(err)) {
      staleVersion.value = null
      await load()
      // load() 会清空 error；冲突提示必须在重读后写回，供运营恢复。
      error.value = '版本冲突（409），请刷新后重试'
    } else {
      error.value = safeAccessDeniedMessage(err)
    }
  } finally {
    busy.value = false
  }
}

async function toggleLifecycle() {
  if (!detail.value) return
  busy.value = true
  message.value = null
  error.value = null
  try {
    const version = detail.value.principal.version
    const body = { reason: lifecycleReason.value.trim() || 'ADMIN_USER_CENTER' }
    if (detail.value.principal.status === 'ACTIVE') {
      await disableSecurityPrincipal(principalId.value, version, body)
    } else {
      await enableSecurityPrincipal(principalId.value, version, body)
    }
    message.value = '生命周期命令已提交，已重读权威状态'
    await load()
  } catch (err) {
    if (isConflictError(err)) {
      error.value = '版本冲突（409），请刷新后重试'
      await load()
    } else {
      error.value = safeAccessDeniedMessage(err)
    }
  } finally {
    busy.value = false
  }
}

async function useStaleVersionForTest() {
  if (!detail.value) return
  busy.value = true
  error.value = null
  try {
    // 确保至少有一次版本推进，才能构造严格小于当前的过期 If-Match。
    if (detail.value.principal.version <= 1) {
      await updateSecurityPrincipalProfile(principalId.value, detail.value.principal.version, {
        displayName: detail.value.principal.displayName,
        employeeNumber: detail.value.principal.employeeNumber,
      })
      await load()
    }
    if (!detail.value) return
    staleVersion.value = detail.value.principal.version - 1
    message.value = `已准备过期 If-Match=v${staleVersion.value}`
  } catch (err) {
    error.value = safeAccessDeniedMessage(err)
  } finally {
    busy.value = false
  }
}

watch(principalId, () => {
  if (principalId.value) void load()
})
onMounted(() => {
  if (principalId.value) void load()
})
</script>

<template>
  <section class="page" data-testid="user-detail-page">
    <header class="top">
      <div>
        <h2>主体详情</h2>
        <p class="meta">分区显示身份、Persona、任职/网点关系与授权来源</p>
      </div>
      <button type="button" :disabled="loading" @click="load">刷新</button>
    </header>

    <p v-if="denied" class="error" data-testid="access-denied">{{ error }}</p>
    <p v-else-if="loading && !detail">加载中…</p>
    <p v-else-if="!detail && error" class="error">{{ error }}</p>

    <template v-else-if="detail">
      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="message" class="ok" data-testid="command-message">{{ message }}</p>

      <article class="card" data-testid="section-identity">
        <h3>身份</h3>
        <dl>
          <div><dt>displayName</dt><dd data-testid="principal-display-name">{{ detail.principal.displayName }}</dd></div>
          <div><dt>employeeNumber</dt><dd>{{ detail.principal.employeeNumber || '—' }}</dd></div>
          <div><dt>状态</dt><dd data-testid="principal-status"><StatusBadge :status="detail.principal.status" /></dd></div>
          <div><dt>类型</dt><dd>{{ statusLabel(detail.principal.type) }}</dd></div>
          <div><dt>version</dt><dd data-testid="principal-version">{{ detail.principal.version }}</dd></div>
        </dl>
      </article>

      <article class="card" data-testid="section-personas">
        <h3>Persona</h3>
        <ul v-if="detail.personas.length">
          <li v-for="persona in detail.personas" :key="persona.id">
            {{ statusLabel(persona.personaType) }} · <StatusBadge :status="persona.status" /> · {{ persona.validFrom }}
            <span v-if="persona.validTo"> → {{ persona.validTo }}</span>
          </li>
        </ul>
        <p v-else class="muted">无 Persona</p>
      </article>

      <article class="card" data-testid="section-identity-links">
        <h3>身份绑定</h3>
        <p v-if="identities === null" class="muted">无 identity.readSensitive，不展示敏感绑定</p>
        <ul v-else-if="identities.length">
          <li v-for="link in identities" :key="link.id">
            issuer={{ link.issuer }} · linkedAt={{ link.linkedAt }}
          </li>
        </ul>
        <p v-else class="muted">无绑定</p>
      </article>

      <article class="card" data-testid="section-grants">
        <h3>授权来源</h3>
        <ul v-if="grants.length">
          <li v-for="grant in grants" :key="grant.grantId">
            {{ grant.roleCode }} · {{ grant.grantStatus }}/{{ grant.grantEffect }} ·
            {{ grant.scopeType }}={{ grant.scopeRef }}
          </li>
        </ul>
        <p v-else class="muted">无授权记录或无权读取</p>
      </article>

      <article class="card" data-testid="section-reassignment">
        <h3>待重分配</h3>
        <ul v-if="reassignments.length">
          <li v-for="item in reassignments" :key="item.id">
            {{ item.workItemStatus }} · {{ item.reason }} · {{ item.createdAt }}
          </li>
        </ul>
        <p v-else class="muted">无 OPEN 待重分配项</p>
      </article>

      <VersionedCommandForm
        title="更新 PersonProfile"
        :version="detail.principal.version"
        :busy="busy"
        submit-label="保存档案"
        hint="成功后重读权威 API；并发冲突返回 409。"
        @submit="saveProfile"
      >
        <label>displayName<input v-model="displayName" aria-label="profile displayName" /></label>
        <label>employeeNumber<input v-model="employeeNumber" aria-label="profile employeeNumber" /></label>
        <button
          type="button"
          data-testid="prepare-stale-if-match"
          :disabled="busy"
          @click="() => void useStaleVersionForTest()"
        >
          准备过期 If-Match（E2E）
        </button>
      </VersionedCommandForm>

      <VersionedCommandForm
        :title="detail.principal.status === 'ACTIVE' ? '停用主体' : '启用主体'"
        :version="detail.principal.version"
        :busy="busy"
        :submit-label="detail.principal.status === 'ACTIVE' ? '确认停用' : '确认启用'"
        @submit="toggleLifecycle"
      >
        <ImpactPanel :items="impactItems" :obligations="['记录审计原因', '确认未完成工作已交接']" />
        <label>reason<input v-model="lifecycleReason" aria-label="lifecycle reason" /></label>
      </VersionedCommandForm>
    </template>
  </section>
</template>

<style scoped>
.page {
  display: grid;
  gap: 1rem;
}
.top {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
}
.meta {
  margin: 0.25rem 0 0;
  color: #627d98;
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
  gap: 0.35rem;
}
dl div {
  display: grid;
  grid-template-columns: 9rem 1fr;
  gap: 0.5rem;
}
dt {
  color: #627d98;
}
dd {
  margin: 0;
}
ul {
  margin: 0;
  padding-left: 1.1rem;
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
}
button {
  border: 1px solid #bcccdc;
  background: #243b53;
  color: #fff;
  border-radius: 6px;
  padding: 0.45rem 0.9rem;
}
.error {
  color: #b42318;
}
.ok {
  color: #054e31;
}
.muted {
  color: #627d98;
}
</style>
