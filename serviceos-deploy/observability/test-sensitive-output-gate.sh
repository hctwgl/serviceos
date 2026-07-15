#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
work_dir="$(mktemp -d)"
trap 'rm -rf "${work_dir}"' EXIT

safe_file="${work_dir}/safe.jsonl"
unsafe_phone_file="${work_dir}/unsafe-phone.jsonl"
unsafe_vin_file="${work_dir}/unsafe-vin.jsonl"

# 生成客户端的类名可能包含连续 17 个 VIN 字符集字母，但第 9 位不是合法校验位，必须视为普通文件名。
printf '%s\n' '{"message":"phone=[REDACTED] amount=[REDACTED] generated=CompleteHumanTaskRequest.ts","traceId":"4bf92f3577b34da6a3ce929d0e0e4736"}' > "${safe_file}"
printf '%s\n' '{"message":"customer phone=13812345678"}' > "${unsafe_phone_file}"
printf '%s\n' '{"message":"vehicle vin=1HGCM82633A004352"}' > "${unsafe_vin_file}"

"${script_dir}/verify-sensitive-output.sh" "${safe_file}"
for unsafe_file in "${unsafe_phone_file}" "${unsafe_vin_file}"; do
  if "${script_dir}/verify-sensitive-output.sh" "${unsafe_file}" >/dev/null 2>&1; then
    echo "negative probe failed: unsafe fixture was accepted: ${unsafe_file}" >&2
    exit 1
  fi
done

if "${script_dir}/verify-sensitive-output.sh" "${work_dir}/missing.jsonl" >/dev/null 2>&1; then
  echo "negative probe failed: missing input file was accepted" >&2
  exit 1
fi

echo "sensitive output gate safe, phone/VIN leak and missing-file probes passed"
