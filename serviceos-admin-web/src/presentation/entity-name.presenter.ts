/**
 * 实体名称展示：优先业务名称/编码；缺失时不回退完整 UUID。
 */

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

export function isUuid(value: string | null | undefined): boolean {
  return !!value && UUID_RE.test(value.trim())
}

export type EntityNamePresentation = {
  label: string
  /** 仅诊断抽屉可用 */
  technicalId?: string
  unavailableReason?: 'not_provided' | 'not_loaded' | 'name_unavailable'
}

export function presentEntityName(input: {
  name?: string | null
  code?: string | null
  id?: string | null
  loaded?: boolean
}): EntityNamePresentation {
  const name = input.name?.trim()
  if (name) {
    return {
      label: name,
      technicalId: input.id ?? undefined,
    }
  }
  const code = input.code?.trim()
  if (code && !isUuid(code)) {
    return {
      label: code,
      technicalId: input.id ?? undefined,
    }
  }
  if (input.loaded === false) {
    return {
      label: '暂未加载',
      technicalId: input.id ?? undefined,
      unavailableReason: 'not_loaded',
    }
  }
  if (input.id && isUuid(input.id)) {
    return {
      label: '名称不可用',
      technicalId: input.id,
      unavailableReason: 'name_unavailable',
    }
  }
  if (!input.id && !code && !name) {
    return {
      label: '未提供',
      unavailableReason: 'not_provided',
    }
  }
  return {
    label: '名称不可用',
    technicalId: input.id ?? undefined,
    unavailableReason: 'name_unavailable',
  }
}

/** 缩短技术引用供诊断，不用于正式主文案。 */
export function shortTechnicalRef(id: string | null | undefined): string {
  if (!id) return ''
  if (!isUuid(id)) return id
  return `${id.slice(0, 8)}…`
}
