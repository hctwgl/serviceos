import assert from 'node:assert/strict'
import test from 'node:test'

import {
  WebApiError,
  createContextSelectionStore,
  createMemoryAccessTokenStore,
  createWebApiClient,
  safeProblemMessage,
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
  assert.equal(result.diagnostics.traceId, 'trace-response')
  assert.equal(result.etag, '"3"')
})

test('problem detail is retained for diagnostics but user message is fixed', async () => {
  let authenticationRequired = 0
  const client = createWebApiClient({
    baseUrl: 'https://serviceos.invalid/api/v1',
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
