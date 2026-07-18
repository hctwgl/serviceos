<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import {
  createNetworkPortalTechnicianMembership,
  listNetworkPortalTechnicianMemberships,
  listNetworkPortalTechnicians,
  submitNetworkPortalTechnicianQualification,
  terminateNetworkPortalTechnicianMembership,
  type NetworkPortalMembershipItem,
  type NetworkPortalTechnicianItem,
} from '../api/networkPortal'

const props = defineProps<{ networkContextId: string | null }>()
const items = ref<NetworkPortalTechnicianItem[]>([])
const memberships = ref<NetworkPortalMembershipItem[]>([])
const error = ref<string | null>(null)
const actionMessage = ref<string | null>(null)

const createProfileId = ref('')
const createValidFrom = ref(new Date().toISOString())
const terminateMembershipId = ref('')
const terminateVersion = ref('')
const terminateReason = ref('')
const qualProfileId = ref('')
const qualCode = ref('EV-INSTALL')
const qualValidFrom = ref(new Date().toISOString())
const qualValidTo = ref('')

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
    return
  }
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
    error.value = err instanceof Error ? err.message : '师傅列表加载失败'
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
    actionMessage.value = err instanceof Error ? err.message : '绑定失败'
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
    actionMessage.value = err instanceof Error ? err.message : '终止失败'
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
    actionMessage.value = `已提交资质 ${result.data.id}（${result.data.status}）`
  } catch (err) {
    actionMessage.value = err instanceof Error ? err.message : '资质提交失败'
  }
}

function fillTerminate(item: NetworkPortalTechnicianItem) {
  terminateMembershipId.value = item.membershipId
  // M206：必须从 memberships 列表（或 technicians.membershipVersion）取真实 version，禁止硬编码 1
  terminateVersion.value = versionForMembership(item.membershipId)
  terminateReason.value = '网点调整'
  qualProfileId.value = item.technicianProfileId
}

onMounted(() => {
  void load()
})
watch(() => props.networkContextId, () => {
  void load()
})
</script>

<template>
  <section data-testid="network-portal-technicians" data-page-id="NETWORK.TECHNICIAN.LIST">
    <h2>本网点师傅</h2>
    <p v-if="error" data-testid="network-portal-error">{{ error }}</p>
    <table v-else data-testid="network-technicians-table">
      <thead>
        <tr>
          <th>关系 ID</th>
          <th>姓名</th>
          <th>主体</th>
          <th>档案状态</th>
          <th>关系状态</th>
          <th>有效期</th>
          <th>版本</th>
          <th>档案 ID</th>
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
            <RouterLink
              :to="`/network-portal/technicians/memberships/${item.membershipId}`"
              data-testid="membership-case-deeplink"
            >
              {{ item.membershipId }}
            </RouterLink>
          </td>
          <td>{{ item.displayName }}</td>
          <td data-testid="technician-principal-id">{{ item.principalId }}</td>
          <td>{{ item.profileStatus }}</td>
          <td>{{ item.membershipStatus }}</td>
          <td data-testid="technician-valid-range">
            {{ item.validFrom }} → {{ item.validTo ?? '—' }}
          </td>
          <td data-testid="technician-membership-version">
            {{ versionForMembership(item.membershipId) }}
          </td>
          <td>{{ item.technicianProfileId }}</td>
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
    <p v-if="!error && items.length === 0">暂无 ACTIVE 师傅关系</p>

    <section data-testid="network-manage-technician-forms" data-page-id="NETWORK.QUALIFICATION">
      <h3>师傅关系与资质（M204/M206）</h3>
      <p v-if="actionMessage" data-testid="manage-technician-message">{{ actionMessage }}</p>

      <form
        data-testid="network-create-membership-form"
        @submit.prevent="onCreateMembership"
      >
        <h4>绑定师傅关系</h4>
        <label>
          师傅档案 ID
          <input
            v-model="createProfileId"
            data-testid="create-membership-profile-id"
            required
          />
        </label>
        <label>
          生效时间
          <input
            v-model="createValidFrom"
            data-testid="create-membership-valid-from"
            required
          />
        </label>
        <button type="submit" data-testid="create-membership-submit">绑定</button>
      </form>

      <form
        data-testid="network-terminate-membership-form"
        @submit.prevent="onTerminateMembership"
      >
        <h4>终止师傅关系</h4>
        <label>
          关系 ID
          <input
            v-model="terminateMembershipId"
            data-testid="terminate-membership-id"
            required
          />
        </label>
        <label>
          版本
          <input
            v-model="terminateVersion"
            data-testid="terminate-membership-version"
            required
          />
        </label>
        <label>
          原因
          <input
            v-model="terminateReason"
            data-testid="terminate-membership-reason"
            required
          />
        </label>
        <button type="submit" data-testid="terminate-membership-submit">终止</button>
      </form>

      <form
        data-testid="network-submit-qualification-form"
        @submit.prevent="onSubmitQualification"
      >
        <h4>提交资质（PENDING）</h4>
        <label>
          师傅档案 ID
          <input
            v-model="qualProfileId"
            data-testid="submit-qualification-profile-id"
            required
          />
        </label>
        <label>
          资质代码
          <input v-model="qualCode" data-testid="submit-qualification-code" required />
        </label>
        <label>
          生效时间
          <input
            v-model="qualValidFrom"
            data-testid="submit-qualification-valid-from"
            required
          />
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
