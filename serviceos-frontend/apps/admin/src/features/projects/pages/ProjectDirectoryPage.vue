<script setup lang="ts">
import type { CreateProjectInput } from '@serviceos/api-client'
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Button, Drawer, Form, Input, SearchOutlined, Select } from '@serviceos/design-system'
import PageError from '../../../components/PageError.vue'
import StatusPill from '../../../components/StatusPill.vue'
import { presentConfigurationStatus, presentProjectPeriod, presentProjectStatus } from '../presenters/client-project-directory'
import { useCreateProjectCommand } from '../commands/use-create-project-command'
import { useClientProjectDirectoryQuery } from '../queries/use-client-project-directory-query'
import { useProjectCreationOptionsQuery } from '../queries/use-project-creation-options-query'

const router = useRouter()
const directory = useClientProjectDirectoryQuery()
const keyword = ref('')
const createOpen = ref(false)
const regionKeyword = ref('')
const creationOptions = useProjectCreationOptionsQuery(createOpen, regionKeyword)
const createCommand = useCreateProjectCommand()
const createForm = reactive<Omit<CreateProjectInput, 'endsOn'> & { endsOn: string }>({
  code: '',
  clientId: '',
  name: '',
  startsOn: '',
  endsOn: '',
  regionCodes: [],
  networkIds: [],
})
const projects = computed(() => {
  const normalized = keyword.value.trim().toLowerCase()
  const items = directory.data.value?.projects ?? []
  if (!normalized) return items
  return items.filter((item) => `${item.projectName} ${item.clientName ?? ''} ${item.regionNames.join(' ')}`.toLowerCase().includes(normalized))
})
const activeCount = computed(() => directory.data.value?.projects.filter((item) => item.status === 'ACTIVE').length ?? 0)
const draftCount = computed(() => directory.data.value?.projects.filter((item) => item.configurationStatus === 'DRAFT' || item.configurationStatus === 'UNPUBLISHED_CHANGES').length ?? 0)

const clientOptions = computed(() => (creationOptions.data.value?.clients ?? []).map((item) => ({
  value: item.code,
  label: item.name,
})))
const regionOptions = computed(() => (creationOptions.data.value?.regions ?? []).map((item) => ({
  value: item.code,
  label: `${item.name} · ${regionLevelLabel(item.level)}`,
})))
const networkOptions = computed(() => (creationOptions.data.value?.networks ?? []).map((item) => ({
  value: item.id,
  label: `${item.name} · ${item.code}`,
})))
const canCreate = computed(() => creationOptions.data.value?.allowedActions.includes('CREATE_PROJECT') ?? false)
const formValid = computed(() => Boolean(
  createForm.code.trim()
  && /^[A-Za-z0-9][A-Za-z0-9_-]*$/.test(createForm.code.trim())
  && createForm.clientId
  && createForm.name.trim()
  && createForm.startsOn
  && (!createForm.endsOn || createForm.endsOn >= createForm.startsOn),
))

function regionLevelLabel(level: string) {
  if (level === 'PROVINCE') return '省级'
  if (level === 'CITY') return '市级'
  return '区县'
}

function openCreate() {
  Object.assign(createForm, {
    code: '',
    clientId: '',
    name: '',
    startsOn: '',
    endsOn: '',
    regionCodes: [],
    networkIds: [],
  })
  regionKeyword.value = ''
  createCommand.reset()
  createOpen.value = true
}

function closeCreate() {
  createOpen.value = false
  regionKeyword.value = ''
}

async function submitCreate() {
  if (!formValid.value) return
  const result = await createCommand.mutateAsync({
    ...createForm,
    code: createForm.code.trim(),
    name: createForm.name.trim(),
    endsOn: createForm.endsOn || null,
  })
  closeCreate()
  await router.push(`/projects/${result.id}`)
}
</script>

