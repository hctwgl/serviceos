import {
  presentUnknownStatus,
  type SemanticStatusPresentation,
} from './semantic-status'

const MAP: Record<string, SemanticStatusPresentation> = {
  MISSING: {
    label: '缺失',
    semantic: 'warning',
    icon: 'warning',
    description: '所需资料尚未提供',
  },
  MISSING_PHOTO: {
    label: '缺少照片',
    semantic: 'warning',
    icon: 'warning',
    description: '缺少照片类资料',
  },
  MISSING_DOCUMENT: {
    label: '缺少文档',
    semantic: 'warning',
    icon: 'warning',
    description: '缺少文档类资料',
  },
  PRESENT: {
    label: '已上传',
    semantic: 'info',
    icon: 'info',
    description: '资料已上传，待后续校验',
  },
  UPLOADED: {
    label: '已上传',
    semantic: 'info',
    icon: 'info',
    description: '资料已上传',
  },
  FINALIZED: {
    label: '已定稿',
    semantic: 'info',
    icon: 'info',
    description: '资料已定稿',
  },
  SCANNING: {
    label: '扫描中',
    semantic: 'info',
    icon: 'sync',
    description: '安全扫描进行中',
  },
  CLEAN: {
    label: '已通过扫描',
    semantic: 'success',
    icon: 'check',
    description: '扫描通过',
  },
  INFECTED: {
    label: '扫描未通过',
    semantic: 'critical',
    icon: 'critical',
    description: '资料未通过安全扫描，禁止下载原件',
  },
  VALIDATED: {
    label: '已校验',
    semantic: 'success',
    icon: 'check',
    description: '机器校验通过',
  },
  STORED: {
    label: '已存储',
    semantic: 'neutral',
    icon: 'info',
    description: '文件已存储',
  },
  REQUIRED: {
    label: '必需',
    semantic: 'warning',
    icon: 'warning',
    description: '该资料槽位为必需',
  },
  OPTIONAL: {
    label: '可选',
    semantic: 'neutral',
    description: '该资料槽位为可选',
  },
}

export function presentEvidenceStatus(
  code: string | null | undefined,
): SemanticStatusPresentation {
  if (code == null || code === '') return presentUnknownStatus(code)
  const normalized = code.trim().toUpperCase()
  const hit = MAP[normalized]
  if (!hit) return presentUnknownStatus(code)
  return { ...hit, rawCode: normalized }
}
