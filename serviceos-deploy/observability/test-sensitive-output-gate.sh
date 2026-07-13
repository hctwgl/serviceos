#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
work_dir="$(mktemp -d)"
trap 'rm -rf "${work_dir}"' EXIT

safe_file="${work_dir}/safe.jsonl"
unsafe_file="${work_dir}/unsafe.jsonl"
printf '%s\n' '{"message":"phone=[REDACTED] amount=[REDACTED]","traceId":"4bf92f3577b34da6a3ce929d0e0e4736"}' > "${safe_file}"
printf '%s\n' '{"message":"customer phone=13812345678"}' > "${unsafe_file}"

"${script_dir}/verify-sensitive-output.sh" "${safe_file}"
if "${script_dir}/verify-sensitive-output.sh" "${unsafe_file}" >/dev/null 2>&1; then
  echo "negative probe failed: unsafe fixture was accepted" >&2
  exit 1
fi

if "${script_dir}/verify-sensitive-output.sh" "${work_dir}/missing.jsonl" >/dev/null 2>&1; then
  echo "negative probe failed: missing input file was accepted" >&2
  exit 1
fi

echo "sensitive output gate safe, leak and missing-file probes passed"
