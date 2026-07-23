<script setup lang="ts">
import { statusLabel } from '../product/labels'
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { formatDateTime, safeProblemMessage } from '../product/labels'
import {
  createNetworkPortalTechnicianMembership,
  listNetworkPortalTechnicianMemberships,
  listNetworkPortalTechnicians,
  submitNetworkPortalTechnicianQualification,
  terminateNetworkPortalTechnicianMembership,
  type NetworkPortalMembershipItem,
  type NetworkPortalTechnicianItem,
} from '../api/networkPortal'
import SummaryStrip, { type SummaryStripItem } from '../components/SummaryStrip.vue'
import PageState from '../components/PageState.vue'

const props = defineProps<{ networkContextId: string | null }>()
const items = ref<NetworkPortalTechnicianItem[]>([])
const memberships = ref<NetworkPortalMembershipItem[]>([])
const error = ref<string | null>(null)
const actionMessage = ref<string | null>(null)
const loading = ref(false)
const showManage = ref(false)

const createProfileId = ref('')
const createValidFrom = ref(new Date().toISOString())
const terminateMembershipId = ref('')
const terminateVersion = ref('')
const terminateReason = ref('')
const qualProfileId = ref('')
const qualCode = ref('EV-INSTALL')
const qualValidFrom = ref(new Date().toISOString())
const qualValidTo = ref('')

const summaryItems = computed<SummaryStripItem[]>(() => {
  const active = items.value.filter((item) => item.membershipStatus === 'ACTIVE').length
  const profileActive = items.value.filter((item) => item.profileStatus === 'ACTIVE').length
  const openTasks = items.value.reduce((sum, item) => sum + (item.openTaskCount ?? 0), 0)
  const pendingQuals = items.value.reduce(
    (sum, item) => sum + (item.pendingQualificationCount ?? 0),
    0,
  )
  return [
    {
      key: 'active',
      label: '可接单关系',
      value: active,
      testId: 'technicians-summary-active',
    },
    {
      key: 'profile',
      label: '档案启用',
      value: profileActive,
      testId: 'technicians-summary-profile-active',
    },
    {
      key: 'openTasks',
      label: '开放任务',
      value: openTasks,
      testId: 'technicians-summary-open-tasks',
    },
    {
      key: 'pendingQuals',
      label: '待审资质',
      value: pendingQuals,
      to: '/network-portal/qualifications',
      testId: 'technicians-summary-pending-qualifications',
    },
    {
      key: 'qual',
      label: '资质入口',
      value: '待审提交',
      to: '/network-portal/qualifications',
      testId: 'technicians-summary-qualifications',
    },
  ]
})

function versionForMembership(membershipId: string): string {
  const fromList = memberships.value.find((row) => row.id === membershipId)
  if (fromList != null) {
    return String(fromList.version)
  }
  const fromTech = items.value.find((row) => row.membershipId === membershipId)
  if (fromTech?.membershipVersion != null) {
    return String(fromTech.membershipVersion)
  }
  return ''
}

async function load() {
  if (!props.networkContextId) {
    items.value = []
    memberships.value = []
    error.value = '请选择 NETWORK 上下文'
    loading.value = false
    return
  }
  loading.value = true
  try {
    const [techPage, memPage] = await Promise.all([
      listNetworkPortalTechnicians(props.networkContextId),
      listNetworkPortalTechnicianMemberships(props.networkContextId, { status: 'ACTIVE' }),
    ])
    items.value = techPage.items
    memberships.value = memPage.items
    error.value = null
  } catch (err) {
    items.value = []
    memberships.value = []
    error.value = safeProblemMessage(err) || '师傅列表加载失败'
  } finally {
    loading.value = false
  }
}

async function onCreateMembership() {
  if (!props.networkContextId) return
  actionMessage.value = null
  try {
    const result = await createNetworkPortalTechnicianMembership(props.networkContextId, {
      technicianProfileId: createProfileId.value.trim(),
      validFrom: createValidFrom.value.trim(),
    })
    actionMessage.value = `已绑定关系 ${result.data.id}`
    await load()
  } catch (err) {
    actionMessage.value = safeProblemMessage(err) || '绑定失败'
  }
}

