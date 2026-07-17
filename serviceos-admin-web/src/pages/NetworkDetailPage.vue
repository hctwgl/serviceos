<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  deactivateServiceNetwork,
  getServiceNetwork,
  getServiceNetworkDeactivationImpact,
  inviteNetworkMember,
  listNetworkMemberships,
  terminateNetworkMembership,
  type NetworkDeactivationImpact,
  type NetworkMembership,
  type ServiceNetwork,
} from '../api/networks'
import { isConflictError, safeAccessDeniedMessage } from '../api/client'
import ImpactPanel from '../components/ImpactPanel.vue'
import PrincipalPicker from '../components/PrincipalPicker.vue'
import VersionedCommandForm from '../components/VersionedCommandForm.vue'

const route = useRoute()
const networkId = computed(() => String(route.params.id ?? ''))

const loading = ref(false)
const busy = ref(false)
const denied = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)
const network = ref<ServiceNetwork | null>(null)
const memberships = ref<NetworkMembership[]>([])
const impact = ref<NetworkDeactivationImpact | null>(null)
const principalId = ref<string | null>(null)
const role = ref<'MANAGER' | 'STAFF'>('STAFF')
const deactivateReason = ref('ADMIN_NETWORK_CLEARANCE')
const selectedMembershipId = ref('')

const impactItems = computed(() => {
  if (!impact.value) return ['加载停用影响中…']
  return [
    `未完成任务 ${impact.value.openTaskCount}`,
    `未完成预约 ${impact.value.openAppointmentCount}`,
    `未完成上门 ${impact.value.openVisitCount}`,
    `有效派工 ${impact.value.activeAssignmentCount}`,
    `离线包 ${impact.value.offlinePackageCount}`,
  ]
})

async function load() {
  loading.value = true
  denied.value = false
  error.value = null
  try {
    const result = await getServiceNetwork(networkId.value)
    network.value = result.data
    memberships.value = (await listNetworkMemberships(networkId.value)).items
    impact.value = await getServiceNetworkDeactivationImpact(networkId.value)
  } catch (err) {
    network.value = null
    denied.value = true
    error.value = safeAccessDeniedMessage(err)
  } finally {
    loading.value = false
  }
}

async function invite() {
  if (!principalId.value) {
    error.value = '请通过目录选择人员'
    return
  }
  busy.value = true
  message.value = null
  error.value = null
  try {
    await inviteNetworkMember(networkId.value, {
      principalId: principalId.value,
      role: role.value,
      validFrom: new Date().toISOString(),
    })
    message.value = '网点人员已邀请，已重读权威状态'
    await load()
  } catch (err) {
    error.value = safeAccessDeniedMessage(err)
  } finally {
    busy.value = false
  }
}

async function terminateSelected() {
  const membership = memberships.value.find((item) => item.id === selectedMembershipId.value)
  if (!membership) {
    error.value = '请选择成员'
    return
  }
  busy.value = true
  message.value = null
  error.value = null
  try {
    await terminateNetworkMembership(membership.id, membership.version, {
      reason: 'ADMIN_NETWORK_EXIT',
    })
    message.value = '成员已终止，已重读权威状态'
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

async function deactivate() {
  if (!network.value) return
  busy.value = true
  message.value = null
  error.value = null
  try {
    await deactivateServiceNetwork(networkId.value, network.value.version, {
      reason: deactivateReason.value.trim(),
    })
    message.value = '网点已停用，已重读权威状态'
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

watch(networkId, () => {
  if (networkId.value) void load()
})
onMounted(() => {
  if (networkId.value) void load()
})
</script>

<template>
  <section class="page" data-testid="network-detail-page">
    <header class="top">
      <div>
        <h2>网点详情</h2>
        <p class="meta">人员、停用影响与清退</p>
      </div>
      <button type="button" :disabled="loading" @click="load">刷新</button>
    </header>

    <p v-if="denied" class="error" data-testid="access-denied">{{ error }}</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <p v-else-if="loading">加载中…</p>

    <template v-else-if="network">
      <p v-if="message" class="ok">{{ message }}</p>
      <article class="card">
        <h3>{{ network.networkName }}</h3>
        <dl>
          <div><dt>code</dt><dd>{{ network.networkCode }}</dd></div>
          <div><dt>status</dt><dd data-testid="network-status">{{ network.status }}</dd></div>
          <div><dt>version</dt><dd>{{ network.version }}</dd></div>
        </dl>
      </article>

      <article class="card form">
        <h3>邀请网点人员</h3>
        <PrincipalPicker v-model="principalId" />
        <label>
          role
          <select v-model="role" aria-label="network member role">
            <option value="STAFF">STAFF</option>
            <option value="MANAGER">MANAGER</option>
          </select>
        </label>
        <button type="button" :disabled="busy" @click="invite">邀请</button>
      </article>

      <article class="card">
        <h3>成员</h3>
        <ul>
          <li v-for="item in memberships" :key="item.id">
            <label>
              <input
                v-model="selectedMembershipId"
                type="radio"
                name="network-membership"
                :value="item.id"
              />
              {{ item.role }} · {{ item.status }} · v{{ item.version }}
            </label>
          </li>
        </ul>
        <button type="button" :disabled="busy" @click="terminateSelected">终止选中成员</button>
      </article>

      <VersionedCommandForm
        title="停用网点"
        :version="network.version"
        :busy="busy"
        submit-label="确认停用"
        @submit="deactivate"
      >
        <ImpactPanel :items="impactItems" :obligations="['生成 clearance 待办', '阻断新派工']" />
        <label>reason<input v-model="deactivateReason" aria-label="deactivate reason" /></label>
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
}
.meta {
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
  margin: 0;
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
  margin: 0 0 0.75rem;
  padding-left: 1.1rem;
}
.error {
  color: #b42318;
}
.ok {
  color: #054e31;
}
</style>
