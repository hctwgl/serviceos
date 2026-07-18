#!/usr/bin/env bash
set -euo pipefail

script_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd "${script_directory}/../.." && pwd)"
module_directory="${repository_root}/serviceos-contracts"
contract_path="${module_directory}/src/main/resources/openapi/serviceos-core-v1.yaml"
generated_directory="${module_directory}/target/generated-clients/swift6"
artifact_directory="${module_directory}/target/client-artifacts/swift6"
use_existing="${1:-}"

if [[ "${use_existing}" != "--use-existing" ]]; then
  generation_log="${module_directory}/target/swift-client-generation.log"
  if ! "${repository_root}/mvnw" --batch-mode --no-transfer-progress \
    -pl serviceos-contracts openapi-generator:generate@generate-swift6-client >"${generation_log}" 2>&1; then
    tail -n 100 "${generation_log}" >&2
    exit 1
  fi
fi

if [[ ! -f "${generated_directory}/Package.swift" ]]; then
  echo "Generated Swift client is missing: ${generated_directory}" >&2
  exit 1
fi

rm -rf "${artifact_directory}"
mkdir -p "${artifact_directory}"

checksum() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

files_manifest="${artifact_directory}/files.sha256"
(
  cd "${generated_directory}"
  while IFS= read -r generated_file; do
    printf '%s  %s\n' "$(checksum "${generated_file}")" "${generated_file#./}"
  done < <(find . -type f -not -path './.build/*' -print | LC_ALL=C sort)
) > "${files_manifest}"

contract_sha256="$(checksum "${contract_path}")"
tree_sha256="$(checksum "${files_manifest}")"
generator_version="$(tr -d '[:space:]' < "${generated_directory}/.openapi-generator/VERSION")"

jq -n \
  --arg artifact "serviceos-swift6-client" \
  --arg generator "swift6" \
  --arg generatorVersion "${generator_version}" \
  --arg moduleName "ServiceOSCoreClient" \
  --arg contractPath "serviceos-contracts/src/main/resources/openapi/serviceos-core-v1.yaml" \
  --arg contractSha256 "${contract_sha256}" \
  --arg generatedTreeSha256 "${tree_sha256}" \
  '{
    artifact: $artifact,
    generator: $generator,
    generatorVersion: $generatorVersion,
    moduleName: $moduleName,
    contractPath: $contractPath,
    contractSha256: $contractSha256,
    generatedTreeSha256: $generatedTreeSha256
  }' > "${artifact_directory}/manifest.json"

echo "Generated Swift client: ${generated_directory}"
echo "Swift artifact manifest: ${artifact_directory}/manifest.json"
