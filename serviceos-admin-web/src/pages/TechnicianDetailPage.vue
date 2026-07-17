<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  createNetworkTechnicianMembership,
  disableTechnicianProfile,
  enableTechnicianProfile,
  getTechnicianProfile,
  listNetworkTechnicianMemberships,
  listTechnicianQualifications,
  type NetworkTechnicianMembership,
  type TechnicianProfile,
  type TechnicianQualification,
} from '../api/technicians'
import { listServiceNetworks, type ServiceNetwork } from '../api/networks'
import { isConflictError, safeAccessDeniedMessage } from '../api/client'
import ImpactPanel from '../components/ImpactPanel.vue'
import VersionedCommandForm from '../components/VersionedCommandForm.vue'

const route = useRoute()
const profileId = computed(() => String(route.params.id ?? ''))

const loading = ref(false)
const busy = ref(false)
const denied = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)
const profile = ref<TechnicianProfile | null>(null)
const qualifications = ref<TechnicianQualification[]>([])
const memberships = ref<NetworkTechnicianMembership[]>([])
const networks = ref<ServiceNetwork[]>([])
const networkId = ref('')
const reason = ref('ADMIN_TECHNICIAN_CLEARANCE')

const impactItems = computed(() => [
  '停用师傅档案后可接单立即失败关闭',
  '将生成 clearance 待办',
  `当前网点关系 ${memberships.value.filter((m) => m.status === 'ACTIVE').length} 条`,
])

async function load() {
  loading.value = true
  denied.value = false
  error.value = null
  try {
    const result = await getTechnicianProfile(profileId.value)
    profile.value = result.data
    qualifications.value = (await listTechnicianQualifications(profileId.value)).items
    memberships.value = (
      await listNetworkTechnicianMemberships({ technicianProfileId: profileId.value })
    ).items
    networks.value = (await listServiceNetworks()).items
    if (!networkId.value && networks.value[0]) {
      networkId.value = networks.value[0].id
    }
  } catch (err) {
    profile.value = null
    denied.value = true
    error.value = safeAccessDeniedMessage(err)
  } finally {
    loading.value = false
  }
}

async function linkNetwork() {
  if (!networkId.value) {
    error.value = '请选择网点'
    return
  }
  busy.value = true
  message.value = null
  error.value = null
  try {
    await createNetworkTechnicianMembership({
      networkId: networkId.value,
      technicianProfileId: profileId.value,
      validFrom: new Date().toISOString(),
    })
    message.value = '网点服务关系已建立，已重读权威状态'
    await load()
  } catch (err) {
    error.value = safeAccessDeniedMessage(err)
  } finally {
    busy.value = false
  }
}

async function toggle() {
  if (!profile.value) return
  busy.value = true
  message.value = null
  error.value = null
  try {
    const body = { reason: reason.value.trim() }
    if (profile.value.status === 'ACTIVE') {
      await disableTechnicianProfile(profileId.value, profile.value.version, body)
    } else {
      await enableTechnicianProfile(profileId.value, profile.value.version, body)
    }
    message.value = '师傅生命周期已更新，已重读权威状态'
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

watch(profileId, () => {
  if (profileId.value) void load()
})
onMounted(() => {
  if (profileId.value) void load()
})
</script>

<template>
  <section class="page" data-testid="technician-detail-page">
    <header class="top">
      <div>
        <h2>师傅详情</h2>
        <p class="meta">档案、资质、网点关系</p>
      </div>
      <button type="button" :disabled="loading" @click="load">刷新</button>
    </header>

    <p v-if="denied" class="error" data-testid="access-denied">{{ error }}</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <p v-else-if="loading">加载中…</p>

    <template v-else-if="profile">
      <p v-if="message" class="ok">{{ message }}</p>
      <article class="card">
        <h3>{{ profile.displayName }}</h3>
        <dl>
          <div><dt>status</dt><dd data-testid="technician-status">{{ profile.status }}</dd></div>
          <div><dt>version</dt><dd>{{ profile.version }}</dd></div>
        </dl>
      </article>

      <article class="card">
        <h3>资质</h3>
        <ul v-if="qualifications.length">
          <li v-for="item in qualifications" :key="item.id">
            {{ item.qualificationCode }} · {{ item.status }}
          </li>
        </ul>
        <p v-else class="muted">无资质记录</p>
      </article>

      <article class="card">
        <h3>网点服务关系</h3>
        <ul>
          <li v-for="item in memberships" :key="item.id">
            network={{ item.serviceNetworkId }} · {{ item.status }}
          </li>
        </ul>
        <label>
          绑定网点
          <select v-model="networkId" aria-label="technician network">
            <option v-for="item in networks" :key="item.id" :value="item.id">
              {{ item.networkName }}
            </option>
          </select>
        </label>
        <button type="button" :disabled="busy" @click="linkNetwork">建立关系</button>
      </article>

      <VersionedCommandForm
        :title="profile.status === 'ACTIVE' ? '停用师傅档案' : '启用师傅档案'"
        :version="profile.version"
        :busy="busy"
        @submit="toggle"
      >
        <ImpactPanel :items="impactItems" />
        <label>reason<input v-model="reason" aria-label="technician lifecycle reason" /></label>
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
.meta,
.muted {
  color: #627d98;
}
.card {
  background: #fff;
  border-radius: 12px;
  padding: 1rem 1.15rem;
  box-shadow: 0 1px 3px rgb(16 42 67 / 8%);
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
