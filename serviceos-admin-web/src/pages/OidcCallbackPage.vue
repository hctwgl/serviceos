<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { completeLocalOidcLogin } from '../auth/oidc'

const router = useRouter()
const error = ref<string | null>(null)

onMounted(async () => {
  try {
    await completeLocalOidcLogin(window.location.search)
    await router.replace('/work-orders')
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'OIDC 登录失败'
  }
})
</script>

<template>
  <main class="callback">
    <h1>ServiceOS Admin 登录</h1>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-else>正在校验本地 Keycloak 回调…</p>
    <RouterLink v-if="error" to="/settings/token">返回登录页</RouterLink>
  </main>
</template>

<style scoped>
.callback {
  max-width: 680px;
  margin: 4rem auto;
  padding: 1.5rem;
  font-family: Inter, system-ui, sans-serif;
}
.error {
  color: #b42318;
}
</style>
