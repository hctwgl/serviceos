/**
 * 从 FORM 定义 JSON 提取 stage 与 fieldKey，供 EVIDENCE requiredWhen 积木发现 formValues 路径。
 */

export type FormFieldKeyExtraction = {
  stage: string | null
  fieldKeys: string[]
  assetKey: string | null
}

export function extractFormFieldKeys(
  definitionJson: string,
  assetKey: string | null = null,
): FormFieldKeyExtraction {
  try {
    const definition = JSON.parse(definitionJson) as Record<string, unknown>
    const stage = typeof definition.stage === 'string' ? definition.stage : null
    const keys: string[] = []
    const sections = definition.sections
    if (Array.isArray(sections)) {
      for (const section of sections) {
        if (!section || typeof section !== 'object') continue
        const fields = (section as Record<string, unknown>).fields
        if (!Array.isArray(fields)) continue
        for (const field of fields) {
          if (!field || typeof field !== 'object') continue
          const fieldKey = (field as Record<string, unknown>).fieldKey
          if (typeof fieldKey === 'string' && fieldKey.trim()) {
            keys.push(fieldKey.trim())
          }
        }
      }
    }
    return { stage, fieldKeys: uniquePreserveOrder(keys), assetKey }
  } catch {
    return { stage: null, fieldKeys: [], assetKey }
  }
}

export type DiscoveredFormFieldKeys = {
  fieldKeys: string[]
  /** 命中的 FORM 草稿 assetKey 列表（同 stage） */
  sourceAssetKeys: string[]
}

/**
 * 在 FORM 草稿列表中按 stage 匹配，合并 fieldKey（去重保序）。
 * 多 FORM 同 stage 时全部纳入，失败关闭不猜测“权威”草稿。
 */
export function discoverFormFieldKeysForStage(
  evidenceStage: string | null | undefined,
  formDrafts: ReadonlyArray<{ assetKey: string; definitionJson: string; status?: string }>,
): DiscoveredFormFieldKeys {
  if (!evidenceStage || !evidenceStage.trim()) {
    return { fieldKeys: [], sourceAssetKeys: [] }
  }
  const stage = evidenceStage.trim()
  const keys: string[] = []
  const sources: string[] = []
  for (const draft of formDrafts) {
    if (draft.status === 'DISCARDED') continue
    const extracted = extractFormFieldKeys(draft.definitionJson, draft.assetKey)
    if (extracted.stage !== stage) continue
    if (extracted.fieldKeys.length === 0) continue
    sources.push(draft.assetKey)
    for (const key of extracted.fieldKeys) {
      keys.push(key)
    }
  }
  return {
    fieldKeys: uniquePreserveOrder(keys),
    sourceAssetKeys: uniquePreserveOrder(sources),
  }
}

function uniquePreserveOrder(values: string[]): string[] {
  const seen = new Set<string>()
  const out: string[] = []
  for (const value of values) {
    if (seen.has(value)) continue
    seen.add(value)
    out.push(value)
  }
  return out
}
