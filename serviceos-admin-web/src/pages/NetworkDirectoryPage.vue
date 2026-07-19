<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  createPartnerOrganization,
  createServiceNetwork,
  listPartnerOrganizations,
  listServiceNetworks,
  type PartnerOrganization,
  type ServiceNetworkPage,
} from '../api/networks'
import { safeAccessDeniedMessage } from '../api/client'
import PrincipalPicker from '../components/PrincipalPicker.vue'
import StatusBadge from '../components/StatusBadge.vue'

const loading = ref(false)
const busy = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)
const partners = ref<PartnerOrganization[]>([])
const networks = ref<ServiceNetworkPage | null>(null)
const partnerCode = ref('')
const partnerName = ref('')
const networkCode = ref('')
const networkName = ref('')
const partnerId = ref('')
const invitePrincipalId = ref<string | null>(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    partners.value = (await listPartnerOrganizations()).items
    networks.value = await listServiceNetworks()
    if (!partnerId.value && partners.value[0]) {
      partnerId.value = partners.value[0].id
    }
  } catch (err) {
    partners.value = []
    networks.value = null
    error.value = safeAccessDeniedMessage(err)
  } finally {
    loading.value = false
  }
}

async function createPartner() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    const created = await createPartnerOrganization({
      code: partnerCode.value.trim(),
      name: partnerName.value.trim(),
    })
    message.value = `合作组织已创建：${created.data.name}`
    partnerCode.value = ''
    partnerName.value = ''
    await load()
  } catch (err) {
    error.value = safeAccessDeniedMessage(err)
  } finally {
    busy.value = false
  }
}

async function createNetwork() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    await createServiceNetwork({
      partnerOrganizationId: partnerId.value,
      networkCode: networkCode.value.trim(),
      networkName: networkName.value.trim(),
    })
    message.value = '网点已创建'
    networkCode.value = ''
    networkName.value = ''
    await load()
  } catch (err) {
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
  <section class="page" data-testid="network-directory-page">
    <header>
      <h2>合作组织与网点</h2>
      <p class="hint">网点人员邀请通过目录选择器完成，不要求粘贴 UUID。</p>
    </header>

    <article class="card form">
      <h3>创建合作组织</h3>
      <label>code<input v-model="partnerCode" aria-label="partner code" /></label>
      <label>name<input v-model="partnerName" aria-label="partner name" /></label>
      <button type="button" :disabled="busy" @click="createPartner">创建合作组织</button>
    </article>

    <article class="card form">
      <h3>创建网点</h3>
      <label>
        合作组织
        <select v-model="partnerId" aria-label="partner organization">
          <option v-for="item in partners" :key="item.id" :value="item.id">
            {{ item.name }}
          </option>
        </select>
      </label>
      <label>networkCode<input v-model="networkCode" aria-label="network code" /></label>
      <label>networkName<input v-model="networkName" aria-label="network name" /></label>
      <PrincipalPicker
        v-model="invitePrincipalId"
        label="（可选）预选网点人员，详情页完成邀请"
      />
      <button type="button" :disabled="busy" @click="createNetwork">创建网点</button>
      <p v-if="message" class="ok">{{ message }}</p>
    </article>

    <p v-if="error" class="error" data-testid="access-denied">{{ error }}</p>
    <p v-else-if="loading">加载中…</p>
    <table v-else-if="networks" class="table" data-testid="network-table">
      <thead>
        <tr>
          <th>网点</th>
          <th>编码</th>
          <th>状态</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in networks.items" :key="item.id">
          <td>{{ item.networkName }}</td>
          <td>{{ item.networkCode }}</td>
          <td><StatusBadge :status="item.status" /></td>
          <td>
            <RouterLink :to="{ name: 'ADMIN.NETWORK.DETAIL', params: { id: item.id } }">
              打开
            </RouterLink>
          </td>
        </tr>
      </tbody>
    </table>
  </section>
</template>

<style scoped>
.page {
  display: grid;
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
.form {
  display: grid;
  gap: 0.55rem;
  max-width: 40rem;
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
.table {
  width: 100%;
  border-collapse: collapse;
  background: #fff;
}
th,
td {
  text-align: left;
  padding: 0.65rem 0.8rem;
  border-bottom: 1px solid #f0f4f8;
}
.error {
  color: #b42318;
}
.ok {
  color: #054e31;
}
</style>
