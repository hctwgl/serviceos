<script setup lang="ts">
import {
  BellOutlined,
  CheckSquareOutlined,
  DownOutlined,
  QuestionCircleOutlined,
  SearchOutlined,
} from '@serviceos/design-system'
import { currentIdentity, endSession } from '@serviceos/auth-context'
import { loadAdminWorkbench } from '@serviceos/api-client'
import { useQuery } from '@tanstack/vue-query'
import { BasicLayout } from '@vben/layouts'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const identity = currentIdentity()
const userMenuOpen = ref(false)
const workbench = useQuery({ queryKey: ['admin-workbench'], queryFn: loadAdminWorkbench })
const pendingCount = computed(() => workbench.data.value?.priorityCount ?? null)

function logout() {
  endSession()
}
</script>

<template>
  <BasicLayout class="serviceos-vben-layout" @click-logo="router.push('/workbench')">
    <template #logo-text>
      <span class="serviceos-logo-text">ServiceOS</span>
    </template>

    <template #header-left-10>
      <button class="project-switcher" type="button">
        <span>项目：全部项目</span>
        <DownOutlined />
      </button>
    </template>

    <template #header-left-20>
      <label class="global-search">
        <SearchOutlined />
        <input aria-label="全局搜索" placeholder="搜索工单、客户、项目、师傅" />
      </label>
    </template>

    <template #header-right-10>
      <button class="header-action" type="button" aria-label="通知">
        <BellOutlined />
        <span>通知</span>
      </button>
    </template>

    <template #header-right-20>
      <button class="header-action" type="button" @click="router.push('/workbench')">
        <CheckSquareOutlined />
        <span>待办</span>
        <b v-if="pendingCount !== null" class="header-badge">{{ pendingCount }}</b>
      </button>
    </template>

    <template #header-right-30>
      <button class="header-action" type="button">
        <QuestionCircleOutlined />
        <span>帮助</span>
      </button>
    </template>

    <template #user-dropdown>
      <div class="serviceos-user-area">
        <button
          class="serviceos-user-button"
          type="button"
          :aria-expanded="userMenuOpen"
          @click="userMenuOpen = !userMenuOpen"
        >
          <span class="serviceos-avatar">{{ identity.initials }}</span>
          <span>{{ identity.displayName }}</span>
          <DownOutlined />
        </button>
        <div v-if="userMenuOpen" class="serviceos-user-menu">
          <strong>{{ identity.displayName }}</strong>
          <span>当前登录账号</span>
          <button type="button" @click="logout">退出登录</button>
        </div>
      </div>
    </template>
  </BasicLayout>
</template>
