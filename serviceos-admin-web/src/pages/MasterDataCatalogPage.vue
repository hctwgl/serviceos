<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  Alert,
  Button,
  Input,
  InputNumber,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Tree,
} from 'ant-design-vue'
import type { DataNode, EventDataNode } from 'ant-design-vue/es/tree'
import ListPageLayout from '../patterns/templates/ListPageLayout.vue'
import SummaryStrip, { type SummaryStripItem } from '../patterns/SummaryStrip.vue'
import {
  listProjectClientBrands,
  listProjectClients,
  listRegionCatalog,
  registerProjectClient,
  registerProjectClientBrand,
  setProjectClientBrandStatus,
  setProjectClientStatus,
  type ProjectClientBrandItem,
  type ProjectClientDirectoryItem,
  type RegionCatalogItem,
} from '../api/projectCatalog'
import { hasCapability, loadActiveCapabilityCodes } from '../api/capabilitiesGate'
import { safeAccessDeniedMessage } from '../api/client'

function catalogStatusLabel(status: string): string {
  if (status === 'ACTIVE') return '启用'
  if (status === 'DISABLED') return '停用'
  return `未知状态（${status}）`
}

const activeTab = ref('clients')
const capabilityCodes = ref<string[]>([])
const canWrite = computed(() => hasCapability(capabilityCodes.value, 'project.create'))

const clientLoading = ref(false)
const clientBusy = ref(false)
const clientError = ref<string | null>(null)
const clientStatusFilter = ref<'ALL' | 'ACTIVE' | 'DISABLED'>('ALL')
const clients = ref<ProjectClientDirectoryItem[]>([])
const selectedClientCode = ref<string | null>(null)
const createClientCode = ref('')
const createClientName = ref('')

const brandLoading = ref(false)
const brandBusy = ref(false)
const brandError = ref<string | null>(null)
const brands = ref<ProjectClientBrandItem[]>([])
const createBrandCode = ref('')
const createBrandName = ref('')
const createBrandSort = ref(0)

const regionLoading = ref(false)
const regionError = ref<string | null>(null)
const regionQuery = ref('')
const treeData = ref<DataNode[]>([])
const expandedKeys = ref<string[]>([])
const loadedKeys = ref<string[]>([])

const clientSummary = computed<SummaryStripItem[]>(() => {
  const items = clients.value
  const active = items.filter((item) => item.status === 'ACTIVE').length
  const disabled = items.filter((item) => item.status === 'DISABLED').length
  return [
    { key: 'total', label: '车企', value: String(items.length) },
    { key: 'active', label: '启用', value: String(active), tone: 'success' },
    { key: 'disabled', label: '停用', value: String(disabled), tone: disabled ? 'warning' : 'default' },
    {
      key: 'brands',
      label: '当前品牌',
      value: String(brands.value.length),
      tone: 'info',
    },
  ]
})

const clientColumns = [
  { title: '车企编码', dataIndex: 'clientCode', key: 'clientCode' },
  { title: '显示名', dataIndex: 'displayName', key: 'displayName' },
  { title: '状态', key: 'status' },
  { title: '操作', key: 'actions', width: 220 },
]

const brandColumns = [
  { title: '品牌编码', dataIndex: 'brandCode', key: 'brandCode' },
  { title: '显示名', dataIndex: 'displayName', key: 'displayName' },
  { title: '排序', dataIndex: 'sortOrder', key: 'sortOrder', width: 80 },
  { title: '状态', key: 'status', width: 100 },
  { title: '操作', key: 'actions', width: 160 },
]

const selectedClient = computed(
  () => clients.value.find((item) => item.clientCode === selectedClientCode.value) ?? null,
)

function toTreeNode(item: RegionCatalogItem): DataNode {
  return {
    key: item.regionCode,
    title: `${item.regionName}（${item.regionCode}）· ${item.regionLevel}`,
    isLeaf: item.childCount <= 0,
    children: item.childCount > 0 ? [] : undefined,
  }
}

