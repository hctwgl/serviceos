<script setup lang="ts">
defineProps<{
  title: string
  columns: string[]
  rows: Array<Record<string, unknown>>
  loading: boolean
  error: string | null
  asOf?: string | null
  nextCursor?: string | null
}>()

const emit = defineEmits<{ refresh: []; next: [] }>()
</script>

<template>
  <section class="queue">
    <header>
      <div>
        <h2>{{ title }}</h2>
        <p v-if="asOf" class="meta">asOf {{ asOf }}</p>
      </div>
      <div class="actions">
        <button type="button" :disabled="loading" @click="emit('refresh')">刷新</button>
        <button type="button" :disabled="loading || !nextCursor" @click="emit('next')">下一页</button>
      </div>
    </header>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-else-if="loading">加载中…</p>
    <table v-else>
      <thead>
        <tr>
          <th v-for="column in columns" :key="column">{{ column }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="rows.length === 0">
          <td :colspan="columns.length">暂无数据</td>
        </tr>
        <tr v-for="(row, index) in rows" :key="index">
          <td v-for="column in columns" :key="column">{{ row[column] ?? '—' }}</td>
        </tr>
      </tbody>
    </table>
  </section>
</template>

<style scoped>
.queue {
  background: #fff;
  border-radius: 12px;
  padding: 1rem 1.25rem;
  box-shadow: 0 1px 3px rgb(16 42 67 / 8%);
}
header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
  margin-bottom: 1rem;
}
h2 {
  margin: 0;
}
.meta {
  margin: 0.25rem 0 0;
  color: #627d98;
  font-size: 0.85rem;
}
.actions {
  display: flex;
  gap: 0.5rem;
}
button {
  border: 1px solid #bcccdc;
  background: #f0f4f8;
  border-radius: 6px;
  padding: 0.4rem 0.75rem;
  cursor: pointer;
}
button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.error {
  color: #9b1c1c;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th,
td {
  text-align: left;
  padding: 0.55rem 0.4rem;
  border-bottom: 1px solid #e2e8f0;
  font-size: 0.92rem;
  vertical-align: top;
}
th {
  color: #486581;
  font-weight: 600;
}
</style>
