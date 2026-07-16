<script setup lang="ts">
import { ref } from 'vue'
import {
  beginLocalOidcLogin,
  clearLocalOidcSession,
  currentLocalOidcSession,
  isLocalOidcAvailable,
} from '../auth/oidc'

const available = isLocalOidcAvailable()
const session = ref(currentLocalOidcSession())
const error = ref<string | null>(null)

async function login() {
  error.value = null
  try {
    await beginLocalOidcLogin()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '无法启动 OIDC 登录'
  }
}

function logout() {
  clearLocalOidcSession()
  session.value = currentLocalOidcSession()
}
</script>

<template>
  <section class="card">
    <h2>身份登录</h2>
    <p v-if="available">
      本地开发使用 Keycloak Authorization Code + PKCE。前端只携带 access token，
      tenant/capability 仍由后端失败关闭校验。
    </p>
    <p v-else class="warning">
      当前构建未配置正式 OIDC 登录，已失败关闭。生产环境禁止粘贴或硬编码 JWT。
    </p>
    <p v-if="session.authenticated" class="ok">
      已登录，令牌将在 {{ new Date(session.expiresAt ?? 0).toLocaleString() }} 前过期。
    </p>
    <button v-if="available && !session.authenticated" type="button" @click="login">
      使用本地 Keycloak 登录
    </button>
    <button v-if="session.authenticated" type="button" @click="logout">清除本机会话</button>
    <p v-if="error" class="error">{{ error }}</p>
  </section>
</template>

<style scoped>
.card {
  background: #fff;
  border-radius: 12px;
  padding: 1rem 1.25rem;
  max-width: 720px;
}
button {
  border: 1px solid #bcccdc;
  background: #243b53;
  color: #fff;
  border-radius: 6px;
  padding: 0.45rem 0.9rem;
}
.ok {
  color: #054e31;
}
.warning,
.error {
  color: #b42318;
}
</style>
