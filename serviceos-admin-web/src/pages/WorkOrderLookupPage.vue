<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const workOrderId = ref('')
const error = ref<string | null>(null)

function openWorkspace() {
  const id = workOrderId.value.trim()
  error.value = null
  if (!/^[0-9a-fA-F-]{36}$/.test(id)) {
    error.value = '请输入有效的工单 UUID'
    return
  }
  router.push({ name: 'ADMIN.WORKORDER.WORKSPACE', params: { id } })
}
</script>

<template>
  <section class="card">
    <h2>打开工单工作区</h2>
    <p>输入已授权工单 ID。页面只读，不替代领域命令或 allowed-actions。</p>
    <form class="row" @submit.prevent="openWorkspace">
      <input v-model="workOrderId" placeholder="workOrderId (UUID)" />
      <button type="submit">打开</button>
    </form>
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
.row {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.75rem;
}
input {
  flex: 1;
  padding: 0.5rem 0.65rem;
  border: 1px solid #bcccdc;
  border-radius: 6px;
}
button {
  border: 0;
  background: #243b53;
  color: #fff;
  border-radius: 6px;
  padding: 0.5rem 0.9rem;
}
.error {
  color: #9b1c1c;
}
</style>
