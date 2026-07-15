#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
work_dir="$(mktemp -d)"
trap 'rm -rf "${work_dir}"' EXIT

safe_file="${work_dir}/safe.jsonl"
unsafe_file="${work_dir}/unsafe.jsonl"
unsafe_vin_file="${work_dir}/unsafe-vin.jsonl"
printf '%s\n' \
  '{"message":"phone=[REDACTED] amount=[REDACTED]","model":"CompleteHumanTaskRequest","traceId":"4bf92f3577b34da6a3ce929d0e0e4736"}' \
  > "${safe_file}"
printf '%s\n' '{"message":"customer phone=13812345678"}' > "${unsafe_file}"
printf '%s\n' '{"message":"vin=LGXCE6CB1N0123456"}' > "${unsafe_vin_file}"

"${script_dir}/verify-sensitive-output.sh" "${safe_file}"
if "${script_dir}/verify-sensitive-output.sh" "${unsafe_file}" >/dev/null 2>&1; then
  echo "negative probe failed: unsafe fixture was accepted" >&2
  exit 1
fi
if "${script_dir}/verify-sensitive-output.sh" "${unsafe_vin_file}" >/dev/null 2>&1; then
  echo "negative probe failed: unsafe VIN fixture was accepted" >&2
  exit 1
fi

if "${script_dir}/verify-sensitive-output.sh" "${work_dir}/missing.jsonl" >/dev/null 2>&1; then
  echo "negative probe failed: missing input file was accepted" >&2
  exit 1
fi

echo "sensitive output gate safe, phone/VIN leak and missing-file probes passed"
