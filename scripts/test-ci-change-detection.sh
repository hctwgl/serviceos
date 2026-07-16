#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
detector="${repo_root}/scripts/detect-ci-changes.sh"
workflow="${repo_root}/.github/workflows/verify.yml"

assert_case() {
  local name="$1"
  local paths="$2"
  local expected="$3"
  local output
  output="$(mktemp)"
  trap 'rm -f "${output}"' RETURN

  printf '%s\n' "${paths}" | GITHUB_OUTPUT="${output}" "${detector}"
  while IFS='=' read -r key value; do
    printf -v "actual_${key}" '%s' "${value}"
  done < "${output}"

  for pair in ${expected}; do
    local key="${pair%%=*}"
    local value="${pair#*=}"
    local actual_variable="actual_${key}"
    if [[ "${!actual_variable:-missing}" != "${value}" ]]; then
      echo "${name}: expected ${key}=${value}, got ${!actual_variable:-missing}" >&2
      exit 1
    fi
  done
}

assert_case docs $'README.md\nAGENTS.md\nserviceos-architecture/docs/example.md' \
  'docs_only=true admin=false backend=false contracts=false deploy=false pilot=false'
assert_case frontend 'serviceos-admin-web/src/App.vue' \
  'docs_only=false admin=true backend=false contracts=false deploy=false pilot=false'
assert_case backend 'serviceos-backend/src/main/java/example.java' \
  'admin=false backend=true contracts=false deploy=false pilot=false'
assert_case contracts 'serviceos-contracts/src/main/resources/openapi/serviceos.yaml' \
  'admin=false backend=false contracts=true deploy=false pilot=false'
assert_case deploy 'serviceos-deploy/compose.yaml' \
  'admin=false backend=false contracts=false deploy=true pilot=false'
assert_case pilot 'serviceos-deploy/admin-pilot/verify-admin-smoke.sh' \
  'admin=false backend=false contracts=false deploy=true pilot=true'
assert_case unknown 'some-new-module/file.txt' \
  'admin=true backend=true contracts=true deploy=true pilot=true'

full_output="$(mktemp)"
trap 'rm -f "${full_output}"' EXIT
GITHUB_OUTPUT="${full_output}" "${detector}" --full
for key in admin backend contracts deploy pilot; do
  grep -qx "${key}=true" "${full_output}" || {
    echo "full verification must enable ${key}" >&2
    exit 1
  }
done

ruby -e 'require "yaml"; YAML.parse_file(ARGV.fetch(0))' "${workflow}"

grep -q '^  push:$' "${workflow}"
grep -A3 '^  push:$' "${workflow}" | grep -q 'master'
grep -q '^  pull_request:$' "${workflow}"
grep -A3 '^  pull_request:$' "${workflow}" | grep -q 'master'
grep -q '^concurrency:$' "${workflow}"
grep -A3 '^concurrency:$' "${workflow}" | grep -q 'cancel-in-progress: true'
grep -A3 '^concurrency:$' "${workflow}" | grep -q 'github.head_ref || github.ref_name'
grep -A14 '^  container-staging:$' "${workflow}" | grep -q "github.event_name == 'workflow_dispatch'"
grep -A14 '^  container-staging:$' "${workflow}" | grep -q "github.event_name == 'push'"
if grep -A14 '^  container-staging:$' "${workflow}" | grep -q "github.event_name == 'pull_request'"; then
  echo "container-staging must not run for pull_request" >&2
  exit 1
fi

echo "CI change detection and workflow policy checks passed"
