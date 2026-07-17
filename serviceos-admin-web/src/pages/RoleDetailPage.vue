<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getRole, type Role } from '../api/authorizationGovernance'
import { safeAccessDeniedMessage } from '../api/client'

const route = useRoute()
const roleId = computed(() => String(route.params.id ?? ''))
const loading = ref(false)
const denied = ref(false)
const error = ref<string | null>(null)
const role = ref<Role | null>(null)

async function load() {
  loading.value = true
  denied.value = false
  error.value = null
  try {
    role.value = (await getRole(roleId.value)).data
  } catch (err) {
    role.value = null
    denied.value = true
    error.value = safeAccessDeniedMessage(err)
  } finally {
    loading.value = false
  }
}

watch(roleId, () => {
  if (roleId.value) void load()
})
onMounted(() => {
  if (roleId.value) void load()
})
</script>

<template>
  <section class="page" data-testid="role-detail-page">
    <header class="top">
      <h2>角色详情</h2>
      <button type="button" :disabled="loading" @click="load">刷新</button>
    </header>
    <p v-if="denied" class="error" data-testid="access-denied">{{ error }}</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <p v-else-if="loading">加载中…</p>
    <article v-else-if="role" class="card">
      <h3>{{ role.roleName }}</h3>
      <dl>
        <div><dt>roleCode</dt><dd>{{ role.roleCode }}</dd></div>
        <div><dt>roleKind</dt><dd>{{ role.roleKind }}</dd></div>
        <div><dt>status</dt><dd>{{ role.roleStatus }}</dd></div>
        <div><dt>version</dt><dd>{{ role.version }}</dd></div>
      </dl>
      <h4>Capabilities</h4>
      <ul>
        <li v-for="code in role.capabilityCodes" :key="code">{{ code }}</li>
      </ul>
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
}
.card {
  background: #fff;
  border-radius: 12px;
  padding: 1rem 1.15rem;
  box-shadow: 0 1px 3px rgb(16 42 67 / 8%);
}
dl {
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
</style>
