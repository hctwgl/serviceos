/**
 * 运行：node serviceos-admin-web/src/expression/extractFormFieldKeys.test.mjs
 */
import assert from 'node:assert/strict'
import { pathToFileURL } from 'node:url'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))

let api
try {
  api = await import(pathToFileURL(join(__dirname, 'extractFormFieldKeys.ts')).href)
} catch {
  // strip-types 不可用时内联最小实现
  function uniquePreserveOrder(values) {
    const seen = new Set()
    const out = []
    for (const value of values) {
      if (seen.has(value)) continue
      seen.add(value)
      out.push(value)
    }
    return out
  }
  api = {
    extractFormFieldKeys(definitionJson, assetKey = null) {
      try {
        const definition = JSON.parse(definitionJson)
        const stage = typeof definition.stage === 'string' ? definition.stage : null
        const keys = []
        for (const section of definition.sections || []) {
          for (const field of section.fields || []) {
            if (typeof field.fieldKey === 'string' && field.fieldKey.trim()) {
              keys.push(field.fieldKey.trim())
            }
          }
        }
        return { stage, fieldKeys: uniquePreserveOrder(keys), assetKey }
      } catch {
        return { stage: null, fieldKeys: [], assetKey }
      }
    },
    discoverFormFieldKeysForStage(evidenceStage, formDrafts) {
      if (!evidenceStage?.trim()) return { fieldKeys: [], sourceAssetKeys: [] }
      const stage = evidenceStage.trim()
      const keys = []
      const sources = []
      for (const draft of formDrafts) {
        if (draft.status === 'DISCARDED') continue
        const extracted = api.extractFormFieldKeys(draft.definitionJson, draft.assetKey)
        if (extracted.stage !== stage || !extracted.fieldKeys.length) continue
        sources.push(draft.assetKey)
        keys.push(...extracted.fieldKeys)
      }
      return { fieldKeys: uniquePreserveOrder(keys), sourceAssetKeys: uniquePreserveOrder(sources) }
    },
  }
}

const formJson = JSON.stringify({
  formKey: 'survey',
  version: '1.0.0',
  stage: 'SURVEY',
  sections: [
    {
      sectionKey: 'site',
      title: '现场',
      fields: [
        { fieldKey: 'needs-photo', label: '需拍照', dataType: 'BOOLEAN', binding: 'task.input.a' },
        { fieldKey: 'site.has-parking-space', label: '停车位', dataType: 'BOOLEAN', binding: 'task.input.b' },
      ],
    },
  ],
})

const extracted = api.extractFormFieldKeys(formJson, 'survey.form')
assert.equal(extracted.stage, 'SURVEY')
assert.deepEqual(extracted.fieldKeys, ['needs-photo', 'site.has-parking-space'])

const discovered = api.discoverFormFieldKeysForStage('SURVEY', [
  { assetKey: 'survey.form', definitionJson: formJson, status: 'DRAFT' },
  {
    assetKey: 'other.stage',
    definitionJson: JSON.stringify({
      stage: 'INSTALLATION',
      sections: [{ fields: [{ fieldKey: 'ignored' }] }],
    }),
    status: 'DRAFT',
  },
  {
    assetKey: 'discarded.form',
    definitionJson: formJson,
    status: 'DISCARDED',
  },
])
assert.deepEqual(discovered.fieldKeys, ['needs-photo', 'site.has-parking-space'])
assert.deepEqual(discovered.sourceAssetKeys, ['survey.form'])

const empty = api.discoverFormFieldKeysForStage('REVIEW', [
  { assetKey: 'survey.form', definitionJson: formJson, status: 'DRAFT' },
])
assert.deepEqual(empty.fieldKeys, [])
assert.deepEqual(empty.sourceAssetKeys, [])

console.log('extractFormFieldKeys.test.mjs OK')
