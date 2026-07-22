#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
app_directory="${repository_root}/serviceos-technician-web"

if rg -n 'localStorage.*(?:token|Token)|(?:token|Token).*localStorage' "${app_directory}/src"; then
  echo "Technician Web 禁止把 Token 写入 localStorage。" >&2
  exit 1
fi

if rg -n -i '\b(price|pricing|settlement|quote|quotedAmount|serviceFee)\b' \
  "${app_directory}/src/api/technicianPortal.ts" "${app_directory}/src/pages"; then
  echo "Technician Web 正式投影不得暴露价格或结算字段。" >&2
  exit 1
fi

if rg -n "path: '/technician-portal|from './pages/TechnicianPortal" \
  "${repository_root}/serviceos-admin-web/src/router.ts"; then
  echo "Admin Web 禁止恢复正式 Technician Portal 路由。" >&2
  exit 1
fi
if find "${repository_root}/serviceos-admin-web/src/pages" -maxdepth 1 -name 'TechnicianPortal*.vue' -print -quit | grep -q . \
  || [[ -f "${repository_root}/serviceos-admin-web/src/api/technicianPortal.ts" ]]; then
  echo "Admin Web 中仍残留正式 Technician Portal 页面或业务 API。" >&2
  exit 1
fi

(
  cd "${app_directory}"
  npm ci --ignore-scripts --no-audit --no-fund --silent
  npm run build
)

probe_directory="$(mktemp -d "${TMPDIR:-/tmp}/serviceos-technician-env.XXXXXX")"
cleanup() {
  rm -rf "${probe_directory}"
}
trap cleanup EXIT

"${app_directory}/node_modules/.bin/tsc" \
  --target ES2022 --module NodeNext --moduleResolution NodeNext --strict \
  --outDir "${probe_directory}" "${app_directory}/src/environment.ts"
node --input-type=module - "${probe_directory}/environment.js" <<'NODE'
const modulePath = process.argv[2]
const { resolveTechnicianEnvironment } = await import(`file://${modulePath}`)
const staging = resolveTechnicianEnvironment({ mode: 'staging', apiBaseUrl: 'https://api.example', clientVersion: '1.2.3+45' })
if (staging.clientKind !== 'TECHNICIAN_WEB' || staging.name !== 'staging') process.exit(1)
for (const invalid of [
  { mode: 'preview', clientVersion: '1.0.0' },
  { mode: 'production', apiBaseUrl: 'http://api.example', clientVersion: '1.0.0' },
  { mode: 'production', clientVersion: 'latest' },
]) {
  let rejected = false
  try { resolveTechnicianEnvironment(invalid) } catch { rejected = true }
  if (!rejected) process.exit(1)
}
NODE

"${app_directory}/node_modules/.bin/tsc" --target ES2022 --module NodeNext --moduleResolution NodeNext --strict \
  --outDir "${probe_directory}" "${app_directory}/src/technicianSession.ts"
node --input-type=module - "${probe_directory}/technicianSession.js" <<'NODE'
const { loadTechnicianSession } = await import(`file://${process.argv[2]}`)
const calls = []
const api = { request: async ({ path }) => {
  calls.push(path)
  if (path === '/me/contexts') return { data: { contextVersion: 'v7', contexts: [
    { contextId: 'admin', portal: 'ADMIN', scopeRef: 'tenant', version: 'v7' },
    { contextId: 'technician-1', portal: 'TECHNICIAN', scopeRef: 'technician', version: 'v7' },
  ] } }
  if (path.startsWith('/me/capabilities')) return { data: { portal: 'TECHNICIAN', contextVersion: 'v7', capabilityCodes: ['task.readAssigned'] } }
  return { data: { portal: 'TECHNICIAN', contextVersion: 'v7', items: [{ pageId: 'TECHNICIAN.TASK.LIST', routeKey: 'task-feed', title: '任务 Feed', order: 10, section: '任务', requiredCapabilities: [] }] } }
} }
const session = await loadTechnicianSession(api)
if (session.activeContextId !== 'technician-1' || session.navigation.length !== 1 || calls.length !== 3) process.exit(1)
let rejected = false
try { await loadTechnicianSession(api, 'forged') } catch { rejected = true }
if (!rejected || calls.length !== 4) process.exit(1)
NODE

test -f "${app_directory}/dist/index.html"
test -f "${app_directory}/Dockerfile"
test -f "${app_directory}/nginx/default.conf.template"
rg -q 'location = /healthz' "${app_directory}/nginx/default.conf.template"
rg -q 'try_files \$uri \$uri/ /index.html' "${app_directory}/nginx/default.conf.template"
rg -q 'root /usr/share/nginx/html' "${app_directory}/nginx/default.conf.template"
rg -q 'serviceos-technician-web/node_modules/\*\*' "${app_directory}/Dockerfile.dockerignore"
rg -q '不承诺原生级定位、后台上传、杀进程恢复或完整离线可靠性' "${app_directory}/src/App.vue"
echo "Technician Web 独立构建门禁通过。"
