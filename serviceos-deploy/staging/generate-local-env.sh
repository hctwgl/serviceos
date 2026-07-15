#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../.." && pwd)"
output_file="${1:-}"
image_ref="${2:-serviceos-backend:m17-local}"
if [[ -z "${output_file}" ]]; then
  echo "usage: $0 <output-env-file> [image-ref]" >&2
  exit 64
fi

read -r migration_version migration_count < <("${repo_root}/scripts/migration-baseline.sh")

umask 077
mkdir -p "$(dirname "${output_file}")"
random_secret() {
  openssl rand -hex 32
}

cat > "${output_file}" <<EOF
SERVICEOS_IMAGE=${image_ref}
SERVICEOS_ALLOW_MUTABLE_IMAGE=true
SERVICEOS_STAGING_PORT=18080
SERVICEOS_EXPECTED_MIGRATION_VERSION=${migration_version}
SERVICEOS_EXPECTED_MIGRATION_COUNT=${migration_count}
SERVICEOS_BOOTSTRAP_DB_PASSWORD=$(random_secret)
SERVICEOS_MIGRATOR_DB_PASSWORD=$(random_secret)
SERVICEOS_RUNTIME_DB_PASSWORD=$(random_secret)
SERVICEOS_FILE_SIGNING_KEY=$(random_secret)
EOF
chmod 600 "${output_file}"
echo "generated local staging environment: ${output_file}"
