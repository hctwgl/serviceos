<script setup lang="ts">
import type { AdminClientDirectoryItem } from '@serviceos/api-client'
import { computed, reactive, ref } from 'vue'
import { Button, Drawer, Form, Input, SearchOutlined } from '@serviceos/design-system'
import PageError from '../../../components/PageError.vue'
import StatusPill from '../../../components/StatusPill.vue'
import { useCreateBrandCommand, useCreateClientCommand } from '../commands/use-client-catalog-commands'
import { useClientProjectDirectoryQuery } from '../queries/use-client-project-directory-query'

const directory = useClientProjectDirectoryQuery()
const keyword = ref('')
const clientOpen = ref(false)
const brandOpen = ref(false)
const selectedClient = ref<AdminClientDirectoryItem | null>(null)
const createClientCommand = useCreateClientCommand()
const createBrandCommand = useCreateBrandCommand()
const clientForm = reactive({ clientCode: '', displayName: '' })
const brandForm = reactive({ brandCode: '', displayName: '', sortOrder: 0 })
const clients = computed(() => {
  const normalized = keyword.value.trim().toLowerCase()
  const items = directory.data.value?.clients ?? []
  if (!normalized) return items
  return items.filter((item) => `${item.clientName} ${item.brands.map((brand) => brand.brandName).join(' ')}`.toLowerCase().includes(normalized))
})
const canCreateClient = computed(() => directory.data.value?.allowedActions.includes('CREATE_CLIENT') ?? false)
const canCreateBrand = computed(() => directory.data.value?.allowedActions.includes('CREATE_BRAND') ?? false)
const normalizedClientCode = computed(() => clientForm.clientCode.trim().toUpperCase())
const normalizedBrandCode = computed(() => brandForm.brandCode.trim().toUpperCase())
const clientCodeExists = computed(() => directory.data.value?.clients.some(
  (item) => item.clientCode.toUpperCase() === normalizedClientCode.value,
) ?? false)
const brandCodeExists = computed(() => selectedClient.value?.brands.some(
  (item) => item.brandCode.toUpperCase() === normalizedBrandCode.value,
) ?? false)
const clientFormValid = computed(() => Boolean(
  normalizedClientCode.value
  && /^[A-Z0-9][A-Z0-9_-]*$/.test(normalizedClientCode.value)
  && clientForm.displayName.trim()
  && !clientCodeExists.value,
))
const brandFormValid = computed(() => Boolean(
  selectedClient.value
  && normalizedBrandCode.value
  && /^[A-Z0-9][A-Z0-9_-]*$/.test(normalizedBrandCode.value)
  && brandForm.displayName.trim()
  && !brandCodeExists.value,
))

function openCreateClient() {
  Object.assign(clientForm, { clientCode: '', displayName: '' })
  createClientCommand.reset()
  clientOpen.value = true
}

function openCreateBrand(client: AdminClientDirectoryItem) {
  selectedClient.value = client
  Object.assign(brandForm, { brandCode: '', displayName: '', sortOrder: client.brands.length * 10 })
  createBrandCommand.reset()
  brandOpen.value = true
}

async function submitClient() {
  if (!clientFormValid.value) return
  await createClientCommand.mutateAsync({
    clientCode: normalizedClientCode.value,
    displayName: clientForm.displayName.trim(),
  })
  clientOpen.value = false
}

async function submitBrand() {
  if (!brandFormValid.value || !selectedClient.value) return
  await createBrandCommand.mutateAsync({
    clientCode: selectedClient.value.clientCode,
    brandCode: normalizedBrandCode.value,
    displayName: brandForm.displayName.trim(),
    sortOrder: brandForm.sortOrder,
  })
  brandOpen.value = false
}
</script>

