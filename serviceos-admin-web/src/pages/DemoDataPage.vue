<script setup lang="ts">
/**
 * 演示数据管理（仅开发/演示环境入口）。
 * 实际初始化走本地脚本，避免生产代码硬编码密码或自动灌库。
 */
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import PageState from '../components/PageState.vue'

const isDev = import.meta.env.DEV
const networkPortalUrl =
  import.meta.env.VITE_NETWORK_PORTAL_URL?.trim() ||
  (import.meta.env.DEV ? 'http://localhost:5174' : '')
const technicianPortalUrl =
  import.meta.env.VITE_TECHNICIAN_PORTAL_URL?.trim() ||
  (import.meta.env.DEV ? 'http://localhost:5175' : '')

const accounts = [
  {
    portal: '管理端',
    username: 'local-admin（或 Keycloak 本地运营账号）',
    role: '平台运营 / 项目管理员',
    org: '本地租户 tenant-local',
    actions: '初审、分配网点、平台审核、整改跟踪、SLA、演示数据',
  },
  {
    portal: '网点端',
    username: '见 serviceos-deploy/demo/README.md',
    role: '网点调度',
    org: '济南恒通新能源服务中心（演示）',
    actions: '查看待办、指派师傅、预约、代补资料',
  },
  {
    portal: '师傅端',
    username: '见 serviceos-deploy/demo/README.md',
    role: '服务师傅',
    org: '张师傅 / 李师傅（演示）',
    actions: '联系客户、预约、上门、提交资料、整改重提',
  },
]

const commands = computed(() => [
  {
    title: '初始化演示数据',
    cmd: 'bash serviceos-deploy/demo/init-demo.sh',
  },
  {
    title: '重置演示数据',
    cmd: 'bash serviceos-deploy/demo/reset-demo.sh',
  },
  {
    title: '清空演示数据',
    cmd: 'bash serviceos-deploy/demo/clear-demo.sh',
  },
])

function copy(text: string) {
  void navigator.clipboard?.writeText(text)
}
</script>

<template>
  <section class="demo" data-testid="demo-data-page">
    <h1>演示数据管理</h1>
    <PageState
      v-if="!isDev"
      kind="forbidden"
      description="演示数据管理仅在开发/演示环境开放，生产环境默认关闭。"
    />
    <template v-else>
      <p class="subtitle">
        演示数据与正式 migration 隔离，可重复执行且幂等。密码通过本地环境变量或 Keycloak
        初始化提供，不写入生产代码。
      </p>

      <section class="panel">
        <h2>一键脚本</h2>
        <div v-for="item in commands" :key="item.title" class="cmd-row">
          <div>
            <strong>{{ item.title }}</strong>
            <pre>{{ item.cmd }}</pre>
          </div>
          <button type="button" @click="copy(item.cmd)">复制命令</button>
        </div>
        <p class="hint">
          脚本要求本地 Docker Compose PostgreSQL 已启动，且后端使用本地开发配置。
        </p>
      </section>

      <section class="panel">
        <h2>演示主体</h2>
        <ul>
          <li>车企：吉利汽车</li>
          <li>项目：吉利家充安装试点项目</li>
          <li>服务区域：山东省济南市</li>
          <li>服务网点：济南恒通新能源服务中心</li>
          <li>服务师傅：张师傅、李师傅</li>
          <li>客户：王先生（演示手机号）</li>
          <li>车辆：银河 E5</li>
          <li>工单类型：家用充电桩勘测安装</li>
        </ul>
      </section>

      <section class="panel">
        <h2>演示账号</h2>
        <table>
          <thead>
            <tr>
              <th>门户</th>
              <th>账号说明</th>
              <th>角色</th>
              <th>所属组织</th>
              <th>可执行操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="account in accounts" :key="account.portal">
              <td>{{ account.portal }}</td>
              <td>{{ account.username }}</td>
              <td>{{ account.role }}</td>
              <td>{{ account.org }}</td>
              <td>{{ account.actions }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <section class="panel">
        <h2>黄金流程入口</h2>
        <div class="links">
          <RouterLink to="/work-orders/golden-path">打开工单全流程演练</RouterLink>
          <RouterLink to="/work-orders">工单中心</RouterLink>
          <RouterLink to="/reviews">审核中心</RouterLink>
          <RouterLink to="/corrections">整改中心</RouterLink>
          <a v-if="networkPortalUrl" :href="networkPortalUrl">打开网点端</a>
          <a v-if="technicianPortalUrl" :href="technicianPortalUrl">打开师傅端</a>
        </div>
      </section>

      <section class="panel">
        <h2>演示工单编号约定</h2>
        <p>业务编号形如 <code>WO-DEMO-20260719-001</code>，禁止要求用户用 UUID 查找。</p>
        <p>兼容既有试点单：<code>ADMIN-PILOT-001</code>。</p>
      </section>
    </template>
  </section>
</template>

<style scoped>
.demo {
  display: grid;
  gap: 1rem;
}
.subtitle,
.hint {
  color: #627d98;
  font-size: 0.92rem;
}
.panel {
  background: #fff;
  border-radius: 12px;
  padding: 1rem 1.25rem;
  box-shadow: 0 1px 3px rgb(16 42 67 / 8%);
}
.cmd-row {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: start;
  padding: 0.5rem 0;
  border-bottom: 1px solid #e2e8f0;
}
pre {
  margin: 0.35rem 0 0;
  padding: 0.5rem 0.65rem;
  background: #f0f4f8;
  border-radius: 6px;
  font-size: 0.85rem;
  overflow: auto;
}
button {
  border: 1px solid #bcccdc;
  background: #f0f4f8;
  border-radius: 6px;
  padding: 0.4rem 0.75rem;
  cursor: pointer;
  white-space: nowrap;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th,
td {
  text-align: left;
  padding: 0.45rem 0.35rem;
  border-bottom: 1px solid #e2e8f0;
  font-size: 0.88rem;
  vertical-align: top;
}
.links {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}
.links a {
  color: #0b69a3;
}
</style>
