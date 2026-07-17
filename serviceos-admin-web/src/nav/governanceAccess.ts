import { loadAdminPortalNavigation, navItemVisible } from './portalNavigation'

export type GovernanceNavKey =
  | 'users'
  | 'organizations'
  | 'networks'
  | 'technicians'
  | 'roles'
  | 'grants'

export type GovernanceAccess = Record<GovernanceNavKey, boolean>

const EMPTY: GovernanceAccess = {
  users: false,
  organizations: false,
  networks: false,
  technicians: false,
  roles: false,
  grants: false,
}

const PAGE_IDS: Record<GovernanceNavKey, string> = {
  users: 'ADMIN.USER.DIRECTORY',
  organizations: 'ADMIN.ORGANIZATION.DIRECTORY',
  networks: 'ADMIN.NETWORK.DIRECTORY',
  technicians: 'ADMIN.TECHNICIAN.DIRECTORY',
  roles: 'ADMIN.ROLE.DIRECTORY',
  grants: 'ADMIN.GRANT.DIRECTORY',
}

/**
 * M188：治理入口可见性改为消费 `/me/navigation` pageId，不再探测目录读接口。
 */
export async function probeGovernanceAccess(): Promise<GovernanceAccess> {
  const nav = await loadAdminPortalNavigation()
  if (nav.error && nav.items.length === 0) {
    return { ...EMPTY }
  }
  return {
    users: navItemVisible(nav.items, PAGE_IDS.users),
    organizations: navItemVisible(nav.items, PAGE_IDS.organizations),
    networks: navItemVisible(nav.items, PAGE_IDS.networks),
    technicians: navItemVisible(nav.items, PAGE_IDS.technicians),
    roles: navItemVisible(nav.items, PAGE_IDS.roles),
    grants: navItemVisible(nav.items, PAGE_IDS.grants),
  }
}