<template>
  <div class="resource-page">
    <div class="page-heading inline">
      <div><p class="breadcrumb">客户与项目 / 客户品牌</p><h1>客户品牌</h1><p>管理租户内合作车企及其品牌目录，项目通过稳定客户编码建立业务归属。</p></div>
      <div class="heading-actions"><Button v-if="canCreateClient" type="primary" @click="openCreateClient">新增合作客户</Button></div>
    </div>
    <PageError v-if="directory.isError.value" :detail="directory.error.value?.message ?? '客户品牌目录加载失败'" />
    <section v-else class="directory-panel">
      <div class="resource-filter"><Input v-model:value="keyword" placeholder="搜索客户或品牌名称" allow-clear><template #prefix><SearchOutlined /></template></Input></div>
      <div class="client-card-grid">
        <article v-for="client in clients" :key="client.clientCode" class="client-card">
          <header><div class="client-logo">{{ client.clientName.slice(0, 1) }}</div><div><h3>{{ client.clientName }}</h3><p>合作客户</p></div><StatusPill :tone="client.status === 'ACTIVE' ? 'green' : 'gray'" :label="client.status === 'ACTIVE' ? '合作中' : '已停用'" /></header>
          <div class="client-project-count"><strong>{{ client.projectCount }}</strong><span>个项目</span></div>
          <footer class="client-brand-list">
            <span v-for="brand in client.brands" :key="brand.brandCode" :class="{ disabled: brand.status === 'DISABLED' }">{{ brand.brandName }}</span>
            <em v-if="!client.brands.length">尚未登记品牌</em>
            <Button v-if="canCreateBrand && client.status === 'ACTIVE'" type="link" size="small" @click="openCreateBrand(client)">新增品牌</Button>
          </footer>
        </article>
      </div>
      <div v-if="directory.isLoading.value" class="table-loading">正在加载客户品牌…</div>
      <div v-else-if="!clients.length" class="empty-state"><h3>暂无符合条件的客户</h3><p>请调整搜索条件。</p></div>
    </section>

    <Drawer :open="clientOpen" width="520" title="新增合作客户" @close="clientOpen = false">
      <div class="create-project-intro">客户编码是项目归属和外部协同使用的稳定业务标识，创建后不应随展示名称变化。</div>
      <Form layout="vertical" @submit.prevent="submitClient">
        <Form.Item label="客户名称" required><Input v-model:value="clientForm.displayName" :maxlength="200" placeholder="例如：比亚迪汽车" /></Form.Item>
        <Form.Item label="客户编码" required>
          <Input v-model:value="clientForm.clientCode" :maxlength="128" placeholder="例如：BYD" />
          <small>仅使用大写英文字母、数字、短横线和下划线。</small>
          <small v-if="clientCodeExists" class="field-error">该客户编码已经存在，请使用新的稳定编码。</small>
        </Form.Item>
        <PageError v-if="createClientCommand.isError.value" :detail="createClientCommand.error.value?.message ?? '合作客户创建失败'" />
        <div class="drawer-actions"><Button @click="clientOpen = false">取消</Button><Button type="primary" html-type="submit" :loading="createClientCommand.isPending.value" :disabled="!clientFormValid">创建客户</Button></div>
      </Form>
    </Drawer>

    <Drawer :open="brandOpen" width="520" :title="`为${selectedClient?.clientName ?? ''}新增品牌`" @close="brandOpen = false">
      <div class="create-project-intro">品牌属于当前合作客户，可供项目、服务产品和外部订单映射使用。</div>
      <Form layout="vertical" @submit.prevent="submitBrand">
        <Form.Item label="品牌名称" required><Input v-model:value="brandForm.displayName" :maxlength="200" placeholder="例如：方程豹" /></Form.Item>
        <Form.Item label="品牌编码" required>
          <Input v-model:value="brandForm.brandCode" :maxlength="128" placeholder="例如：FANGCHENGBAO" />
          <small>使用客户范围内唯一的稳定编码。</small>
          <small v-if="brandCodeExists" class="field-error">该品牌编码已在当前客户下登记。</small>
        </Form.Item>
        <Form.Item label="展示顺序"><Input v-model:value.number="brandForm.sortOrder" type="number" min="0" max="999999" /></Form.Item>
        <PageError v-if="createBrandCommand.isError.value" :detail="createBrandCommand.error.value?.message ?? '品牌创建失败'" />
        <div class="drawer-actions"><Button @click="brandOpen = false">取消</Button><Button type="primary" html-type="submit" :loading="createBrandCommand.isPending.value" :disabled="!brandFormValid">创建品牌</Button></div>
      </Form>
    </Drawer>
  </div>
</template>
