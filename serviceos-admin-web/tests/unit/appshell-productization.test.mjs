import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '../..')
const shell = readFileSync(join(root, 'src/pages/AppShell.vue'), 'utf8')

test('AppShell brand is productized without technical sidebar hint', () => {
  assert.match(shell, /运营管理平台/)
  assert.match(shell, /data-testid="app-shell"/)
  assert.match(shell, /data-testid="app-header"/)
  assert.match(shell, /<ScopeBar/)
  assert.match(shell, /data-testid="user-menu-trigger"/)
  assert.match(shell, /data-testid="global-search-entry"/)
  assert.doesNotMatch(shell, /菜单可见性来自服务端/)
  assert.doesNotMatch(shell, /写操作仍由 Capability 校验/)
  const scopeBar = readFileSync(join(root, 'src/patterns/ScopeBar.vue'), 'utf8')
  assert.match(scopeBar, /data-testid="scope-bar"/)
})

test('AppShell keeps server navigation test ids', () => {
  assert.match(shell, /nav-users/)
  assert.match(shell, /nav-portal-stubs/)
  assert.match(shell, /recent-resources/)
  assert.match(shell, /portal-context-select/)
})
