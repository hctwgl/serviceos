#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
migration_root="${root}/serviceos-backend/src/main/resources/db/migration"
versioned_pattern='^V([0-9]+)__.+\.sql$'

fail() {
  echo "迁移基线解析失败: $1" >&2
  exit 1
}

[[ -d "${migration_root}" ]] || fail "迁移目录不存在: ${migration_root}"

versioned_files=()
while IFS= read -r file; do
  versioned_files[${#versioned_files[@]}]="${file}"
done < <(find "${migration_root}" -type f -name 'V*.sql' -print | sort)

repeatable_files=()
while IFS= read -r file; do
  repeatable_files[${#repeatable_files[@]}]="${file}"
done < <(find "${migration_root}" -type f -name 'R*.sql' -print | sort)

[[ "${#versioned_files[@]}" -gt 0 ]] || fail "没有发现版本化 Flyway 迁移"

seen_versions="|"
latest_version=""
latest_normalized=-1
for file in "${versioned_files[@]}"; do
  name="$(basename "${file}")"
  if [[ ! "${name}" =~ ${versioned_pattern} ]]; then
    fail "版本化迁移文件名不合法: ${name}"
  fi
  version="${BASH_REMATCH[1]}"
  normalized=$((10#${version}))
  if [[ "${seen_versions}" == *"|${normalized}|"* ]]; then
    fail "Flyway 版本重复: ${version}"
  fi
  seen_versions="${seen_versions}${normalized}|"
  if [[ "${normalized}" -gt "${latest_normalized}" ]]; then
    latest_normalized="${normalized}"
    latest_version="${version}"
  fi
done

migration_count=$(( ${#versioned_files[@]} + ${#repeatable_files[@]} ))

case "${1:-}" in
  "")
    printf '%s %s\n' "${latest_version}" "${migration_count}"
    ;;
  --shell)
    printf 'SERVICEOS_EXPECTED_MIGRATION_VERSION=%s\n' "${latest_version}"
    printf 'SERVICEOS_EXPECTED_MIGRATION_COUNT=%s\n' "${migration_count}"
    ;;
  --version)
    printf '%s\n' "${latest_version}"
    ;;
  --count)
    printf '%s\n' "${migration_count}"
    ;;
  *)
    echo "用法: $0 [--shell|--version|--count]" >&2
    exit 2
    ;;
esac
