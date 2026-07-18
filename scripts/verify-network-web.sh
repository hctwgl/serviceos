#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
app_directory="${repository_root}/serviceos-network-web"

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

test -f "${app_directory}/dist/index.html"
echo "Network Web 独立构建门禁通过。"
