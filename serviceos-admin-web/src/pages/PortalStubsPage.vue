<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listMeContexts, listMeNavigation } from '../api/me'
import { loadNetworkPortalNavigation } from '../portal/networkPortalStub'
import { loadTechnicianPortalNavigation } from '../portal/technicianPortalStub'

const message = ref('')
const adminPages = ref<string[]>([])
const networkPages = ref<string[]>([])
const technicianPages = ref<string[]>([])
const forgedResult = ref('')

async function refresh() {
  const contexts = await listMeContexts()
  const admin = contexts.data.contexts.find((context) => context.portal === 'ADMIN')
  if (admin) {
    const navigation = await listMeNavigation(admin.contextId, contexts.data.contextVersion)
    adminPages.value = navigation.data.items.map((item) => item.pageId)
  }
  const network = await loadNetworkPortalNavigation()
  networkPages.value = network.navigation?.items.map((item) => item.pageId) ?? []
  const technician = await loadTechnicianPortalNavigation()
  technicianPages.value = technician.navigation?.items.map((item) => item.pageId) ?? []
  message.value = `contexts=${contexts.data.contexts.length}; version=${contexts.data.contextVersion}`
}

async function tryForgeNetwork() {
  const forged = await loadNetworkPortalNavigation(`NETWORK|NETWORK|${crypto.randomUUID()}`)
  forgedResult.value = forged.ok ? 'unexpected-allow' : (forged.error ?? 'denied')
}

onMounted(() => {
  void refresh()
})
</script>

<template>
  <section data-testid="portal-stubs-page">
    <h2>M188 Portal stubs</h2>
    <p data-testid="portal-stub-summary">{{ message }}</p>
    <p>Admin pages: <span data-testid="admin-page-ids">{{ adminPages.join(',') }}</span></p>
    <p>Network pages: <span data-testid="network-page-ids">{{ networkPages.join(',') }}</span></p>
    <p>
      Technician pages:
      <span data-testid="technician-page-ids">{{ technicianPages.join(',') }}</span>
    </p>
    <button type="button" data-testid="forge-network-context" @click="tryForgeNetwork">
      伪造 NETWORK 上下文
    </button>
    <p data-testid="forge-network-result">{{ forgedResult }}</p>
  </section>
</template>
