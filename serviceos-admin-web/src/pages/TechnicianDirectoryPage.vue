<script setup lang="ts">
import PageContainer from '../patterns/PageContainer.vue'
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  createTechnicianProfile,
  listTechnicianProfiles,
  type TechnicianProfilePage,
} from '../api/technicians'
import { safeAccessDeniedMessage } from '../api/client'
import PrincipalPicker from '../components/PrincipalPicker.vue'
import StatusBadge from '../components/StatusBadge.vue'

const loading = ref(false)
const busy = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)
const page = ref<TechnicianProfilePage | null>(null)
const principalId = ref<string | null>(null)
const displayName = ref('')

async function load() {
  loading.value = true
  error.value = null
  try {
    page.value = await listTechnicianProfiles()
  } catch (err) {
    page.value = null
    error.value = safeAccessDeniedMessage(err)
  } finally {
    loading.value = false
  }
}

async function create() {
  if (!principalId.value || !displayName.value.trim()) {
    error.value = '请选择主体并填写显示名'
    return
  }
  busy.value = true
  message.value = null
  error.value = null
  try {
    await createTechnicianProfile({
      principalId: principalId.value,
      displayName: displayName.value.trim(),
    })
    message.value = '师傅档案已创建'
    displayName.value = ''
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
  <PageContainer title="师傅档案" description="查询服务师傅档案与资质。"><article class="card form">
      <h3>创建师傅档案</h3>
      <PrincipalPicker v-model="principalId" />
      <label>displayName<input v-model="displayName" aria-label="technician displayName" /></label>
      <button type="button" :disabled="busy" @click="create">创建</button>
      <p v-if="message" class="ok">{{ message }}</p>
    </article>

    <p v-if="error" class="error" data-testid="access-denied">{{ error }}</p>
    <p v-else-if="loading">加载中…</p>
    <table v-else-if="page" class="table" data-testid="technician-table">
      <thead>
        <tr>
          <th>显示名</th>
          <th>状态</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in page.items" :key="item.id">
          <td>{{ item.displayName }}</td>
          <td><StatusBadge :status="item.status" /></td>
          <td>
            <RouterLink :to="{ name: 'ADMIN.TECHNICIAN.DETAIL', params: { id: item.id } }">
              打开
            </RouterLink>
          </td>
        </tr>
      </tbody>
    </table>
  </PageContainer>
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
input {
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
