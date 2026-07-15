#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work_dir="$(mktemp -d)"
trap 'rm -rf "${work_dir}"' EXIT

test_root="${work_dir}/src/test/java"
staging_generator="${work_dir}/generate-local-env.sh"
mkdir -p "${test_root}"

read -r flyway_version migration_count < <(bash "${root}/scripts/migration-baseline.sh")

cat > "${staging_generator}" <<'EOF'
#!/usr/bin/env bash
SERVICEOS_EXPECTED_MIGRATION_VERSION=${migration_version}
SERVICEOS_EXPECTED_MIGRATION_COUNT=${migration_count}
EOF

cat > "${test_root}/CurrentMigrationIT.java" <<EOF
class CurrentMigrationIT {
    void verifies(org.flywaydb.core.Flyway flyway) {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("${flyway_version}");
        assertThat(flyway.info().applied()).hasSize(${migration_count});
    }
}
EOF

bash "${root}/scripts/check-migration-baseline-references.sh" \
  "${test_root}" "${staging_generator}" >/dev/null

version_width="${#flyway_version}"
version_number=$((10#${flyway_version}))
if (( version_number > 0 )); then
  printf -v stale_version "%0${version_width}d" "$((version_number - 1))"
else
  stale_version="999"
fi
stale_count=$((migration_count > 1 ? migration_count - 1 : migration_count + 1))

cat > "${test_root}/CurrentMigrationIT.java" <<EOF
class CurrentMigrationIT {
    void verifies(org.flywaydb.core.Flyway flyway) {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("${stale_version}");
        assertThat(flyway.info().applied()).hasSize(${stale_count});
    }
}
EOF

if bash "${root}/scripts/check-migration-baseline-references.sh" \
  "${test_root}" "${staging_generator}" >/dev/null 2>&1; then
  echo "negative probe failed: stale migration assertions were accepted" >&2
  exit 1
fi

cat > "${staging_generator}" <<'EOF'
#!/usr/bin/env bash
SERVICEOS_EXPECTED_MIGRATION_VERSION=001
SERVICEOS_EXPECTED_MIGRATION_COUNT=1
EOF
rm -f "${test_root}/CurrentMigrationIT.java"
if bash "${root}/scripts/check-migration-baseline-references.sh" \
  "${test_root}" "${staging_generator}" >/dev/null 2>&1; then
  echo "negative probe failed: hardcoded staging migration manifest was accepted" >&2
  exit 1
fi

printf 'verification preflight positive and negative probes passed\n'