async function onTerminateMembership() {
  if (!props.networkContextId) return
  actionMessage.value = null
  try {
    const result = await terminateNetworkPortalTechnicianMembership(
      props.networkContextId,
      terminateMembershipId.value.trim(),
      { reason: terminateReason.value.trim() },
      Number(terminateVersion.value),
    )
    actionMessage.value = `已终止关系 ${result.data.id}`
    await load()
  } catch (err) {
    actionMessage.value = safeProblemMessage(err) || '终止失败'
  }
}

async function onSubmitQualification() {
  if (!props.networkContextId) return
  actionMessage.value = null
  try {
    const result = await submitNetworkPortalTechnicianQualification(props.networkContextId, {
      technicianProfileId: qualProfileId.value.trim(),
      qualificationCode: qualCode.value.trim(),
      validFrom: qualValidFrom.value.trim(),
      validTo: qualValidTo.value.trim() || null,
    })
    actionMessage.value = `已提交资质 ${result.data.id}（${statusLabel(result.data.status)}）`
  } catch (err) {
    actionMessage.value = safeProblemMessage(err) || '资质提交失败'
  }
}

function fillTerminate(item: NetworkPortalTechnicianItem) {
  terminateMembershipId.value = item.membershipId
  terminateVersion.value = versionForMembership(item.membershipId)
  terminateReason.value = '网点调整'
  qualProfileId.value = item.technicianProfileId
  showManage.value = true
}

onMounted(() => {
  void load()
})
watch(
  () => props.networkContextId,
  () => {
    void load()
  },
)
</script>

<template>
  <section
    data-testid="network-portal-technicians"
    data-page-id="NETWORK.TECHNICIAN.LIST"
    class="tech-page"
  >
    <header class="hero">
      <div>
        <p class="eyebrow">师傅与资质</p>
        <h2>本网点师傅</h2>
        <p class="hint">
          管理本网点师傅关系与资质提交。停用前请确认未完成任务与预约；资质提交不等于总部审核通过。
        </p>
      </div>
      <div class="hero-actions">
        <button type="button" @click="load">刷新</button>
        <button type="button" class="primary" data-testid="technicians-toggle-manage" @click="showManage = !showManage">
          {{ showManage ? '收起管理' : '关系与资质管理' }}
        </button>
      </div>
    </header>

    <PageState v-if="loading && !items.length && !error" kind="loading" />
    <p v-else-if="error" data-testid="network-portal-error">{{ error }}</p>
    <template v-else>
      <SummaryStrip :items="summaryItems" />

      <div class="panel">
        <table data-testid="network-technicians-table">
          <thead>
            <tr>
              <th>师傅</th>
              <th>主体</th>
              <th>档案 / 关系</th>
              <th>开放任务</th>
              <th>资质</th>
              <th>有效期</th>
              <th>版本</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in items"
              :key="item.membershipId"
              :data-testid="`technician-row-${item.membershipId}`"
            >
              <td>
                <strong>{{ item.displayName }}</strong>
                <div class="muted">
                  <RouterLink
                    :to="`/network-portal/technicians/memberships/${item.membershipId}`"
                    data-testid="membership-case-deeplink"
                  >
                    打开关系详情
                  </RouterLink>
                </div>
              </td>
              <td data-testid="technician-principal-id">{{ item.principalId }}</td>
              <td>
                {{ statusLabel(item.profileStatus) }} /
                {{ statusLabel(item.membershipStatus) }}
              </td>
              <td data-testid="technician-open-task-count">{{ item.openTaskCount ?? 0 }}</td>
              <td data-testid="technician-qualification-summary">
                <div>{{ item.qualificationSummary }}</div>
                <div class="muted">
                  已通过 {{ item.approvedQualificationCount ?? 0 }} · 待审
                  {{ item.pendingQualificationCount ?? 0 }}
                </div>
              </td>
              <td data-testid="technician-valid-range">
                <div>{{ formatDateTime(item.validFrom) }} → {{ item.validTo ? formatDateTime(item.validTo) : '—' }}</div>
                <div class="muted">{{ item.validFrom }} → {{ item.validTo ?? '—' }}</div>
              </td>
              <td data-testid="technician-membership-version">
                {{ versionForMembership(item.membershipId) }}
              </td>
              <td>
                <button
                  type="button"
                  data-testid="fill-terminate-from-row"
                  @click="fillTerminate(item)"
                >
                  填入终止/资质
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        <PageState
          v-if="!items.length"
          kind="empty"
          guide="暂无 ACTIVE 师傅关系。可通过下方管理区绑定师傅档案。"
        />
      </div>

      <p class="muted gap-note">
        UI_DATA_GAP：技能 taxonomy、服务区域、最近同步与资质到期提醒尚未由专用读模型完整交付；
        当前任务量与资质摘要已由服务端列表字段交付（M421）。
      </p>
    </template>

    <section
      v-show="showManage"
      data-testid="network-manage-technician-forms"
      data-page-id="NETWORK.QUALIFICATION"
      class="manage"
    >
      <h3>师傅关系与资质管理</h3>
      <p class="hint">终止关系前请确认未完成任务与预约的重新分配计划；资质提交后进入 PENDING 审核。</p>
      <p v-if="actionMessage" data-testid="manage-technician-message">{{ actionMessage }}</p>

      <form data-testid="network-create-membership-form" class="form-card" @submit.prevent="onCreateMembership">
        <h4>绑定师傅关系</h4>
        <label>
          师傅档案 ID
          <input v-model="createProfileId" data-testid="create-membership-profile-id" required />
        </label>
        <label>
          生效时间
          <input v-model="createValidFrom" data-testid="create-membership-valid-from" required />
        </label>
        <button type="submit" data-testid="create-membership-submit">绑定</button>
      </form>

      <form
        data-testid="network-terminate-membership-form"
        class="form-card"
        @submit.prevent="onTerminateMembership"
      >
        <h4>终止师傅关系</h4>
        <label>
          关系 ID
          <input v-model="terminateMembershipId" data-testid="terminate-membership-id" required />
        </label>
        <label>
          版本
          <input v-model="terminateVersion" data-testid="terminate-membership-version" required />
        </label>
        <label>
          原因
          <input v-model="terminateReason" data-testid="terminate-membership-reason" required />
        </label>
        <button type="submit" data-testid="terminate-membership-submit">终止</button>
      </form>

      <form
        data-testid="network-submit-qualification-form"
        class="form-card"
        @submit.prevent="onSubmitQualification"
      >
        <h4>提交资质（PENDING）</h4>
        <label>
          师傅档案 ID
          <input v-model="qualProfileId" data-testid="submit-qualification-profile-id" required />
        </label>
        <label>
          资质代码
          <input v-model="qualCode" data-testid="submit-qualification-code" required />
        </label>
        <label>
          生效时间
          <input v-model="qualValidFrom" data-testid="submit-qualification-valid-from" required />
        </label>
        <label>
          失效时间（可选）
          <input v-model="qualValidTo" data-testid="submit-qualification-valid-to" />
        </label>
        <button type="submit" data-testid="submit-qualification-submit">提交资质</button>
      </form>
    </section>
  </section>
