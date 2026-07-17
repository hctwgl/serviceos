<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import {
  probeGovernanceAccess,
  type GovernanceAccess,
} from '../nav/governanceAccess'
import { AUTH_REQUIRED_EVENT, currentLocalOidcSession } from '../auth/oidc'

const access = ref<GovernanceAccess>({
  users: false,
  organizations: false,
  networks: false,
  technicians: false,
  roles: false,
  grants: false,
})

async function refreshAccess() {
  access.value = await probeGovernanceAccess()
}

onMounted(() => {
  void refreshAccess()
  window.addEventListener(AUTH_REQUIRED_EVENT, () => {
    access.value = {
      users: false,
      organizations: false,
      networks: false,
      technicians: false,
      roles: false,
      grants: false,
    }
  })
  // 登录页返回后重新探测
  window.addEventListener('focus', () => {
    if (currentLocalOidcSession().authenticated) {
      void refreshAccess()
    }
  })
})
</script>

<template>
  <div class="shell">
    <aside class="nav">
      <h1>ServiceOS Admin</h1>
      <p class="hint">运营外壳（M101～M187）。写操作仅走服务端命令与 Capability。</p>
      <RouterLink to="/reviews">审核队列</RouterLink>
      <RouterLink to="/corrections">整改跟踪</RouterLink>
      <RouterLink to="/tasks">任务目录</RouterLink>
      <RouterLink to="/sla">SLA 工作台</RouterLink>
      <RouterLink to="/exceptions">运营异常</RouterLink>
      <RouterLink to="/integration/inbound">入站队列</RouterLink>
      <RouterLink to="/integration/outbound">外发交付</RouterLink>
      <RouterLink to="/work-orders">工单目录</RouterLink>
      <RouterLink to="/projects">项目目录</RouterLink>
      <template v-if="access.users || access.organizations || access.networks || access.technicians || access.roles || access.grants">
        <p class="section">用户中心</p>
        <RouterLink v-if="access.users" to="/users" data-testid="nav-users">用户目录</RouterLink>
        <RouterLink v-if="access.organizations" to="/organizations" data-testid="nav-organizations">
          企业组织
        </RouterLink>
        <RouterLink v-if="access.networks" to="/networks" data-testid="nav-networks">
          合作组织与网点
        </RouterLink>
        <RouterLink v-if="access.technicians" to="/technicians" data-testid="nav-technicians">
          师傅档案
        </RouterLink>
        <RouterLink v-if="access.roles" to="/roles" data-testid="nav-roles">角色与 Capability</RouterLink>
        <RouterLink v-if="access.grants" to="/grants" data-testid="nav-grants">授权与委托</RouterLink>
      </template>
      <RouterLink to="/work-orders/lookup">按 ID 打开</RouterLink>
      <RouterLink to="/settings/token">身份登录</RouterLink>
    </aside>
    <main class="content">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.shell {
  display: grid;
  grid-template-columns: 240px 1fr;
  min-height: 100vh;
  font-family: Inter, system-ui, sans-serif;
  color: #102a43;
  background: #f5f7fa;
}
.nav {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1.25rem;
  background: #243b53;
  color: #f0f4f8;
}
.nav h1 {
  margin: 0;
  font-size: 1.1rem;
}
.hint {
  margin: 0 0 0.5rem;
  font-size: 0.8rem;
  color: #9fb3c8;
}
.section {
  margin: 0.5rem 0 0;
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: #9fb3c8;
}
.nav a {
  color: #d9e2ec;
  text-decoration: none;
  padding: 0.4rem 0.55rem;
  border-radius: 6px;
}
.nav a.router-link-active {
  background: #334e68;
  color: #fff;
}
.content {
  padding: 1.5rem;
}
</style>
