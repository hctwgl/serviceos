#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
app_directory="${repository_root}/serviceos-network-web"

if rg -n 'localStorage.*(?:token|Token)|(?:token|Token).*localStorage' "${app_directory}/src"; then
  echo "Network Web 禁止把 Token 写入 localStorage。" >&2
  exit 1
fi

(
  cd "${app_directory}"
  npm ci --ignore-scripts --no-audit --no-fund --silent
  npm run build
)

probe_directory="$(mktemp -d "${TMPDIR:-/tmp}/serviceos-network-env.XXXXXX")"
cleanup() {
  rm -rf "${probe_directory}"
}
trap cleanup EXIT

"${app_directory}/node_modules/.bin/tsc" \
  --target ES2022 --module NodeNext --moduleResolution NodeNext --strict \
  --outDir "${probe_directory}" "${app_directory}/src/environment.ts"
node --input-type=module - "${probe_directory}/environment.js" <<'NODE'
const modulePath = process.argv[2]
const { resolveNetworkEnvironment } = await import(`file://${modulePath}`)
const staging = resolveNetworkEnvironment({ mode: 'staging', apiBaseUrl: 'https://api.example', clientVersion: '1.2.3+45' })
if (staging.clientKind !== 'NETWORK_WEB' || staging.name !== 'staging') process.exit(1)
for (const invalid of [
  { mode: 'preview', clientVersion: '1.0.0' },
  { mode: 'production', apiBaseUrl: 'http://api.example', clientVersion: '1.0.0' },
  { mode: 'production', clientVersion: 'latest' },
]) {
  let rejected = false
  try { resolveNetworkEnvironment(invalid) } catch { rejected = true }
  if (!rejected) process.exit(1)
}
NODE

"${app_directory}/node_modules/.bin/tsc" --target ES2022 --module NodeNext --moduleResolution NodeNext --strict \
  --outDir "${probe_directory}" "${app_directory}/src/networkSession.ts"
node --input-type=module - "${probe_directory}/networkSession.js" <<'NODE'
const { loadNetworkSession } = await import(`file://${process.argv[2]}`)
const calls = []
const api = { request: async ({ path }) => {
  calls.push(path)
  if (path === '/me/contexts') return { data: { contextVersion: 'v7', contexts: [
    { contextId: 'admin', portal: 'ADMIN', scopeRef: 'tenant', version: 'v7' },
    { contextId: 'network-1', portal: 'NETWORK', scopeRef: 'network', version: 'v7' },
  ] } }
  if (path.startsWith('/me/capabilities')) return { data: { portal: 'NETWORK', contextVersion: 'v7', capabilityCodes: ['networkTask.read'] } }
  return { data: { portal: 'NETWORK', contextVersion: 'v7', items: [{ pageId: 'NETWORK.WORKBENCH', routeKey: 'workbench', title: '工作台', order: 10, section: '核心', requiredCapabilities: [] }] } }
} }
const session = await loadNetworkSession(api)
if (session.activeContextId !== 'network-1' || session.navigation.length !== 1 || calls.length !== 3) process.exit(1)
let rejected = false
try { await loadNetworkSession(api, 'forged') } catch { rejected = true }
if (!rejected || calls.length !== 4) process.exit(1)
NODE

test -f "${app_directory}/dist/index.html"
echo "Network Web 独立构建门禁通过。"
