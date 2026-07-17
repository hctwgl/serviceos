import { apiProbe } from '../api/client'
import { currentLocalOidcSession } from '../auth/oidc'

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

/**
 * M187 导航门禁：在 /me/capabilities（M188）落地前，以真实目录读接口探测后端 Capability。
 * 探测失败一律隐藏入口；深链仍由页面与后端失败关闭。
 */
export async function probeGovernanceAccess(): Promise<GovernanceAccess> {
  if (!currentLocalOidcSession().authenticated) {
    return { ...EMPTY }
  }
  const [users, organizations, networks, technicians, roles, grants] = await Promise.all([
    apiProbe('/security-principals?limit=1'),
    apiProbe('/organizations'),
    apiProbe('/service-networks'),
    apiProbe('/technician-profiles'),
    apiProbe('/roles'),
    apiProbe('/role-grants'),
  ])
  return {
    users: users === 'allow',
    organizations: organizations === 'allow',
    networks: networks === 'allow',
    technicians: technicians === 'allow',
    roles: roles === 'allow',
    grants: grants === 'allow',
  }
}
