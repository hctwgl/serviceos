<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  createRole,
  listCapabilities,
  listRoles,
  type Capability,
  type RolePage,
} from '../api/authorizationGovernance'
import { safeAccessDeniedMessage } from '../api/client'

const loading = ref(false)
const busy = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)
const page = ref<RolePage | null>(null)
const capabilities = ref<Capability[]>([])
const roleCode = ref('')
const roleName = ref('')
const selectedCaps = ref<string[]>([])

async function load() {
  loading.value = true
  error.value = null
  try {
    page.value = await listRoles()
    capabilities.value = await listCapabilities()
  } catch (err) {
    page.value = null
    error.value = safeAccessDeniedMessage(err)
  } finally {
    loading.value = false
  }
}

async function create() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    if (!selectedCaps.value.length) {
      throw new Error('至少选择一项 Capability')
    }
    await createRole({
      roleCode: roleCode.value.trim(),
      roleName: roleName.value.trim(),
      capabilityCodes: selectedCaps.value,
    })
    message.value = '角色已创建'
    roleCode.value = ''
    roleName.value = ''
    selectedCaps.value = []
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : safeAccessDeniedMessage(err)
  } finally {
    busy.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <section class="page" data-testid="role-directory-page">
    <header>
      <h2>角色与 Capability</h2>
      <p class="hint">只能引用目录能力，不能改写风险级别。</p>
    </header>

    <article class="card form">
      <h3>创建租户角色</h3>
      <label>roleCode<input v-model="roleCode" aria-label="role code" /></label>
      <label>roleName<input v-model="roleName" aria-label="role name" /></label>
      <fieldset>
        <legend>capabilities</legend>
        <label v-for="cap in capabilities" :key="cap.capabilityCode" class="check">
          <input v-model="selectedCaps" type="checkbox" :value="cap.capabilityCode" />
          {{ cap.capabilityCode }}（{{ cap.riskLevel }}）
        </label>
      </fieldset>
      <button type="button" :disabled="busy" @click="create">创建角色</button>
      <p v-if="message" class="ok">{{ message }}</p>
    </article>

    <p v-if="error" class="error" data-testid="access-denied">{{ error }}</p>
    <p v-else-if="loading">加载中…</p>
    <table v-else-if="page" class="table" data-testid="role-table">
      <thead>
        <tr>
          <th>角色</th>
          <th>编码</th>
          <th>状态</th>
          <th>能力数</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in page.items" :key="item.roleId">
          <td>{{ item.roleName }}</td>
          <td>{{ item.roleCode }}</td>
          <td>{{ item.roleStatus }}</td>
          <td>{{ item.capabilityCodes.length }}</td>
          <td>
            <RouterLink :to="{ name: 'ADMIN.ROLE.DETAIL', params: { id: item.roleId } }">
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
  max-width: 48rem;
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
fieldset {
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  max-height: 14rem;
  overflow: auto;
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
