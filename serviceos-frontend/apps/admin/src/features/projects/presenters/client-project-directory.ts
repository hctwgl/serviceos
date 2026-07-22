export function presentProjectStatus(status: string) {
  if (status === 'ACTIVE') return { label: '运行中', tone: 'green' as const }
  if (status === 'DRAFT') return { label: '筹备中', tone: 'orange' as const }
  if (status === 'SUSPENDED') return { label: '已暂停', tone: 'orange' as const }
  return { label: '已结束', tone: 'gray' as const }
}

export function presentConfigurationStatus(status: string) {
  const labels: Record<string, { label: string; tone: 'blue' | 'green' | 'orange' | 'gray' }> = {
    NOT_CONFIGURED: { label: '待首次配置', tone: 'gray' },
    DRAFT: { label: '草稿待发布', tone: 'orange' },
    PUBLISHED: { label: '已发布', tone: 'green' },
    UNPUBLISHED_CHANGES: { label: '有未发布变更', tone: 'orange' },
    NO_PERMISSION: { label: '无权查看配置', tone: 'gray' },
  }
  return labels[status] ?? { label: '配置状态异常', tone: 'gray' as const }
}

export function presentProjectPeriod(startsOn: string, endsOn: string | null) {
  return `${startsOn} 至 ${endsOn ?? '长期有效'}`
}
