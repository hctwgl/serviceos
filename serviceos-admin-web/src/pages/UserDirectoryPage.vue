<script setup lang="ts">
import PageContainer from '../patterns/PageContainer.vue'
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { listSecurityPrincipals, type SecurityPrincipalPage } from '../api/securityPrincipals'
import { safeAccessDeniedMessage } from '../api/client'
import StatusBadge from '../components/StatusBadge.vue'
import { statusLabel, statusOptions } from '../product/statusLabels'

const loading = ref(false)
const error = ref<string | null>(null)
const query = ref('')
const status = ref('')
const page = ref<SecurityPrincipalPage | null>(null)

async function load(cursor?: string) {
  loading.value = true
  error.value = null
  try {
    page.value = await listSecurityPrincipals({
      query: query.value.trim() || undefined,
      status: status.value || undefined,
      cursor,
      limit: '20',
    })
  } catch (err) {
    page.value = null
    error.value = safeAccessDeniedMessage(err)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div data-testid="user-directory-page">
  <PageContainer title="用户目录" description="查询平台用户与主体档案。"><form class="filters" @submit.prevent="load()">
      <label>
        搜索
        <input
          v-model="query"
          aria-label="user directory search"
          placeholder="姓名或工号"
        />
      </label>
      <label>
        状态
        <select v-model="status" aria-label="user status filter">
          <option value="">全部</option>
          <option v-for="opt in statusOptions(['ACTIVE', 'DISABLED'])" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </option>
        </select>
      </label>
      <button type="submit" :disabled="loading">查询</button>
    </form>
    <p v-if="error" class="error" data-testid="access-denied">{{ error }}</p>
    <p v-else-if="loading">加载中…</p>
    <table v-else-if="page" class="table" data-testid="user-directory-table">
      <thead>
        <tr>
          <th>显示名</th>
          <th>工号</th>
          <th>状态</th>
          <th>类型</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in page.items" :key="item.id">
          <td>{{ item.displayName }}</td>
          <td>{{ item.employeeNumber || '—' }}</td>
          <td><StatusBadge :status="item.status" /></td>
          <td>{{ statusLabel(item.type) }}</td>
          <td>
            <RouterLink :to="{ name: 'ADMIN.USER.DETAIL', params: { id: item.id } }">
              打开
            </RouterLink>
          </td>
        </tr>
      </tbody>
    </table>
    <button
      v-if="page?.nextCursor"
      type="button"
      :disabled="loading"
      @click="load(page.nextCursor ?? undefined)"
    >
      下一页
    </button>
  </PageContainer>
  </div>
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
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  align-items: end;
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
  border-radius: 12px;
  overflow: hidden;
}
th,
td {
  text-align: left;
  padding: 0.65rem 0.8rem;
  border-bottom: 1px solid #f0f4f8;
  font-size: 0.9rem;
}
.error {
  color: #b42318;
}
</style>
