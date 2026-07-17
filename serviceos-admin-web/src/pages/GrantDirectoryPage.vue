<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  createDelegation,
  decideRoleGrant,
  explainAuthorization,
  listDelegations,
  listRoleGrants,
  listRoles,
  requestRoleGrant,
  revokeDelegation,
  revokeRoleGrant,
  type AuthorizationExplainResult,
  type Delegation,
  type Role,
  type RoleGrant,
} from '../api/authorizationGovernance'
import { isConflictError, safeAccessDeniedMessage } from '../api/client'
import ImpactPanel from '../components/ImpactPanel.vue'
import PrincipalPicker from '../components/PrincipalPicker.vue'
import VersionedCommandForm from '../components/VersionedCommandForm.vue'

const loading = ref(false)
const busy = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)
const grants = ref<RoleGrant[]>([])
const delegations = ref<Delegation[]>([])
const roles = ref<Role[]>([])

const principalId = ref<string | null>(null)
const roleId = ref('')
const scopeType = ref<'TENANT' | 'PROJECT' | 'REGION' | 'NETWORK'>('TENANT')
const scopeRef = ref('tenant-local')
const requestReason = ref('ADMIN_USER_CENTER_GRANT')
const selectedGrantId = ref('')
const revokeReason = ref('ADMIN_REVOKE')

const delegatePrincipalId = ref<string | null>(null)
const delegationCaps = ref('project.read')
const selectedDelegationId = ref('')

const explainPrincipalId = ref<string | null>(null)
const explainCapability = ref('project.read')
const explainResult = ref<AuthorizationExplainResult | null>(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    grants.value = (await listRoleGrants()).items
    delegations.value = (await listDelegations()).items
    roles.value = (await listRoles()).items
    if (!roleId.value && roles.value[0]) {
      roleId.value = roles.value[0].roleId
    }
  } catch (err) {
    grants.value = []
    delegations.value = []
    error.value = safeAccessDeniedMessage(err)
  } finally {
    loading.value = false
  }
}

async function requestGrant() {
  if (!principalId.value || !roleId.value) {
    error.value = '请选择人员与角色'
    return
  }
  busy.value = true
  message.value = null
  error.value = null
  try {
    await requestRoleGrant({
      principalId: principalId.value,
      roleId: roleId.value,
      scopeType: scopeType.value,
      scopeRef: scopeRef.value.trim(),
      validFrom: new Date().toISOString(),
      requestReason: requestReason.value.trim(),
    })
    message.value = '授权申请已提交，已重读权威状态'
    await load()
  } catch (err) {
    error.value = safeAccessDeniedMessage(err)
  } finally {
    busy.value = false
  }
}

function selectedGrant(): RoleGrant | undefined {
  return grants.value.find((item) => item.grantId === selectedGrantId.value)
}

async function approveSelected() {
  const grant = selectedGrant()
  if (!grant) {
    error.value = '请选择授权'
    return
  }
  busy.value = true
  message.value = null
  error.value = null
  try {
    await decideRoleGrant(grant.grantId, grant.version, { decision: 'APPROVE' })
    message.value = '已批准，已重读权威状态'
    await load()
  } catch (err) {
    error.value = isConflictError(err)
      ? '版本冲突（409），请刷新后重试'
      : safeAccessDeniedMessage(err)
    if (isConflictError(err)) await load()
  } finally {
    busy.value = false
  }
}

async function revokeSelected() {
  const grant = selectedGrant()
  if (!grant) {
    error.value = '请选择授权'
    return
  }
  busy.value = true
  message.value = null
  error.value = null
  try {
    await revokeRoleGrant(grant.grantId, grant.version, {
      reason: revokeReason.value.trim(),
    })
    message.value = '授权已撤销，已重读权威状态'
    await load()
  } catch (err) {
    error.value = isConflictError(err)
      ? '版本冲突（409），请刷新后重试'
      : safeAccessDeniedMessage(err)
    if (isConflictError(err)) await load()
  } finally {
    busy.value = false
  }
}

async function createDeleg() {
  if (!delegatePrincipalId.value) {
    error.value = '请选择被委托人'
    return
  }
  busy.value = true
  message.value = null
  error.value = null
  try {
    await createDelegation({
      delegatePrincipalId: delegatePrincipalId.value,
      capabilityCodes: delegationCaps.value
        .split(/[,\s]+/)
        .map((v) => v.trim())
        .filter(Boolean),
      scopeType: scopeType.value,
      scopeRef: scopeRef.value.trim(),
      validFrom: new Date().toISOString(),
      reason: 'ADMIN_DELEGATION',
    })
    message.value = '委托已创建，已重读权威状态'
    await load()
  } catch (err) {
    error.value = safeAccessDeniedMessage(err)
  } finally {
    busy.value = false
  }
}

async function revokeDeleg() {
  const item = delegations.value.find((d) => d.delegationId === selectedDelegationId.value)
  if (!item) {
    error.value = '请选择委托'
    return
  }
  busy.value = true
  message.value = null
  error.value = null
  try {
    await revokeDelegation(item.delegationId, item.version, { reason: 'ADMIN_DELEGATION_REVOKE' })
    message.value = '委托已撤销，已重读权威状态'
    await load()
  } catch (err) {
    error.value = isConflictError(err)
      ? '版本冲突（409），请刷新后重试'
      : safeAccessDeniedMessage(err)
    if (isConflictError(err)) await load()
  } finally {
    busy.value = false
  }
}

