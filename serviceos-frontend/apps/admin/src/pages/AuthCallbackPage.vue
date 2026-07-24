<script setup lang="ts">
import { completeLogin } from '@serviceos/auth-context'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { SafetyCertificateFilled } from '@serviceos/design-system'

const router = useRouter()
const error = ref('')

onMounted(async () => {
  try {
    const returnTo = await completeLogin(globalThis.location.search)
    await router.replace(returnTo)
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '登录失败'
  }
})
</script>

<template>
  <main class="auth-callback">
    <div class="auth-card">
      <div class="brand-mark"><SafetyCertificateFilled /></div>
      <h1>{{ error ? '登录未完成' : '正在完成登录' }}</h1>
      <p>{{ error || '请稍候，正在验证身份与权限。' }}</p>
    </div>
  </main>
</template>
