import assert from 'node:assert/strict'
import { readFileSync, readdirSync } from 'node:fs'
import { join } from 'node:path'
import test from 'node:test'

const pagesDir = new URL('../../src/pages/', import.meta.url)
const componentsDir = new URL('../../src/components/fulfillment/', import.meta.url)

function read(pathUrl) {
  return readFileSync(pathUrl, 'utf8')
}

test('fulfillment product pages do not render manifestJson as primary UI', () => {
  const files = [
    'FulfillmentPublishFlowPage.vue',
    'FulfillmentPreviewPage.vue',
    'FulfillmentProfileDetailPage.vue',
    'FulfillmentProfileListPage.vue',
  ]
  for (const file of files) {
    const source = read(new URL(file, pagesDir))
    assert.doesNotMatch(
      source,
      /<pre[^>]*>\{\{\s*manifest\?\.manifestJson/,
      `${file} must not render manifestJson in a pre block`,
    )
    assert.match(
      source,
      /FulfillmentRunbookTable|ConfigurationWorkspaceLayout|FulfillmentCompareImpactPanel/,
      `${file} should use productized fulfillment components`,
    )
  }
})

test('runbook table does not parse Manifest JSON', () => {
  const source = read(new URL('FulfillmentRunbookTable.vue', componentsDir))
  assert.doesNotMatch(source, /JSON\.parse/)
  assert.doesNotMatch(source, /manifestJson/)
  assert.match(source, /runbook/)
})

test('list page no longer hardcodes one-click standard create', () => {
  const source = read(new URL('FulfillmentProfileListPage.vue', pagesDir))
  assert.doesNotMatch(source, /createProjectFulfillmentProfile\(/)
  assert.doesNotMatch(source, /profileName:\s*'标准家充履约方案'/)
  assert.match(source, /ADMIN\.PROJECT\.FULFILLMENT\.CREATE/)
})

test('create wizard page exists with product start modes', () => {
  const source = read(new URL('FulfillmentProfileCreatePage.vue', pagesDir))
  assert.match(source, /STANDARD/)
  assert.match(source, /COPY/)
  assert.match(source, /BLANK/)
  assert.match(source, /createProjectFulfillmentProfile/)
})
