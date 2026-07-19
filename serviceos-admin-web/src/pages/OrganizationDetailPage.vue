<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  createOrganizationMembership,
  createOrganizationUnit,
  getOrganization,
  listOrganizationMemberships,
  listOpenReassignmentWorkItems,
  terminateOrganizationMembership,
  transferOrganizationMembership,
  type OrgMembership,
  type OrganizationDetail,
  type ReassignmentWorkItem,
} from '../api/organizations'
import { isConflictError, safeAccessDeniedMessage } from '../api/client'
import ExternalAuthoritativeBadge from '../components/ExternalAuthoritativeBadge.vue'
import ImpactPanel from '../components/ImpactPanel.vue'
import PrincipalPicker from '../components/PrincipalPicker.vue'
import VersionedCommandForm from '../components/VersionedCommandForm.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { statusLabel } from '../product/statusLabels'

const route = useRoute()
const organizationId = computed(() => String(route.params.id ?? ''))

const loading = ref(false)
const busy = ref(false)
const denied = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)
const detail = ref<OrganizationDetail | null>(null)
const memberships = ref<OrgMembership[]>([])
const reassignments = ref<ReassignmentWorkItem[]>([])

const unitCode = ref('')
const unitName = ref('')
const parentUnitId = ref('')
const membershipUnitId = ref('')
const membershipPrincipalId = ref<string | null>(null)
const membershipType = ref<'PRIMARY' | 'SECONDARY' | 'MANAGER'>('PRIMARY')
const terminateReason = ref('ADMIN_TRANSFER_OR_EXIT')
const disablePrincipal = ref(false)
const selectedMembershipId = ref('')
const transferTargetUnitId = ref('')

const external = computed(
  () => detail.value?.organization.authorityMode === 'EXTERNAL_AUTHORITATIVE',
)

const terminateImpact = computed(() => [
  '终止任职将写入历史，不可静默复活',
  disablePrincipal.value ? '将同事务停用 Principal 并撤销有效 RoleGrant' : '不联动停用 Principal',
  '若联动停用，将生成待重分配清单',
])

async function load() {
  loading.value = true
  denied.value = false
  error.value = null
  try {
    const result = await getOrganization(organizationId.value)
    detail.value = result.data
    memberships.value = (
      await listOrganizationMemberships(organizationId.value)
    ).items
    try {
      const work = await listOpenReassignmentWorkItems()
      reassignments.value = work.items.filter(
        (item) => item.organizationId === organizationId.value,
      )
    } catch {
      reassignments.value = []
    }
    if (!membershipUnitId.value && result.data.units[0]) {
      membershipUnitId.value = result.data.units[0].id
    }
  } catch (err) {
    detail.value = null
    denied.value = true
    error.value = safeAccessDeniedMessage(err)
  } finally {
    loading.value = false
  }
}

