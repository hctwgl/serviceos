<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import PageError from '../../../components/PageError.vue'
import StatusPill from '../../../components/StatusPill.vue'
import { formatDateTime } from '../../../presenters/work-order'
import { presentProjectPeriod, presentProjectStatus } from '../presenters/client-project-directory'
import { useProjectWorkspaceQuery } from '../queries/use-project-workspace-query'

const route = useRoute()
const projectId = computed(() => String(route.params.id ?? ''))
const workspace = useProjectWorkspaceQuery(projectId)
const project = computed(() => workspace.data.value)

function profileStatus(status: string) {
  if (status === 'ACTIVE') return { label: '已发布', tone: 'green' as const }
  if (status === 'DRAFT') return { label: '草稿', tone: 'orange' as const }
  if (status === 'SUSPENDED') return { label: '已暂停', tone: 'orange' as const }
  return { label: '已归档', tone: 'gray' as const }
}
</script>

<template>
  <div class="project-workspace-page">
    <PageError v-if="workspace.isError.value" :detail="workspace.error.value?.message ?? '项目详情加载失败'" />
    <div v-else-if="workspace.isLoading.value" class="page-loading">正在加载项目工作区…</div>
    <template v-else-if="project">
      <div class="page-heading inline project-object-header">
        <div><p class="breadcrumb">客户与项目 / 项目管理 / 项目详情</p><div class="project-title-line"><h1>{{ project.projectName }}</h1><StatusPill :tone="presentProjectStatus(project.status).tone" :label="presentProjectStatus(project.status).label" /></div><p>{{ project.clientName ?? '客户名称缺失' }} · {{ project.projectCode }}</p></div>
        <div class="heading-actions"><RouterLink class="primary-link-button" :to="`/work-orders?projectId=${project.projectId}`">查看项目工单</RouterLink></div>
      </div>
      <PageError v-if="!project.dataComplete" :detail="project.dataProblem ?? '项目页面数据不完整'" />
      <section class="project-fact-strip">
        <div><span>服务周期</span><strong>{{ presentProjectPeriod(project.startsOn, project.endsOn) }}</strong></div>
        <div><span>服务区域</span><strong>{{ project.regionNames.join('、') || '数据不完整' }}</strong></div>
        <div><span>参与网点</span><strong>{{ project.networkNames.join('、') || '尚未配置' }}</strong></div>
        <div><span>使用中工单</span><strong>{{ project.activeWorkOrderCount === null ? '无权查看' : `${project.activeWorkOrderCount}${project.activeWorkOrderCountTruncated ? '+' : ''} 单` }}</strong></div>
      </section>
      <section class="project-workspace-shell">
        <nav class="project-subnav"><button class="active">项目概览</button><button>服务与合同</button><button>项目团队与区域分工</button><button>履约配置</button><button>项目工单</button><button>操作记录</button></nav>
        <main class="project-workspace-content">
          <div class="project-section-heading"><div><p>履约配置</p><h2>工单类型与履约方案</h2><span>每套方案定义一种服务产品的流程、表单、资料、SLA、派单和审核规则。</span></div><StatusPill v-if="project.configurationReadable" tone="blue" :label="`${project.fulfillmentProfiles.length} 套方案`" /></div>
          <div v-if="!project.configurationReadable" class="permission-state"><h3>无权查看履约配置</h3><p>项目基本信息可见，但当前角色没有项目履约配置读取权限。</p></div>
          <div v-else-if="!project.fulfillmentProfiles.length" class="empty-state"><h3>项目尚未建立履约方案</h3><p>需要先明确服务产品和履约流程，再投入正式接单。</p></div>
          <div v-else class="fulfillment-profile-grid">
            <article v-for="profile in project.fulfillmentProfiles" :key="profile.profileId" class="fulfillment-profile-card">
              <header><div><p>{{ profile.serviceProductName ?? '服务产品名称缺失' }}</p><h3>{{ profile.profileName }}</h3></div><StatusPill :tone="profileStatus(profile.status).tone" :label="profileStatus(profile.status).label" /></header>
              <PageError v-if="!profile.dataComplete" :detail="profile.dataProblem ?? '履约方案数据不完整'" />
              <dl><div><dt>履约阶段</dt><dd>{{ profile.stageCount ?? '—' }} 个</dd></div><div><dt>业务表单</dt><dd>{{ profile.formCount ?? '—' }} 份</dd></div><div><dt>资料要求</dt><dd>{{ profile.evidenceCount ?? '—' }} 项</dd></div><div><dt>当前版本</dt><dd>{{ profile.activeVersion ? `V${profile.activeVersion}` : '尚未发布' }}</dd></div></dl>
              <section><div><span>流程概览</span><strong>{{ profile.workflowSummary ?? '尚未生成流程摘要' }}</strong></div><div><span>SLA 概览</span><strong>{{ profile.slaSummary ?? '尚未生成 SLA 摘要' }}</strong></div></section>
              <footer><span>最近更新 {{ formatDateTime(profile.updatedAt) }}</span><RouterLink :to="`/projects/${project.projectId}/fulfillment?profileId=${profile.profileId}`">进入履约配置</RouterLink></footer>
            </article>
          </div>
        </main>
        <aside class="project-context-rail"><section><h3>项目责任</h3><p>项目团队与行政区域分工将在下一切片接入工单岗位人员匹配。</p></section><section><h3>版本规则</h3><p>新工单使用当前生效配置，进行中的工单继续使用创建时冻结的履约版本。</p></section><section><h3>数据更新时间</h3><p>{{ formatDateTime(project.asOf) }}</p></section></aside>
      </section>
    </template>
  </div>
</template>
