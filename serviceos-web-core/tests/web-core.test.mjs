import assert from 'node:assert/strict'
import test from 'node:test'

import {
  WebApiError,
  createContextSelectionStore,
  createMemoryAccessTokenStore,
  createWebApiClient,
  fieldLabel,
  formatDateTime,
  safeProblemMessage,
  statusLabel,
} from '../dist/index.js'

test('memory token store expires fail-closed without browser persistence', () => {
  let now = 1_000
  const store = createMemoryAccessTokenStore(() => now, 0)
  store.set({ accessToken: 'secret-token', expiresAtEpochMs: 2_000 })
  assert.equal(store.current()?.accessToken, 'secret-token')
  now = 2_000
  assert.equal(store.current(), null)
})

test('context boundary change clears host cache without interpreting scope', () => {
  let clearCount = 0
  const store = createContextSelectionStore(() => { clearCount += 1 })
  store.select({ contextId: 'ctx-1', contextVersion: 'v1', scopeRef: 'opaque' })
  store.select({ contextId: 'ctx-1', contextVersion: 'v2', scopeRef: 'opaque' })
  assert.equal(clearCount, 1)
})

test('http client carries bearer, caller context and diagnostic identifiers', async () => {
  let captured
  const client = createWebApiClient({
    baseUrl: 'https://serviceos.invalid/api/v1',
    clientMetadata: { kind: 'TECHNICIAN_WEB', version: '1.2.3-test.1' },
    accessToken: () => ({ accessToken: 'token', expiresAtEpochMs: 9_999 }),
    createCorrelationId: () => 'correlation-request',
    fetch: async (input, init) => {
      captured = { input, headers: new Headers(init.headers) }
      return new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: {
          'Content-Type': 'application/json',
          'X-Correlation-Id': 'correlation-response',
          'X-Trace-Id': 'trace-response',
          ETag: '"3"',
        },
      })
    },
  })

  const result = await client.request({ path: '/me', headers: { 'X-Service-Context': 'ctx-1' } })
  assert.equal(captured.input, 'https://serviceos.invalid/api/v1/me')
  assert.equal(captured.headers.get('Authorization'), 'Bearer token')
  assert.equal(captured.headers.get('X-Service-Context'), 'ctx-1')
  assert.equal(captured.headers.get('X-Correlation-Id'), 'correlation-request')
  assert.equal(captured.headers.get('X-ServiceOS-Client-Kind'), 'TECHNICIAN_WEB')
  assert.equal(captured.headers.get('X-ServiceOS-Client-Version'), '1.2.3-test.1')
  assert.equal(result.diagnostics.traceId, 'trace-response')
  assert.equal(result.etag, '"3"')
})

test('problem detail is retained for diagnostics but user message is fixed', async () => {
  let authenticationRequired = 0
  const client = createWebApiClient({
    baseUrl: 'https://serviceos.invalid/api/v1',
    clientMetadata: { kind: 'ADMIN_WEB', version: '1.0.0' },
    accessToken: () => null,
    onAuthenticationRequired: () => { authenticationRequired += 1 },
    fetch: async () => new Response(JSON.stringify({
      title: 'Unauthorized',
      detail: 'sensitive backend detail',
      errorCode: 'AUTH_REQUIRED',
    }), { status: 401, headers: { 'X-Correlation-Id': 'corr-401' } }),
  })

  await assert.rejects(client.request({ path: '/me' }), (error) => {
    assert.ok(error instanceof WebApiError)
    assert.equal(error.problem.errorCode, 'AUTH_REQUIRED')
    assert.equal(error.diagnostics.correlationId, 'corr-401')
    assert.equal(safeProblemMessage(error), '需要重新登录')
    assert.ok(!safeProblemMessage(error).includes('sensitive'))
    return true
  })
  assert.equal(authenticationRequired, 1)
})

test('product status and field labels are Chinese for operators', () => {
  assert.equal(statusLabel('IN_PROGRESS'), '处理中')
  assert.equal(statusLabel('WAITING_REVIEW'), '待审核')
  assert.equal(statusLabel('BREACHED'), '已超时')
  assert.equal(fieldLabel('createdAt'), '创建时间')
  assert.equal(fieldLabel('projectId'), '所属项目')
})

test('formatDateTime renders local wall clock without ISO Z suffix', () => {
  const formatted = formatDateTime('2026-07-19T09:00:15.613824Z')
  assert.match(formatted, /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/)
  assert.ok(!formatted.includes('T'))
  assert.ok(!formatted.includes('Z'))
})
