import { loadOrganizationDetail, loadOrganizations } from '@serviceos/api-client'
import { useQuery } from '@tanstack/vue-query'
import type { Ref } from 'vue'
import { computed } from 'vue'

export function useOrganizationDirectoryQuery() {
  return useQuery({ queryKey: ['organizations'], queryFn: loadOrganizations })
}

export function useOrganizationDetailQuery(organizationId: Ref<string | undefined>) {
  return useQuery({
    queryKey: computed(() => ['organization', organizationId.value]),
    queryFn: () => loadOrganizationDetail(organizationId.value as string),
    enabled: computed(() => Boolean(organizationId.value)),
  })
}
