import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const page = readFileSync(
  new URL('../../src/pages/WorkOrderWorkspacePage.vue', import.meta.url),
  'utf8',
)
const api = readFileSync(new URL('../../src/api/dispatch.ts', import.meta.url), 'utf8')

test('责任网点分配只消费服务端候选，不保留演示标识或双责任快捷入口', () => {
  assert.match(page, /getNetworkAssignmentCandidates/)
  assert.match(page, /networkCandidates\.value\?\.candidates/)
  assert.match(page, /candidate\.networkName/)
  assert.doesNotMatch(page, /DEMO_NETWORK_ID|DEMO_TECHNICIAN_PROFILE_ID/)
  assert.doesNotMatch(page, /跳过网点接单|确认双责任分配/)
  assert.doesNotMatch(page, /manualAssignServiceAssignments/)
  assert.doesNotMatch(api, /manualAssignServiceAssignments|service-assignments:manual-assign`/)
})

test('责任网点候选使用 Task 派生的专用查询契约', () => {
  assert.match(
    api,
    /`\/tasks\/\$\{taskId\}\/network-assignment-candidates`/,
  )
  assert.match(api, /networkName: string/)
  assert.match(api, /coverageSummary: string/)
  assert.match(api, /remainingCapacity: number/)
})
