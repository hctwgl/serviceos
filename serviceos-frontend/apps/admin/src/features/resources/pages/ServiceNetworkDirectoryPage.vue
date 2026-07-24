<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { Button, Drawer, Form, Input, SearchOutlined, Select } from '@serviceos/design-system'
import PageError from '../../../components/PageError.vue'
import StatusPill from '../../../components/StatusPill.vue'
import { formatDateTime } from '../../../presenters/work-order'
import { useCreateNetworkCommand, useCreatePartnerCommand } from '../commands/use-network-commands'
import { presentRegions } from '../presenters/resource-directory'
import { useResourceDirectoryQuery } from '../queries/use-resource-directory-query'

const directory = useResourceDirectoryQuery()
const keyword = ref('')
const partnerOpen = ref(false)
const networkOpen = ref(false)
const createPartnerCommand = useCreatePartnerCommand()
const createNetworkCommand = useCreateNetworkCommand()
const partnerForm = reactive({ code: '', name: '' })
const networkForm = reactive({ partnerOrganizationId: '', networkCode: '', networkName: '' })
const items = computed(() => {
  const normalized = keyword.value.trim().toLowerCase()
  const networks = directory.data.value?.networks ?? []
  if (!normalized) return networks
  return networks.filter((item) => `${item.networkName} ${item.networkCode} ${item.partnerOrganizationName}`.toLowerCase().includes(normalized))
})
const canCreatePartner = computed(() => directory.data.value?.allowedActions.includes('CREATE_PARTNER') ?? false)
const canCreateNetwork = computed(() => directory.data.value?.allowedActions.includes('CREATE_NETWORK') ?? false)
const activePartners = computed(() => directory.data.value?.partners.filter((item) => item.status === 'ACTIVE') ?? [])
const partnerOptions = computed(() => activePartners.value.map((item) => ({
  value: item.id,
  label: `${item.partnerName} · ${item.partnerCode}`,
})))
const normalizedPartnerCode = computed(() => partnerForm.code.trim().toUpperCase())
const normalizedNetworkCode = computed(() => networkForm.networkCode.trim().toUpperCase())
const partnerCodeExists = computed(() => directory.data.value?.partners.some(
  (item) => item.partnerCode.toUpperCase() === normalizedPartnerCode.value,
) ?? false)
const networkCodeExists = computed(() => directory.data.value?.networks.some(
  (item) => item.networkCode.toUpperCase() === normalizedNetworkCode.value,
) ?? false)
const partnerFormValid = computed(() => Boolean(
  normalizedPartnerCode.value
  && /^[A-Z0-9][A-Z0-9_-]*$/.test(normalizedPartnerCode.value)
  && partnerForm.name.trim()
  && !partnerCodeExists.value,
))
const networkFormValid = computed(() => Boolean(
  networkForm.partnerOrganizationId
  && normalizedNetworkCode.value
  && /^[A-Z0-9][A-Z0-9_-]*$/.test(normalizedNetworkCode.value)
  && networkForm.networkName.trim()
  && !networkCodeExists.value,
))

function openCreatePartner() {
  Object.assign(partnerForm, { code: '', name: '' })
  createPartnerCommand.reset()
  partnerOpen.value = true
}

function openCreateNetwork(partnerOrganizationId = '') {
  Object.assign(networkForm, { partnerOrganizationId, networkCode: '', networkName: '' })
  createNetworkCommand.reset()
  networkOpen.value = true
}

async function submitPartner() {
  if (!partnerFormValid.value) return
  const partner = await createPartnerCommand.mutateAsync({
    code: normalizedPartnerCode.value,
    name: partnerForm.name.trim(),
  })
  partnerOpen.value = false
  openCreateNetwork(partner.id)
}

async function submitNetwork() {
  if (!networkFormValid.value) return
  await createNetworkCommand.mutateAsync({
    partnerOrganizationId: networkForm.partnerOrganizationId,
    networkCode: normalizedNetworkCode.value,
    networkName: networkForm.networkName.trim(),
  })
  networkOpen.value = false
}
</script>