async function loadClients() {
  clientLoading.value = true
  clientError.value = null
  try {
    const page = await listProjectClients(clientStatusFilter.value)
    clients.value = page.items
    if (
      selectedClientCode.value &&
      !clients.value.some((item) => item.clientCode === selectedClientCode.value)
    ) {
      selectedClientCode.value = null
      brands.value = []
    }
  } catch (err) {
    clients.value = []
    clientError.value = safeAccessDeniedMessage(err)
  } finally {
    clientLoading.value = false
  }
}

async function loadBrands() {
  if (!selectedClientCode.value) {
    brands.value = []
    return
  }
  brandLoading.value = true
  brandError.value = null
  try {
    const page = await listProjectClientBrands(selectedClientCode.value, 'ALL')
    brands.value = page.items
  } catch (err) {
    brands.value = []
    brandError.value = safeAccessDeniedMessage(err)
  } finally {
    brandLoading.value = false
  }
}

async function submitCreateClient() {
  if (!canWrite.value) return
  clientBusy.value = true
  clientError.value = null
  try {
    const created = await registerProjectClient({
      clientCode: createClientCode.value.trim(),
      displayName: createClientName.value.trim(),
    })
    createClientCode.value = ''
    createClientName.value = ''
    selectedClientCode.value = created.data.clientCode
    await loadClients()
    await loadBrands()
  } catch (err) {
    clientError.value = safeAccessDeniedMessage(err)
  } finally {
    clientBusy.value = false
  }
}

