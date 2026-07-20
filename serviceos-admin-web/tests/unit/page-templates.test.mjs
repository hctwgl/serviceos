import assert from 'node:assert/strict'
import test from 'node:test'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '../..')

const templates = [
  'ListPageLayout.vue',
  'DetailPageLayout.vue',
  'WorkbenchPageLayout.vue',
  'FormPageLayout.vue',
  'ConfigurationPageLayout.vue',
  'DedicatedFlowLayout.vue',
]

test('standard page templates exist', () => {
  for (const name of templates) {
    assert.equal(existsSync(join(root, 'src/patterns/templates', name)), true, name)
  }
})

test('SavedViewBar uses modal save/share and keeps test ids', () => {
  const source = readFileSync(join(root, 'src/components/SavedViewBar.vue'), 'utf8')
  assert.match(source, /saved-view-save-modal/)
  assert.match(source, /saved-view-share-modal/)
  assert.match(source, /saved-view-picker/)
  assert.match(source, /saved-view-name/)
})