<template>
  <div class="resource-page">
    <div class="page-heading inline">
      <h1>服务网点</h1>
      <div class="heading-actions"><Button v-if="canCreatePartner" @click="openCreatePartner">新增合作服务商</Button><Button v-if="canCreateNetwork" type="primary" @click="openCreateNetwork()">新建服务网点</Button></div>
    </div>
    <PageError v-if="directory.isError.value" :detail="directory.error.value?.message ?? '服务网点目录加载失败'" />
    <template v-else>
      <section class="resource-summary-grid">
        <div><span>合作服务商</span><strong>{{ directory.data.value?.partners.filter((item) => item.status === 'ACTIVE').length ?? 0 }}</strong></div>
        <div><span>服务网点</span><strong>{{ directory.data.value?.networks.length ?? 0 }}</strong></div>
        <div><span>运行中</span><strong>{{ directory.data.value?.networks.filter((item) => item.status === 'ACTIVE').length ?? 0 }}</strong></div>
        <div><span>在册师傅</span><strong>{{ directory.data.value?.technicians.filter((item) => item.status === 'ACTIVE').length ?? 0 }}</strong></div>
      </section>
      <section class="directory-panel">
        <div class="resource-filter"><Input v-model:value="keyword" placeholder="搜索网点名称、编码或合作公司" allow-clear><template #prefix><SearchOutlined /></template></Input></div>
        <div class="network-card-grid">
          <article v-for="network in items" :key="network.id" class="network-card">
            <header><div class="network-mark">网</div><div><h3>{{ network.networkName }}</h3><p>{{ network.partnerOrganizationName }}</p></div><StatusPill :tone="network.status === 'ACTIVE' ? 'green' : 'gray'" :label="network.status === 'ACTIVE' ? '运行中' : '已清退'" /></header>
            <dl><div><dt>网点编码</dt><dd>{{ network.networkCode }}</dd></div><div><dt>在册师傅</dt><dd>{{ network.activeTechnicianCount }} 人</dd></div><div><dt>更新时间</dt><dd>{{ formatDateTime(network.updatedAt) }}</dd></div></dl>
            <footer><span v-for="region in presentRegions(network.regionCodes)" :key="region">{{ region }}</span><em v-if="!network.regionCodes.length">尚未配置服务区域</em></footer>
          </article>
        </div>
        <div v-if="directory.isLoading.value" class="table-loading">正在加载服务网点…</div>
        <div v-else-if="!items.length" class="empty-state"><h3>暂无符合条件的服务网点</h3><p>请调整搜索条件或完善正式网点数据。</p></div>
      </section>
    </template>

    <Drawer :open="partnerOpen" width="520" title="新增合作服务商" @close="partnerOpen = false">
      <div class="create-project-intro">合作服务商是网点的法人或经营主体，不进入 ServiceOS 内部组织架构。创建完成后继续建立其首个服务网点。</div>
      <Form layout="vertical" @submit.prevent="submitPartner">
        <Form.Item label="服务商名称" required><Input v-model:value="partnerForm.name" :maxlength="200" placeholder="例如：山东诚维新能源服务有限公司" /></Form.Item>
        <Form.Item label="服务商编码" required>
          <Input v-model:value="partnerForm.code" :maxlength="64" placeholder="例如：SD-CHENGWEI" />
          <small>仅使用大写英文字母、数字、短横线和下划线。</small>
          <small v-if="partnerCodeExists" class="field-error">该服务商编码已经存在。</small>
        </Form.Item>
        <PageError v-if="createPartnerCommand.isError.value" :detail="createPartnerCommand.error.value?.message ?? '合作服务商创建失败'" />
        <div class="drawer-actions"><Button @click="partnerOpen = false">取消</Button><Button type="primary" html-type="submit" :loading="createPartnerCommand.isPending.value" :disabled="!partnerFormValid">创建并继续</Button></div>
      </Form>
    </Drawer>

    <Drawer :open="networkOpen" width="560" title="新建服务网点" @close="networkOpen = false">
      <div class="create-project-intro">网点负责接单、调度师傅和履约协作。创建后还需要配置服务覆盖区域、品牌和业务类型，才能进入派单候选。</div>
      <Form layout="vertical" @submit.prevent="submitNetwork">
        <Form.Item label="所属合作服务商" required><Select v-model:value="networkForm.partnerOrganizationId" show-search option-filter-prop="label" :options="partnerOptions" placeholder="选择合作服务商" /></Form.Item>
        <Form.Item label="网点名称" required><Input v-model:value="networkForm.networkName" :maxlength="200" placeholder="例如：济南高新服务中心" /></Form.Item>
        <Form.Item label="网点编码" required>
          <Input v-model:value="networkForm.networkCode" :maxlength="64" placeholder="例如：JINAN-GAOXIN" />
          <small>编码在租户内唯一，创建后作为派单和外部协同的稳定标识。</small>
          <small v-if="networkCodeExists" class="field-error">该网点编码已经存在。</small>
        </Form.Item>
        <PageError v-if="createNetworkCommand.isError.value" :detail="createNetworkCommand.error.value?.message ?? '服务网点创建失败'" />
        <div class="drawer-actions"><Button @click="networkOpen = false">取消</Button><Button type="primary" html-type="submit" :loading="createNetworkCommand.isPending.value" :disabled="!networkFormValid">创建网点</Button></div>
      </Form>
    </Drawer>
  </div>
</template>