</template>

<style scoped>
.tech-page {
  display: grid;
  gap: 14px;
}
.hero {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}
.eyebrow {
  margin: 0 0 4px;
  color: var(--sos-primary-600);
  font-size: 12px;
  letter-spacing: 0.08em;
}
.hero h2 {
  margin: 0 0 6px;
  font-size: 22px;
}
.hero-actions {
  display: flex;
  gap: 8px;
}
.hint,
.muted,
.gap-note {
  color: var(--sos-color-text-tertiary);
  font-size: 13px;
}
.panel,
.manage {
  border: 1px solid var(--sos-color-border-default);
  border-radius: var(--sos-radius-md);
  background: var(--sos-color-surface-card);
  padding: 12px 14px;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th,
td {
  text-align: left;
  padding: 0.55rem 0.4rem;
  border-bottom: 1px solid var(--sos-color-border-light);
  font-size: 13px;
  vertical-align: top;
}
.form-card {
  display: grid;
  gap: 8px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--sos-color-border-light);
}
.form-card label {
  display: grid;
  gap: 4px;
  font-size: 13px;
}
.form-card input {
  border: 1px solid var(--sos-color-border-default);
  border-radius: 6px;
  padding: 8px 10px;
}
button {
  border: 1px solid var(--sos-color-border-default);
  background: #fff;
  border-radius: 6px;
  padding: 0.4rem 0.75rem;
  cursor: pointer;
}
button.primary {
  background: var(--sos-primary-600);
  border-color: var(--sos-primary-600);
  color: #fff;
}
</style>
