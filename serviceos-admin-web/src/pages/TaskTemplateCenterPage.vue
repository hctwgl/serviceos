<script setup lang="ts">
/**
 * Admin 任务模板中心（M387）。
 * 数据来自服务端 WORKFLOW 投影读模型，前端不得自行解析流程 JSON。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Alert, Button, Empty, Input, Table, Tag } from 'ant-design-vue'
import { ArrowLeftOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import PageHeader from '../patterns/PageHeader.vue'
import SummaryStrip, { type SummaryStripItem } from '../patterns/SummaryStrip.vue'
import RightContextRail from '../patterns/RightContextRail.vue'
import {
  listConfigurationTaskTemplates,
  type ConfigurationTaskTemplateItem,
  type TaskTemplateCategory,
} from '../api/taskTemplates'
import { formatDateTimeDisplay } from '../presentation/date-time.presenter'
import { toUserFacingError } from '../product/errorMessages'

const router = useRouter()
const loading = ref(false)
const error = ref<string | null>(null)
const items = ref<ConfigurationTaskTemplateItem[]>([])
const keyword = ref('')
const activeCategory = ref<'ALL' | TaskTemplateCategory>('ALL')
const selectedKey = ref<string | null>(null)

const categories: Array<{ key: 'ALL' | TaskTemplateCategory; label: string }> = [
  { key: 'ALL', label: '全部' },
  { key: 'INTAKE', label: '受理类' },
  { key: 'DISPATCH', label: '派单类' },
  { key: 'APPOINTMENT', label: '预约类' },
  { key: 'SURVEY', label: '勘测类' },
  { key: 'INSTALL', label: '安装类' },
  { key: 'REVIEW', label: '审核类' },
  { key: 'CORRECTION', label: '整改类' },
  { key: 'FOLLOW_UP', label: '回访类' },
  { key: 'SYSTEM', label: '系统任务' },
]

const filtered = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  return items.value.filter((item) => {
    if (activeCategory.value !== 'ALL' && item.category !== activeCategory.value) return false
    if (!q) return true
    return (
      item.templateName.toLowerCase().includes(q) ||
      item.taskTypeCode.toLowerCase().includes(q) ||
      item.executionRoleLabel.toLowerCase().includes(q)
    )
  })
})

const selected = computed(
  () => items.value.find((item) => item.templateKey === selectedKey.value) ?? null,
)

const summaryItems = computed<SummaryStripItem[]>(() => {
  const published = items.value.filter((item) => item.status === 'PUBLISHED').length
  const drafts = items.value.filter((item) => item.status === 'DRAFT').length
  const referenced = items.value.filter((item) => item.referencedWorkflowCount > 0).length
  const latest = items.value
    .map((item) => item.lastUpdatedAt)
    .filter((value): value is string => !!value)
    .sort()
    .at(-1)
  return [
    { key: 'total', label: '任务模板总数', value: String(items.value.length) },
    { key: 'published', label: '已发布', value: String(published), tone: 'success' },
    { key: 'draft', label: '草稿', value: String(drafts), tone: drafts ? 'warning' : 'default' },
    { key: 'referenced', label: '被流程引用', value: String(referenced), tone: 'info' },
    {
      key: 'updated',
      label: '最近更新',
      value: latest ? formatDateTimeDisplay(latest) : '—',
    },
  ]
})

const columns = [
  { title: '模板名称', dataIndex: 'templateName', key: 'templateName' },
  { title: '任务编码', dataIndex: 'taskTypeCode', key: 'taskTypeCode', width: 140 },
  { title: '分类', dataIndex: 'categoryLabel', key: 'categoryLabel', width: 100 },
  { title: '执行角色', dataIndex: 'executionRoleLabel', key: 'executionRoleLabel', width: 120 },
  { title: '分配策略', dataIndex: 'assignmentStrategyLabel', key: 'assignmentStrategyLabel' },
  { title: '表单', dataIndex: 'formSummary', key: 'formSummary' },
  { title: '资料', dataIndex: 'evidenceSummary', key: 'evidenceSummary' },
  { title: 'SLA', dataIndex: 'slaSummary', key: 'slaSummary', width: 160 },
  { title: '状态', dataIndex: 'statusLabel', key: 'statusLabel', width: 90 },
  { title: '引用流程数', dataIndex: 'referencedWorkflowCount', key: 'referencedWorkflowCount', width: 110 },
]

function selectTemplate(templateKey: string) {
  selectedKey.value = templateKey
}

async function load() {
  loading.value = true
  error.value = null
  try {
    items.value = await listConfigurationTaskTemplates()
    if (!selectedKey.value && items.value[0]) {
      selectedKey.value = items.value[0].templateKey
    }
  } catch (err) {
    error.value = toUserFacingError(err).message
    items.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="task-template-center" data-testid="task-template-center">
    <PageHeader
      title="任务模板中心"
      description="统一查看任务由谁执行、如何分配、需要什么表单与资料、绑定哪些 SLA，以及被哪些流程引用。"
    >
      <template #breadcrumb>
        <a @click.prevent="router.push({ name: 'ADMIN.WORKFLOW.DESIGNER' })">工作流设计器</a>
        <span> / 任务模板中心</span>
      </template>
      <template #secondary-actions>
        <Button @click="router.push({ name: 'ADMIN.CONFIGURATION.DESIGNER' })">
          <template #icon><ArrowLeftOutlined /></template>
          全部配置资产
        </Button>
      </template>
      <template #primary-action>
        <Button type="primary" :loading="loading" @click="load">
          <template #icon><ReloadOutlined /></template>
          刷新
        </Button>
      </template>
    </PageHeader>

    <Alert
      v-if="error"
      type="error"
      show-icon
      :message="error"
      style="margin-bottom: 12px"
    />
    <Alert
      type="info"
      show-icon
      message="任务模板由服务端从工作流资产投影"
      description="当前为只读产品目录。独立任务模板写聚合、升级策略与批量发布将在后续切片补齐；缺口见右侧详情。"
      style="margin-bottom: 12px"
    />

    <SummaryStrip :items="summaryItems" />

    <div class="task-template-center__body">
      <aside class="category-tree" aria-label="任务分类">
        <h2>任务分类</h2>
        <button
          v-for="item in categories"
          :key="item.key"
          type="button"
          class="category-tree__item"
          :class="{ active: activeCategory === item.key }"
          @click="activeCategory = item.key"
        >
          {{ item.label }}
          <span>
            {{
              item.key === 'ALL'
                ? items.length
                : items.filter((row) => row.category === item.key).length
            }}
          </span>
        </button>
      </aside>

      <main class="table-panel">
        <div class="toolbar">
          <Input
            v-model:value="keyword"
            allow-clear
            placeholder="搜索模板名称、编码或执行角色"
            style="max-width: 360px"
            aria-label="搜索任务模板"
          />
        </div>
        <Table
          v-if="filtered.length"
          size="middle"
          row-key="templateKey"
          :loading="loading"
          :pagination="false"
          :columns="columns"
          :data-source="filtered"
          :custom-row="(record) => ({ onClick: () => selectTemplate(record.templateKey) })"
          :row-class-name="(record) => (record.templateKey === selectedKey ? 'is-selected' : '')"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'statusLabel'">
              <Tag :color="record.status === 'PUBLISHED' ? 'success' : 'processing'">
                {{ record.statusLabel }}
              </Tag>
            </template>
          </template>
        </Table>
        <Empty v-else-if="!loading" description="当前分类下没有任务模板" />
      </main>

      <RightContextRail title="模板详情">
        <template v-if="selected">
          <section>
            <h3>基础信息</h3>
            <p><strong>{{ selected.templateName }}</strong></p>
            <p>分类：{{ selected.categoryLabel }}</p>
            <p>编码：{{ selected.taskTypeCode }}</p>
            <p>状态：{{ selected.statusLabel }}</p>
          </section>
          <section>
            <h3>执行规则</h3>
            <p>执行角色：{{ selected.executionRoleLabel }}</p>
            <p>分配策略：{{ selected.assignmentStrategyLabel || '—' }}</p>
          </section>
          <section>
            <h3>表单与资料</h3>
            <p>{{ selected.formSummary || '—' }}</p>
            <p>{{ selected.evidenceSummary || '—' }}</p>
            <p>{{ selected.slaSummary || '—' }}</p>
          </section>
          <section>
            <h3>被以下流程引用</h3>
            <ul v-if="selected.referencedWorkflowNames.length">
              <li v-for="name in selected.referencedWorkflowNames" :key="name">{{ name }}</li>
            </ul>
            <p v-else>尚未被流程引用</p>
          </section>
          <section v-if="selected.gaps.length">
            <h3>数据缺口</h3>
            <ul>
              <li v-for="(gap, index) in selected.gaps" :key="index">{{ gap }}</li>
            </ul>
          </section>
        </template>
        <Empty v-else description="选择左侧模板查看详情" />
      </RightContextRail>
    </div>
  </div>
</template>

<style scoped>
.task-template-center__body {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr) 300px;
  gap: 16px;
  align-items: start;
}
.category-tree,
.table-panel {
  background: var(--sos-color-surface-card);
  border: 1px solid var(--sos-color-border-default);
  border-radius: var(--sos-radius-lg);
  padding: 12px;
}
.category-tree h2 {
  margin: 0 0 10px;
  font-size: 15px;
}
.category-tree__item {
  width: 100%;
  display: flex;
  justify-content: space-between;
  border: 0;
  background: transparent;
  padding: 8px 10px;
  border-radius: var(--sos-radius-md);
  cursor: pointer;
  color: var(--sos-color-text-secondary);
}
.category-tree__item.active,
.category-tree__item:hover {
  background: var(--sos-primary-100);
  color: var(--sos-primary-700);
}
.toolbar {
  margin-bottom: 12px;
}
:deep(tr.is-selected) > td {
  background: var(--sos-primary-050) !important;
}
section + section {
  margin-top: 14px;
}
h3 {
  margin: 0 0 8px;
  font-size: 14px;
}
p,
ul {
  margin: 0 0 6px;
  color: var(--sos-color-text-secondary);
  font-size: 13px;
}
@media (max-width: 1280px) {
  .task-template-center__body {
    grid-template-columns: 160px minmax(0, 1fr);
  }
  .task-template-center__body :deep(.sos-right-rail) {
    grid-column: 1 / -1;
  }
}
</style>
