import { statusLabel as coreStatusLabel } from '@serviceos/web-core'

/** 网点端本地补充：Portal persona 等不得放进共享 web-core。 */
const LOCAL_LABELS: Record<string, string> = {
  INTERNAL_EMPLOYEE: '内部员工',
  NETWORK_MEMBER: '网点成员',
  TECHNICIAN: '服务师傅',
  CONSUMER: '客户',
  SERVICE_ACCOUNT: '服务账号',
  ADMIN: '管理端',
  NETWORK: '网点端',
}

export function statusLabel(code: string | null | undefined): string {
  if (code == null || code === '') {
    return '—'
  }
  const normalized = code.trim().toUpperCase()
  return LOCAL_LABELS[normalized] ?? coreStatusLabel(code)
}
