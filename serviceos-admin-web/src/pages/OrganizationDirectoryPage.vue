<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  createOrganization,
  listOrganizations,
  type OrganizationPage,
} from '../api/organizations'
import { safeAccessDeniedMessage } from '../api/client'
import ExternalAuthoritativeBadge from '../components/ExternalAuthoritativeBadge.vue'
import StatusBadge from '../components/StatusBadge.vue'

const loading = ref(false)
const busy = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)
const page = ref<OrganizationPage | null>(null)
const code = ref('')
const name = ref('')
const authorityMode = ref<'LOCAL' | 'EXTERNAL_AUTHORITATIVE'>('LOCAL')
const sourceSystem = ref('HR_DEMO')
const sourceKey = ref('')

async function load() {
  loading.value = true
  error.value = null
  try {
    page.value = await listOrganizations()
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
    const created = await createOrganization({
      code: code.value.trim(),
      name: name.value.trim(),
      authorityMode: authorityMode.value,
      sourceSystem: authorityMode.value === 'EXTERNAL_AUTHORITATIVE' ? sourceSystem.value.trim() : null,
      sourceKey: authorityMode.value === 'EXTERNAL_AUTHORITATIVE' ? sourceKey.value.trim() : null,
    })
    message.value = `已创建组织 ${created.data.name}`
    code.value = ''
    name.value = ''
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
  <section class="page" data-testid="organization-directory-page">
    <header>
      <h2>企业组织</h2>
      <p class="hint">组织树与任职治理；EXTERNAL_AUTHORITATIVE 来源字段只读。</p>
    </header>

    <article class="card form">
      <h3>创建组织</h3>
      <label>code<input v-model="code" aria-label="organization code" /></label>
      <label>name<input v-model="name" aria-label="organization name" /></label>
      <label>
        authorityMode
        <select v-model="authorityMode" aria-label="organization authorityMode">
          <option value="LOCAL">LOCAL</option>
          <option value="EXTERNAL_AUTHORITATIVE">EXTERNAL_AUTHORITATIVE</option>
        </select>
      </label>
      <template v-if="authorityMode === 'EXTERNAL_AUTHORITATIVE'">
        <label>sourceSystem<input v-model="sourceSystem" aria-label="organization sourceSystem" /></label>
        <label>sourceKey<input v-model="sourceKey" aria-label="organization sourceKey" /></label>
      </template>
      <button type="button" :disabled="busy" @click="create">创建</button>
      <p v-if="message" class="ok">{{ message }}</p>
    </article>

    <p v-if="error" class="error" data-testid="access-denied">{{ error }}</p>
    <p v-else-if="loading">加载中…</p>
    <table v-else-if="page" class="table" data-testid="organization-table">
      <thead>
        <tr>
          <th>名称</th>
          <th>编码</th>
          <th>权威模式</th>
          <th>状态</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in page.items" :key="item.id">
          <td>{{ item.name }}</td>
          <td>{{ item.code }}</td>
          <td>
            <ExternalAuthoritativeBadge
              :authority-mode="item.authorityMode"
              :source-system="item.sourceSystem"
              :source-key="item.sourceKey"
              :last-synced-at="item.updatedAt"
            />
          </td>
          <td><StatusBadge :status="item.status" /></td>
          <td>
            <RouterLink :to="{ name: 'ADMIN.ORGANIZATION.DETAIL', params: { id: item.id } }">
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
  vertical-align: top;
}
.error {
  color: #b42318;
}
.ok {
  color: #054e31;
}
</style>