async function createUnit() {
  if (!detail.value) return
  busy.value = true
  message.value = null
  error.value = null
  try {
    await createOrganizationUnit(
      organizationId.value,
      detail.value.organization.version,
      {
        unitCode: unitCode.value.trim(),
        unitName: unitName.value.trim(),
        parentUnitId: parentUnitId.value.trim() || null,
      },
    )
    message.value = '单元已创建，已重读权威状态'
    unitCode.value = ''
    unitName.value = ''
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

async function createMembership() {
  if (!membershipPrincipalId.value || !membershipUnitId.value) {
    error.value = '请通过目录选择人员并指定单元'
    return
  }
  busy.value = true
  message.value = null
  error.value = null
  try {
    await createOrganizationMembership(organizationId.value, {
      unitId: membershipUnitId.value,
      principalId: membershipPrincipalId.value,
      membershipType: membershipType.value,
      validFrom: new Date().toISOString(),
    })
    message.value = '任职已创建，已重读权威状态'
    await load()
  } catch (err) {
    error.value = safeAccessDeniedMessage(err)
  } finally {
    busy.value = false
  }
}

async function transferSelected() {
  const membership = memberships.value.find((item) => item.id === selectedMembershipId.value)
  if (!membership || !transferTargetUnitId.value) {
    error.value = '请选择任职与目标单元'
    return
  }
  busy.value = true
  message.value = null
  error.value = null
  try {
    await transferOrganizationMembership(membership.id, membership.version, {
      targetUnitId: transferTargetUnitId.value,
    })
    message.value = '调动完成，已重读权威状态'
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

async function terminateSelected() {
  const membership = memberships.value.find((item) => item.id === selectedMembershipId.value)
  if (!membership) {
    error.value = '请选择任职'
    return
  }
  busy.value = true
  message.value = null
  error.value = null
  try {
    await terminateOrganizationMembership(membership.id, membership.version, {
      reason: terminateReason.value.trim(),
      disablePrincipal: disablePrincipal.value,
    })
    message.value = '任职已终止，已重读权威状态与待重分配'
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

watch(organizationId, () => {
  if (organizationId.value) void load()
})
onMounted(() => {
  if (organizationId.value) void load()
})
</script>

<template>
  <section class="page" data-testid="organization-detail-page">
    <header class="top">
      <div>
        <h2>组织详情</h2>
        <p class="meta">单元、任职、调动/终止与待重分配</p>
      </div>
      <button type="button" :disabled="loading" @click="load">刷新</button>
    </header>

    <p v-if="denied" class="error" data-testid="access-denied">{{ error }}</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <p v-else-if="loading">加载中…</p>

    <template v-else-if="detail">
      <p v-if="message" class="ok" data-testid="command-message">{{ message }}</p>
      <article class="card">
        <h3>{{ detail.organization.name }}</h3>
        <ExternalAuthoritativeBadge
          :authority-mode="detail.organization.authorityMode"
          :source-system="detail.organization.sourceSystem"
          :source-key="detail.organization.sourceKey"
          :last-synced-at="detail.organization.updatedAt"
        />
        <dl>
          <div><dt>code</dt><dd>{{ detail.organization.code }}</dd></div>
          <div><dt>状态</dt><dd><StatusBadge :status="detail.organization.status" /></dd></div>
          <div><dt>version</dt><dd>{{ detail.organization.version }}</dd></div>
        </dl>
      </article>

      <article class="card">
        <h3>组织单元</h3>
        <ul>
          <li v-for="unit in detail.units" :key="unit.id">
            {{ unit.unitName }}（{{ unit.unitCode }}）
            <span v-if="external">
              · sourceVersion={{ unit.sourceVersion ?? '—' }} · updatedAt={{ unit.updatedAt }}
            </span>
          </li>
        </ul>
      </article>

      <VersionedCommandForm
        v-if="!external"
        title="创建单元"
        :version="detail.organization.version"
        :busy="busy"
        @submit="createUnit"
      >
        <label>unitCode<input v-model="unitCode" aria-label="unit code" /></label>
        <label>unitName<input v-model="unitName" aria-label="unit name" /></label>
        <label>parentUnitId<input v-model="parentUnitId" aria-label="parent unit id" placeholder="可选" /></label>
      </VersionedCommandForm>
      <p v-else class="muted">EXTERNAL_AUTHORITATIVE：结构字段只读，请走目录同步批次。</p>

      <article class="card form">
        <h3>创建任职</h3>
        <PrincipalPicker v-model="membershipPrincipalId" label="任职人员" />
        <label>
          单元
          <select v-model="membershipUnitId" aria-label="membership unit">
            <option v-for="unit in detail.units" :key="unit.id" :value="unit.id">
              {{ unit.unitName }}
            </option>
          </select>
        </label>
        <label>
          membershipType
          <select v-model="membershipType" aria-label="membership type">
            <option value="PRIMARY">PRIMARY</option>
            <option value="SECONDARY">SECONDARY</option>
            <option value="MANAGER">MANAGER</option>
          </select>
        </label>
        <button type="button" :disabled="busy" @click="createMembership">创建任职</button>
      </article>

      <article class="card">
        <h3>任职列表</h3>
        <ul data-testid="membership-list">
          <li v-for="item in memberships" :key="item.id">
            <label>
              <input
                v-model="selectedMembershipId"
                type="radio"
                name="membership"
                :value="item.id"
              />
              {{ statusLabel(item.membershipType) }} · <StatusBadge :status="item.status" /> · unit={{ item.orgUnitId }} · v{{ item.version }}
            </label>
          </li>
        </ul>
      </article>

      <article class="card form">
        <h3>调动任职</h3>
        <label>
          目标单元
          <select v-model="transferTargetUnitId" aria-label="transfer target unit">
            <option value="">选择</option>
            <option v-for="unit in detail.units" :key="unit.id" :value="unit.id">
              {{ unit.unitName }}
            </option>
          </select>
        </label>
        <button type="button" :disabled="busy" @click="transferSelected">调动</button>
      </article>

      <article class="card form">
        <h3>终止任职</h3>
        <ImpactPanel :items="terminateImpact" :obligations="['确认审批/交接完成']" />
        <label>reason<input v-model="terminateReason" aria-label="terminate reason" /></label>
        <label class="check">
          <input v-model="disablePrincipal" type="checkbox" aria-label="disable principal on terminate" />
          联动停用 Principal
        </label>
        <button type="button" :disabled="busy" data-testid="terminate-membership" @click="terminateSelected">
          终止
        </button>
      </article>

      <article class="card">
        <h3>待重分配</h3>
        <ul v-if="reassignments.length" data-testid="reassignment-list">
          <li v-for="item in reassignments" :key="item.id">
            {{ item.workItemStatus }} · {{ item.reason }} · principal={{ item.principalId }}
          </li>
        </ul>
        <p v-else class="muted">无 OPEN 待办</p>
      </article>
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
}
.meta,
.muted {
  color: #627d98;
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
label {
  display: grid;
  gap: 0.25rem;
  font-size: 0.85rem;
  color: #486581;
}
.check {
  grid-template-columns: auto 1fr;
  align-items: center;
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
dl {
  margin: 0.75rem 0 0;
  display: grid;
  gap: 0.3rem;
}
dl div {
  display: grid;
  grid-template-columns: 7rem 1fr;
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
.error {
  color: #b42318;
}
.ok {
  color: #054e31;
}
</style>
