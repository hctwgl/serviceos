<script setup lang="ts">
import {
  AppstoreOutlined,
  AuditOutlined,
  BellOutlined,
  CheckSquareOutlined,
  DownOutlined,
  FileDoneOutlined,
  HomeOutlined,
  MenuFoldOutlined,
  QuestionCircleOutlined,
  SearchOutlined,
  SettingOutlined,
  TeamOutlined,
  ToolOutlined,
  SafetyCertificateFilled,
} from '@serviceos/design-system'
import { currentIdentity, endSession } from '@serviceos/auth-context'
import { loadAdminWorkbench } from '@serviceos/api-client'
import { useQuery } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'

const route = useRoute()
const identity = currentIdentity()
const workbench = useQuery({ queryKey: ['admin-workbench'], queryFn: loadAdminWorkbench })
const pendingCount = computed(() => workbench.data.value?.priorityCount ?? null)
const userMenuOpen = ref(false)

function logout() {
  endSession()
}

const sections = [
  { key: 'workbench', label: '工作台', path: '/workbench', icon: HomeOutlined },
  {
    key: 'work-orders',
    label: '工单运营',
    path: '/work-orders',
    icon: FileDoneOutlined,
    children: ['我的工单', '工单池', '工单看板', '工单查询'],
  },
  { key: 'fulfillment', label: '服务履约', path: '/work-orders?view=fulfillment', icon: CheckSquareOutlined },
  { key: 'review', label: '审核与整改', path: '/work-orders?view=review', icon: AuditOutlined },
  { key: 'projects', label: '客户与项目', icon: TeamOutlined },
  { key: 'resources', label: '组织与资源', icon: AppstoreOutlined },
  { key: 'system', label: '系统管理', icon: SettingOutlined },
  { key: 'audit', label: '审计与监控', icon: ToolOutlined },
]

const activeKey = computed(() => {
  if (!route.path.startsWith('/work-orders')) return 'workbench'
  if (route.query.view === 'fulfillment') return 'fulfillment'
  if (route.query.view === 'review') return 'review'
  return 'work-orders'
})
</script>

<template>
  <div class="admin-shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark"><SafetyCertificateFilled /></span>
        <span>ServiceOS</span>
      </div>
      <nav class="main-nav" aria-label="主导航">
        <div v-for="item in sections" :key="item.label" class="nav-group">
          <RouterLink v-if="item.path" :to="item.path" class="nav-item" :class="{ active: activeKey === item.key }">
            <component :is="item.icon" />
            <span>{{ item.label }}</span>
            <DownOutlined v-if="item.children" class="nav-arrow" />
          </RouterLink>
          <span v-else class="nav-item nav-item-disabled" aria-disabled="true">
            <component :is="item.icon" />
            <span>{{ item.label }}</span>
          </span>
          <div v-if="item.children && activeKey === item.key" class="sub-nav">
            <span v-for="child in item.children" :key="child" :class="{ selected: child === '工单池' }">{{ child }}</span>
          </div>
        </div>
      </nav>
      <button class="collapse-button" type="button"><MenuFoldOutlined /> 收起菜单</button>
    </aside>

    <div class="shell-main">
      <header class="topbar">
        <button class="project-switcher" type="button">项目：全部项目 <DownOutlined /></button>
        <label class="global-search">
          <SearchOutlined />
          <input aria-label="全局搜索" placeholder="搜索工单、客户、项目、师傅" />
        </label>
        <div class="top-actions">
          <button type="button"><BellOutlined /><span>通知</span></button>
          <RouterLink class="top-action-link" to="/workbench">
            <CheckSquareOutlined /><span>待办</span><b v-if="pendingCount !== null">{{ pendingCount }}</b>
          </RouterLink>
          <button type="button"><QuestionCircleOutlined /><span>帮助</span></button>
          <button class="user-button" type="button" :aria-expanded="userMenuOpen" @click="userMenuOpen = !userMenuOpen">
            <span class="avatar">{{ identity.initials }}</span><span>{{ identity.displayName }}</span><DownOutlined />
          </button>
          <div v-if="userMenuOpen" class="user-menu">
            <strong>{{ identity.displayName }}</strong>
            <span>当前登录账号</span>
            <button type="button" @click="logout">退出登录</button>
          </div>
        </div>
      </header>
      <main class="page-main"><RouterView /></main>
    </div>
  </div>
</template>