async function runExplain() {
  if (!explainPrincipalId.value) {
    error.value = '请选择解释主体'
    return
  }
  busy.value = true
  error.value = null
  try {
    explainResult.value = (
      await explainAuthorization({
        subjectPrincipalId: explainPrincipalId.value,
        capability: explainCapability.value.trim(),
        resourceType: 'project',
        resourceId: 'tenant-local',
      })
    ).data
  } catch (err) {
    explainResult.value = null
    error.value = safeAccessDeniedMessage(err)
  } finally {
    busy.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <section class="page" data-testid="grant-directory-page">
    <header class="top">
      <div>
        <h2>用户授权与委托</h2>
        <p class="hint">申请、批准、撤销与 Delegation；写动作展示影响并重读权威结果。</p>
      </div>
      <button type="button" :disabled="loading" @click="load">刷新</button>
    </header>

    <p v-if="error" class="error" data-testid="access-denied">{{ error }}</p>
    <p v-if="message" class="ok" data-testid="command-message">{{ message }}</p>

    <article class="card form">
      <h3>申请 RoleGrant</h3>
      <PrincipalPicker v-model="principalId" label="授权对象" />
      <label>
        角色
        <select v-model="roleId" aria-label="grant role">
          <option v-for="role in roles" :key="role.roleId" :value="role.roleId">
            {{ role.roleName }}（{{ role.roleCode }}）
          </option>
        </select>
      </label>
      <label>
        scopeType
        <select v-model="scopeType" aria-label="grant scope type">
          <option value="TENANT">TENANT</option>
          <option value="PROJECT">PROJECT</option>
          <option value="REGION">REGION</option>
          <option value="NETWORK">NETWORK</option>
        </select>
      </label>
      <label>scopeRef<input v-model="scopeRef" aria-label="grant scope ref" /></label>
      <label>requestReason<input v-model="requestReason" aria-label="grant request reason" /></label>
      <ImpactPanel
        :items="[
          '申请后进入 PENDING_APPROVAL（或按策略即时生效）',
          'SoD / 可授予范围由后端失败关闭校验',
          `范围 ${scopeType}=${scopeRef}`,
        ]"
        :obligations="['高风险能力可能要求审批']"
      />
      <button type="button" :disabled="busy" data-testid="request-grant" @click="requestGrant">
        提交申请
      </button>
    </article>

    <article class="card">
      <h3>RoleGrant 列表</h3>
      <p v-if="loading">加载中…</p>
      <ul v-else data-testid="grant-list">
        <li v-for="item in grants" :key="item.grantId">
          <label>
            <input v-model="selectedGrantId" type="radio" name="grant" :value="item.grantId" />
            {{ item.roleCode }} · {{ item.grantStatus }}/{{ item.grantEffect }} ·
            {{ item.scopeType }}={{ item.scopeRef }} · v{{ item.version }}
          </label>
        </li>
      </ul>
      <div class="actions">
        <button type="button" :disabled="busy" @click="approveSelected">批准选中</button>
        <VersionedCommandForm
          title="撤销选中授权"
          :version="selectedGrant()?.version ?? null"
          :busy="busy"
          submit-label="撤销"
          @submit="revokeSelected"
        >
          <ImpactPanel
            :items="[
              '撤销后运行时立即失权',
              '推进租户 grant generation',
              '不可覆盖并发新事实（If-Match）',
            ]"
          />
          <label>reason<input v-model="revokeReason" aria-label="revoke reason" /></label>
        </VersionedCommandForm>
      </div>
    </article>

    <article class="card form">
      <h3>创建 Delegation</h3>
      <PrincipalPicker v-model="delegatePrincipalId" label="被委托人" />
      <label>capabilityCodes<input v-model="delegationCaps" aria-label="delegation capabilities" /></label>
      <button type="button" :disabled="busy" @click="createDeleg">创建委托</button>
      <ul>
        <li v-for="item in delegations" :key="item.delegationId">
          <label>
            <input
              v-model="selectedDelegationId"
              type="radio"
              name="delegation"
              :value="item.delegationId"
            />
            {{ item.delegationStatus }} · {{ item.capabilityCodes.join(',') }} · v{{ item.version }}
          </label>
        </li>
      </ul>
      <button type="button" :disabled="busy" @click="revokeDeleg">撤销选中委托</button>
    </article>

    <article class="card form">
      <h3>授权解释</h3>
      <PrincipalPicker v-model="explainPrincipalId" label="解释主体" />
      <label>capability<input v-model="explainCapability" aria-label="explain capability" /></label>
      <button type="button" :disabled="busy" @click="runExplain">解释</button>
      <pre v-if="explainResult" data-testid="explain-result">{{ explainResult }}</pre>
    </article>
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
.hint {
  margin: 0.25rem 0 0;
  color: #627d98;
}
.card {
  background: #fff;
  border-radius: 12px;
  padding: 1rem 1.15rem;
  box-shadow: 0 1px 3px rgb(16 42 67 / 8%);
}
.form,
.actions {
  display: grid;
  gap: 0.55rem;
}
label {
  display: grid;
  gap: 0.25rem;
  font-size: 0.85rem;
  color: #486581;
}
input,
select {
  border: 1px solid #bcccdc;
  border-radius: 6px;
  padding: 0.4rem 0.65rem;
}
button {
  justify-self: start;
  border: 1px solid #bcccdc;
  background: #243b53;
  color: #fff;
  border-radius: 6px;
  padding: 0.45rem 0.9rem;
}
ul {
  margin: 0;
  padding-left: 1.1rem;
}
pre {
  margin: 0;
  white-space: pre-wrap;
  font-size: 0.8rem;
  background: #f0f4f8;
  padding: 0.75rem;
  border-radius: 8px;
}
.error {
  color: #b42318;
}
.ok {
  color: #054e31;
}
</style>
