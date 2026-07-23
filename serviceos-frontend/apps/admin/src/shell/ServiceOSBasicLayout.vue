<script setup lang="ts">
import { loadAdminClientProjectDirectory, loadAdminWorkbench } from '@serviceos/api-client'
import { currentIdentity, endSession } from '@serviceos/auth-context'
import {
  Avatar,
  Badge,
  BellOutlined,
  Button,
  CheckSquareOutlined,
  DownOutlined,
  Dropdown,
  Input,
  Menu,
  QuestionCircleOutlined,
  SearchOutlined,
} from '@serviceos/design-system'
import { useQuery } from '@tanstack/vue-query'
import { BasicLayout } from '@vben/layouts'
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()
const identity = currentIdentity()
const globalSearch = ref('')
const workbench = useQuery({ queryKey: ['admin-workbench'], queryFn: loadAdminWorkbench })
const projects = useQuery({
  queryKey: ['admin-client-project-directory'],
  queryFn: loadAdminClientProjectDirectory,
})

const pendingCount = computed(() => workbench.data.value?.priorityCount ?? null)
const currentProjectId = computed(() => String(route.params.id ?? route.query.projectId ?? ''))
const currentProject = computed(() =>
  projects.data.value?.projects.find((item) => item.id === currentProjectId.value),
)
const projectContextLabel = computed(() => currentProject.value?.projectName ?? '全部项目')

function logout() {
  endSession()
}

function openProject({ key }: { key: string | number }) {
  const projectId = String(key)
  if (projectId === 'all') {
    router.push('/projects')
    return
  }
  router.push(`/projects/${projectId}`)
}

function search() {
  const keyword = globalSearch.value.trim()
  if (!keyword) return
  router.push({ path: '/work-orders', query: { q: keyword } })
}
</script>

<template>
  <BasicLayout class="serviceos-vben-layout" @click-logo="router.push('/workbench')">
    <template #logo-text>
      <span class="serviceos-logo-text">ServiceOS</span>
    </template>

    <template #header-left-10>
      <Dropdown :trigger="['click']">
        <Button class="project-switcher">
          <span>项目：{{ projectContextLabel }}</span>
          <DownOutlined />
        </Button>
        <template #overlay>
          <Menu :selected-keys="currentProjectId ? [currentProjectId] : ['all']" @click="openProject">
            <Menu.Item key="all">全部项目</Menu.Item>
            <Menu.Divider />
            <Menu.Item v-for="project in projects.data.value?.projects ?? []" :key="project.id">
              {{ project.projectName }}
            </Menu.Item>
          </Menu>
        </template>
      </Dropdown>
    </template>

    <template #header-left-20>
      <Input
        v-model:value="globalSearch"
        class="global-search"
        aria-label="全局搜索"
        placeholder="搜索工单、客户、项目、师傅"
        allow-clear
        @press-enter="search"
      >
        <template #prefix><SearchOutlined /></template>
      </Input>
    </template>

    <template #header-right-10>
      <Button class="header-action" type="text" aria-label="通知">
        <BellOutlined />
        <span>通知</span>
      </Button>
    </template>

    <template #header-right-20>
      <Badge :count="pendingCount ?? 0" :offset="[-2, 3]" :overflow-count="99">
        <Button class="header-action" type="text" @click="router.push('/workbench')">
          <CheckSquareOutlined />
          <span>待办</span>
        </Button>
      </Badge>
    </template>

    <template #header-right-30>
      <Button class="header-action" type="text">
        <QuestionCircleOutlined />
        <span>帮助</span>
      </Button>
    </template>

    <template #user-dropdown>
      <Dropdown :trigger="['click']" placement="bottomRight">
        <Button class="serviceos-user-button" type="text">
          <Avatar :size="28">{{ identity.initials }}</Avatar>
          <span>{{ identity.displayName }}</span>
          <DownOutlined />
        </Button>
        <template #overlay>
          <Menu>
            <Menu.Item key="identity" disabled>{{ identity.displayName }}</Menu.Item>
            <Menu.Divider />
            <Menu.Item key="logout" @click="logout">退出登录</Menu.Item>
          </Menu>
        </template>
      </Dropdown>
    </template>
  </BasicLayout>
</template>