async function toggleClientStatus(item: ProjectClientDirectoryItem) {
  if (!canWrite.value) return
  clientBusy.value = true
  clientError.value = null
  try {
    await setProjectClientStatus(item.clientCode, item.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE')
    await loadClients()
  } catch (err) {
    clientError.value = safeAccessDeniedMessage(err)
  } finally {
    clientBusy.value = false
  }
}

async function submitCreateBrand() {
  if (!canWrite.value || !selectedClientCode.value) return
  brandBusy.value = true
  brandError.value = null
  try {
    await registerProjectClientBrand(selectedClientCode.value, {
      brandCode: createBrandCode.value.trim(),
      displayName: createBrandName.value.trim(),
      sortOrder: createBrandSort.value || 0,
    })
    createBrandCode.value = ''
    createBrandName.value = ''
    createBrandSort.value = 0
    await loadBrands()
  } catch (err) {
    brandError.value = safeAccessDeniedMessage(err)
  } finally {
    brandBusy.value = false
  }
}

async function toggleBrandStatus(item: ProjectClientBrandItem) {
  if (!canWrite.value) return
  brandBusy.value = true
  brandError.value = null
  try {
    await setProjectClientBrandStatus(
      item.clientCode,
      item.brandCode,
      item.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE',
    )
    await loadBrands()
  } catch (err) {
    brandError.value = safeAccessDeniedMessage(err)
  } finally {
    brandBusy.value = false
  }
}

async function loadRegionRoots() {
  regionLoading.value = true
  regionError.value = null
  try {
    const page = regionQuery.value.trim()
      ? await listRegionCatalog({ query: regionQuery.value.trim(), parentCode: '*', limit: 200 })
      : await listRegionCatalog({ parentCode: undefined, level: 'PROVINCE', limit: 100 })
    treeData.value = page.items.map(toTreeNode)
    expandedKeys.value = []
    loadedKeys.value = []
  } catch (err) {
    treeData.value = []
    regionError.value = safeAccessDeniedMessage(err)
  } finally {
    regionLoading.value = false
  }
}

async function onRegionLoadData(treeNode: EventDataNode) {
  const key = String(treeNode.key)
  if (loadedKeys.value.includes(key)) return
  const page = await listRegionCatalog({ parentCode: key, limit: 200 })
  const children = page.items.map(toTreeNode)
  const attach = (nodes: DataNode[]): DataNode[] =>
    nodes.map((node) => {
      if (String(node.key) === key) {
        return { ...node, children, isLeaf: children.length === 0 }
      }
      if (node.children?.length) {
        return { ...node, children: attach(node.children) }
      }
      return node
    })
  treeData.value = attach(treeData.value)
  loadedKeys.value = [...loadedKeys.value, key]
}

function selectClient(code: string) {
  selectedClientCode.value = code
}

watch(selectedClientCode, () => {
  void loadBrands()
})

watch(clientStatusFilter, () => {
  void loadClients()
})

onMounted(async () => {
  capabilityCodes.value = await loadActiveCapabilityCodes()
  await loadClients()
  await loadRegionRoots()
})
</script>

<template>
  <div data-page-id="ADMIN.MASTERDATA.CATALOG" data-testid="master-data-catalog-page">
    <ListPageLayout
      title="主数据治理"
      description="维护租户车企/品牌生命周期，并按层级浏览国标行政区名称目录。选择器默认仍只消费 ACTIVE 车企。"
      :loading="clientLoading || regionLoading"
      :count-label="activeTab === 'clients' ? `车企 ${clients.length}` : `行政区节点 ${treeData.length}`"
    >
      <template #feedback>
        <Alert
          v-if="!canWrite"
          type="info"
          show-icon
          message="当前上下文缺少 project.create，仅可浏览主数据；登记与启停需后端授权。"
        />
        <Alert
          type="warning"
          show-icon
          message="诚实边界：本页提供省级全量骨架与演示地市/区县展开，不是全国区县全量；暂无拼音索引与多级子品牌树。"
        />
      </template>

      <template #filters>
        <Tabs v-model:active-key="activeTab" data-testid="master-data-tabs">
          <Tabs.TabPane key="clients" tab="车企与品牌" />
          <Tabs.TabPane key="regions" tab="行政区树" />
        </Tabs>
      </template>

      <div v-if="activeTab === 'clients'" class="clients-panel">
        <SummaryStrip :items="clientSummary" />
        <Space wrap class="filter-row">
          <Select
            v-model:value="clientStatusFilter"
            style="width: 160px"
            aria-label="车企状态筛选"
            data-testid="client-status-filter"
            :options="[
              { value: 'ALL', label: '全部状态' },
              { value: 'ACTIVE', label: '仅启用' },
              { value: 'DISABLED', label: '仅停用' },
            ]"
          />
          <Button data-testid="client-reload" @click="loadClients">刷新</Button>
        </Space>
        <Alert v-if="clientError" type="error" show-icon :message="clientError" />

        <section v-if="canWrite" class="create-card" data-testid="client-create-form">
          <h3>登记车企</h3>
          <Space wrap>
            <Input
              v-model:value="createClientCode"
              placeholder="车企编码"
              aria-label="车企编码"
              style="width: 180px"
              data-testid="client-code-input"
            />
            <Input
              v-model:value="createClientName"
              placeholder="显示名"
              aria-label="车企显示名"
              style="width: 220px"
              data-testid="client-name-input"
            />
            <Button
              type="primary"
              :loading="clientBusy"
              data-testid="client-create-submit"
              @click="submitCreateClient"
            >
              登记
            </Button>
          </Space>
        </section>

        <Table
          row-key="clientCode"
          :columns="clientColumns"
          :data-source="clients"
          :loading="clientLoading"
          :pagination="false"
          size="middle"
          data-testid="client-directory-table"
          :row-class-name="(record) => (record.clientCode === selectedClientCode ? 'row-selected' : '')"
          :custom-row="(record) => ({ onClick: () => selectClient(record.clientCode) })"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <Tag :color="record.status === 'ACTIVE' ? 'success' : 'default'">
                {{ catalogStatusLabel(record.status) }}
              </Tag>
            </template>
            <template v-else-if="column.key === 'actions'">
              <Space>
                <Button size="small" type="link" @click.stop="selectClient(record.clientCode)">
                  管理品牌
                </Button>
                <Button
                  v-if="canWrite"
                  size="small"
                  :danger="record.status === 'ACTIVE'"
                  data-testid="client-toggle-status"
                  :disabled="clientBusy"
                  @click.stop="toggleClientStatus(record as ProjectClientDirectoryItem)"
                >
                  {{ record.status === 'ACTIVE' ? '停用' : '启用' }}
                </Button>
              </Space>
            </template>
          </template>
        </Table>

        <section v-if="selectedClient" class="brand-panel" data-testid="brand-panel">
          <h3>
            品牌 · {{ selectedClient.displayName }}
            <Tag>{{ selectedClient.clientCode }}</Tag>
          </h3>
          <Alert v-if="brandError" type="error" show-icon :message="brandError" />
          <section v-if="canWrite" class="create-card" data-testid="brand-create-form">
            <Space wrap>
              <Input
                v-model:value="createBrandCode"
                placeholder="品牌编码"
                aria-label="品牌编码"
                style="width: 160px"
                data-testid="brand-code-input"
              />
              <Input
                v-model:value="createBrandName"
                placeholder="品牌显示名"
                aria-label="品牌显示名"
                style="width: 200px"
                data-testid="brand-name-input"
              />
              <InputNumber
                v-model:value="createBrandSort"
                :min="0"
                :max="999999"
                aria-label="品牌排序"
                data-testid="brand-sort-input"
              />
              <Button
                type="primary"
                :loading="brandBusy"
                data-testid="brand-create-submit"
                @click="submitCreateBrand"
              >
                登记品牌
              </Button>
            </Space>
          </section>
          <Table
            row-key="brandCode"
            :columns="brandColumns"
            :data-source="brands"
            :loading="brandLoading"
            :pagination="false"
            size="middle"
            data-testid="brand-directory-table"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'status'">
                <Tag :color="record.status === 'ACTIVE' ? 'success' : 'default'">
                  {{ catalogStatusLabel(record.status) }}
                </Tag>
              </template>
              <template v-else-if="column.key === 'actions'">
                <Button
                  v-if="canWrite"
                  size="small"
                  :danger="record.status === 'ACTIVE'"
                  data-testid="brand-toggle-status"
                  :disabled="brandBusy"
                  @click="toggleBrandStatus(record as ProjectClientBrandItem)"
                >
                  {{ record.status === 'ACTIVE' ? '停用' : '启用' }}
                </Button>
              </template>
            </template>
          </Table>
        </section>
      </div>

      <div v-else class="regions-panel" data-testid="region-tree-panel">
        <Space wrap class="filter-row">
          <Input
            v-model:value="regionQuery"
            allow-clear
            placeholder="按编码/名称搜索"
            aria-label="行政区搜索"
            style="width: 260px"
            data-testid="region-query-input"
            @press-enter="loadRegionRoots"
          />
          <Button type="primary" data-testid="region-search" @click="loadRegionRoots">搜索</Button>
          <Button data-testid="region-reset" @click="regionQuery = ''; loadRegionRoots()">省级骨架</Button>
        </Space>
        <Alert v-if="regionError" type="error" show-icon :message="regionError" />
        <Tree
          v-model:expanded-keys="expandedKeys"
          :tree-data="treeData"
          :load-data="onRegionLoadData"
          show-line
          block-node
          data-testid="region-catalog-tree"
        />
      </div>
    </ListPageLayout>
  </div>
</template>

<style scoped>
.clients-panel,
.regions-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.filter-row {
  margin-bottom: 4px;
}

.create-card,
.brand-panel {
  border: 1px solid var(--sos-color-border, #d9d9d9);
  border-radius: 8px;
  padding: 12px 16px;
  background: var(--sos-color-bg-elevated, #fff);
}

.create-card h3,
.brand-panel h3 {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
}

:deep(.row-selected) > td {
  background: var(--sos-color-primary-bg, #e6f4ff) !important;
}
</style>
