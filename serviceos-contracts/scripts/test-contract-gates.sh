#!/usr/bin/env bash
set -euo pipefail

script_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd "${script_directory}/../.." && pwd)"
oasdiff_binary="${OASDIFF_BIN:-}"
if [[ -z "${oasdiff_binary}" || ! -x "${oasdiff_binary}" ]]; then
  echo "OASDIFF_BIN must point to the pinned executable before running gate tests." >&2
  exit 2
fi

temporary_repository="$(mktemp -d "${TMPDIR:-/tmp}/serviceos-contract-gate-test.XXXXXX")"
trap 'rm -rf "${temporary_repository}"' EXIT

mkdir -p \
  "${temporary_repository}/serviceos-contracts/src/main/resources/openapi" \
  "${temporary_repository}/serviceos-contracts/src/main/resources/events"
cp "${repository_root}/serviceos-contracts/src/main/resources/openapi/serviceos-core-v1.yaml" \
  "${temporary_repository}/serviceos-contracts/src/main/resources/openapi/serviceos-core-v1.yaml"
cp "${repository_root}/serviceos-contracts/src/main/resources/events/project-created-v1.schema.json" \
  "${temporary_repository}/serviceos-contracts/src/main/resources/events/project-created-v1.schema.json"

git -C "${temporary_repository}" init --quiet
git -C "${temporary_repository}" add serviceos-contracts
git -C "${temporary_repository}" \
  -c user.name=ServiceOS-CI -c user.email=ci@serviceos.invalid \
  commit --quiet -m baseline
base_ref="$(git -C "${temporary_repository}" rev-parse HEAD)"

run_gate() {
  CONTRACT_REPOSITORY_ROOT="${temporary_repository}" \
    OASDIFF_BIN="${oasdiff_binary}" \
    "${script_directory}/check-contract-compatibility.sh" "${base_ref}"
}

expect_gate_failure() {
  local scenario="$1"
  set +e
  run_gate > "${temporary_repository}/${scenario}.log" 2>&1
  local exit_code=$?
  set -e
  if [[ "${exit_code}" -eq 0 ]]; then
    echo "Expected contract gate failure did not occur: ${scenario}" >&2
    cat "${temporary_repository}/${scenario}.log" >&2
    exit 1
  fi
  echo "Negative gate passed: ${scenario} (exit=${exit_code})"
}

run_gate >/dev/null

openapi_file="${temporary_repository}/serviceos-contracts/src/main/resources/openapi/serviceos-core-v1.yaml"
perl -0pi -e 's/required: \[code, clientId, name, startsOn\]/required: [code, clientId, name, startsOn, endsOn]/' \
  "${openapi_file}"
expect_gate_failure "openapi-breaking-change"
git -C "${temporary_repository}" restore -- "serviceos-contracts/src/main/resources/openapi/serviceos-core-v1.yaml"

event_file="${temporary_repository}/serviceos-contracts/src/main/resources/events/project-created-v1.schema.json"
perl -0pi -e 's/ProjectCreatedV1/ProjectCreatedV1Changed/' "${event_file}"
expect_gate_failure "event-schema-in-place-change"
git -C "${temporary_repository}" restore -- "serviceos-contracts/src/main/resources/events/project-created-v1.schema.json"

event_v2_file="${temporary_repository}/serviceos-contracts/src/main/resources/events/project-created-v2.schema.json"
cp "${event_file}" "${event_v2_file}"
perl -0pi -e 's/project-created-v1\.schema\.json/project-created-v2.schema.json/g; s/ProjectCreatedV1/ProjectCreatedV2/g; s/"schemaVersion"\s*:\s*\{\s*"const"\s*:\s*1\s*\}/"schemaVersion": { "const": 2 }/g' \
  "${event_v2_file}"
run_gate >/dev/null
echo "Versioned event evolution gate passed: new v2 file is allowed."

echo "Contract gate positive and negative probes passed."
