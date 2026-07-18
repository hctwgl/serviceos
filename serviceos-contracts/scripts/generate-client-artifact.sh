#!/usr/bin/env bash
set -euo pipefail

script_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd "${script_directory}/../.." && pwd)"
module_directory="${repository_root}/serviceos-contracts"
contract_path="${module_directory}/src/main/resources/openapi/serviceos-core-v1.yaml"
generated_directory="${module_directory}/target/generated-clients/typescript-fetch"
artifact_directory="${module_directory}/target/client-artifacts/typescript-fetch"
use_existing="${1:-}"

if [[ "${use_existing}" != "--use-existing" ]]; then
  rm -rf "${generated_directory}"
  "${repository_root}/mvnw" --batch-mode --no-transfer-progress \
    -pl serviceos-contracts openapi-generator:generate@generate-typescript-fetch-client
fi

if [[ ! -f "${generated_directory}/package.json" ]]; then
  echo "Generated TypeScript client is missing: ${generated_directory}" >&2
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
  done < <(find . -type f -print | LC_ALL=C sort)
) > "${files_manifest}"

contract_sha256="$(checksum "${contract_path}")"
tree_sha256="$(checksum "${files_manifest}")"
generator_version="$(tr -d '[:space:]' < "${generated_directory}/.openapi-generator/VERSION")"
package_name="$(jq -r '.name' "${generated_directory}/package.json")"
package_version="$(jq -r '.version' "${generated_directory}/package.json")"

jq -n \
  --arg artifact "serviceos-typescript-fetch-client" \
  --arg generator "typescript-fetch" \
  --arg generatorVersion "${generator_version}" \
  --arg packageName "${package_name}" \
  --arg packageVersion "${package_version}" \
  --arg contractPath "serviceos-contracts/src/main/resources/openapi/serviceos-core-v1.yaml" \
  --arg contractSha256 "${contract_sha256}" \
  --arg generatedTreeSha256 "${tree_sha256}" \
  '{
    artifact: $artifact,
    generator: $generator,
    generatorVersion: $generatorVersion,
    packageName: $packageName,
    packageVersion: $packageVersion,
    contractPath: $contractPath,
    contractSha256: $contractSha256,
    generatedTreeSha256: $generatedTreeSha256
  }' > "${artifact_directory}/manifest.json"

echo "Generated client: ${generated_directory}"
echo "Artifact manifest: ${artifact_directory}/manifest.json"