<template>
  <div class="resource-page">
    <div class="page-heading inline">
      <div><p class="breadcrumb">客户与项目 / 项目管理</p><h1>项目管理</h1><p>查看项目服务周期、区域范围、参与网点及履约配置准备情况。</p></div>
      <div class="heading-actions"><Button type="primary" @click="openCreate">新建项目</Button></div>
    </div>
    <PageError v-if="directory.isError.value" :detail="directory.error.value?.message ?? '项目目录加载失败'" />
    <template v-else>
      <section class="resource-summary-grid project-summary"><div><span>全部项目</span><strong>{{ directory.data.value?.projects.length ?? 0 }}</strong></div><div><span>运行中</span><strong>{{ activeCount }}</strong></div><div><span>存在草稿</span><strong>{{ draftCount }}</strong></div><div><span>合作客户</span><strong>{{ directory.data.value?.clients.filter((item) => item.status === 'ACTIVE').length ?? 0 }}</strong></div></section>
      <section class="directory-panel">
        <div class="resource-filter"><Input v-model:value="keyword" placeholder="搜索项目、客户或服务区域" allow-clear><template #prefix><SearchOutlined /></template></Input></div>
        <div class="data-table-wrap">
          <table class="business-table project-table">
            <thead><tr><th>项目</th><th>客户</th><th>服务周期</th><th>服务区域</th><th>参与网点</th><th>履约配置</th><th>项目状态</th></tr></thead>
            <tbody><tr v-for="item in projects" :key="item.id" :class="{ 'data-incomplete-row': !item.dataComplete }"><td><RouterLink class="table-link" :to="`/projects/${item.id}`"><strong>{{ item.projectName }}</strong></RouterLink><small>{{ item.projectCode }}</small><em v-if="!item.dataComplete">{{ item.dataProblem }}</em></td><td>{{ item.clientName ?? '数据不完整' }}</td><td>{{ presentProjectPeriod(item.startsOn, item.endsOn) }}</td><td>{{ item.regionNames.join('、') || '数据不完整' }}</td><td>{{ item.networkCount }} 个</td><td><StatusPill :tone="presentConfigurationStatus(item.configurationStatus).tone" :label="presentConfigurationStatus(item.configurationStatus).label" /></td><td><StatusPill :tone="presentProjectStatus(item.status).tone" :label="presentProjectStatus(item.status).label" /></td></tr></tbody>
          </table>
          <div v-if="directory.isLoading.value" class="table-loading">正在加载项目…</div>
          <div v-else-if="!projects.length" class="empty-state"><h3>暂无符合条件的项目</h3><p>请调整搜索条件。</p></div>
        </div>
      </section>
    </template>

    <Drawer :open="createOpen" width="620" title="新建项目" placement="right" @close="closeCreate">
      <div class="create-project-intro">
        新项目以草稿状态创建。完成项目团队、区域分工和履约配置并校验后，才能投入正式接单。
      </div>
      <PageError
        v-if="creationOptions.isError.value"
        :detail="creationOptions.error.value?.message ?? '新建项目选项加载失败'"
      />
      <div v-else-if="creationOptions.isLoading.value" class="page-loading">正在加载客户、行政区域和服务网点…</div>
      <template v-else-if="creationOptions.data.value">
        <div v-if="!canCreate" class="permission-state">
          <h3>当前账号不能创建项目</h3><p>你可以继续查看项目目录，但没有项目创建权限。</p>
        </div>
        <Form v-else layout="vertical" @submit.prevent="submitCreate">
          <div class="create-project-form-grid">
            <Form.Item label="项目名称" required class="full-row">
              <Input v-model:value="createForm.name" :maxlength="200" placeholder="例如：比亚迪山东家充安装服务项目" />
            </Form.Item>
            <Form.Item label="项目编码" required>
              <Input v-model:value="createForm.code" :maxlength="64" placeholder="例如：BYD-SD-HC-2026" />
              <small>仅使用英文字母、数字、短横线和下划线，创建后作为稳定业务编码。</small>
            </Form.Item>
            <Form.Item label="合作客户" required>
              <Select
                v-model:value="createForm.clientId"
                show-search
                option-filter-prop="label"
                :options="clientOptions"
                placeholder="选择已登记客户"
              />
            </Form.Item>
            <Form.Item label="服务开始日期" required>
              <Input v-model:value="createForm.startsOn" type="date" />
            </Form.Item>
            <Form.Item label="服务结束日期">
              <Input v-model:value="createForm.endsOn" type="date" />
            </Form.Item>
            <Form.Item label="服务行政区域" class="full-row">
              <Select
                v-model:value="createForm.regionCodes"
                mode="multiple"
                show-search
                :filter-option="false"
                :options="regionOptions"
                :not-found-content="creationOptions.isFetching.value ? '正在查询行政区域…' : '未找到匹配的标准行政区域'"
                placeholder="选择项目允许服务的行政区域"
                @search="regionKeyword = $event"
              />
            </Form.Item>
            <Form.Item label="参与服务网点" class="full-row">
              <Select
                v-model:value="createForm.networkIds"
                mode="multiple"
                show-search
                option-filter-prop="label"
                :options="networkOptions"
                placeholder="选择允许参与项目的服务网点"
              />
            </Form.Item>
          </div>
          <PageError
            v-if="createCommand.isError.value"
            :detail="createCommand.error.value?.message ?? '项目创建失败'"
          />
          <div class="project-create-summary">
            <strong>创建后下一步</strong>
            <span>维护项目团队与区域分工 → 建立履约方案 → 校验并发布 → 启用项目接单</span>
          </div>
          <div class="drawer-actions">
            <Button @click="closeCreate">取消</Button>
            <Button type="primary" html-type="submit" :loading="createCommand.isPending.value" :disabled="!formValid">创建项目</Button>
          </div>
        </Form>
      </template>
    </Drawer>
  </div>
</template>
