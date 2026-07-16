<script setup lang="ts">
import { ref } from 'vue'

const token = ref(localStorage.getItem('serviceos.accessToken') ?? '')
const saved = ref(false)

function save() {
  localStorage.setItem('serviceos.accessToken', token.value.trim())
  saved.value = true
}
</script>

<template>
  <section class="card">
    <h2>访问令牌</h2>
    <p>
      粘贴 OIDC/JWT access token。前端只负责携带 Authorization，不做 tenant/capability 判定。
    </p>
    <textarea v-model="token" rows="6" placeholder="eyJ..." />
    <button type="button" @click="save">保存到本机</button>
    <p v-if="saved" class="ok">已保存</p>
  </section>
</template>

<style scoped>
.card {
  background: #fff;
  border-radius: 12px;
  padding: 1rem 1.25rem;
  max-width: 720px;
}
textarea {
  width: 100%;
  margin: 0.75rem 0;
  font-family: ui-monospace, monospace;
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
</style>
