#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -eq 0 ]]; then
  echo "usage: $0 <log-or-trace-file> [...]" >&2
  exit 2
fi

# 门禁必须 fail closed：路径拼错或 clean 删除日志时，不能把“没有扫描到内容”当成安全。
for input_file in "$@"; do
  if [[ ! -f "${input_file}" || ! -r "${input_file}" ]]; then
    echo "sensitive output gate failed: unreadable input file: ${input_file}" >&2
    exit 2
  fi
done

patterns=(
  '(?i)Bearer\s+(?!\[REDACTED\])[A-Za-z0-9._~+/=-]{8,}'
  '(?<![A-Za-z0-9_-])eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}(?![A-Za-z0-9_-])'
  '(?i)(authorization|access[_-]?token|refresh[_-]?token|id[_-]?token|password|client[_-]?secret|signature)\s*[:=]\s*(?!\[REDACTED\])[^,;[:space:]}]+'
  '(?<![0-9])1[3-9][0-9]{9}(?![0-9])'
  '(?i)(?<![A-HJ-NPR-Z0-9])[A-HJ-NPR-Z0-9]{17}(?![A-HJ-NPR-Z0-9])'
  '(?i)(address|customerAddress|installationAddress|用户地址|安装地址)\s*[:=]\s*(?!\[REDACTED\])[^,;}]+'
  '(?i)(price|amount|unitPrice|totalAmount|对上金额|对下金额|结算金额)\s*[:=]\s*(?!\[REDACTED\])-?[0-9]+([.][0-9]+)?'
)

for pattern in "${patterns[@]}"; do
  if rg --line-number --pcre2 --no-heading --color never "$pattern" "$@"; then
    echo "sensitive output gate failed: unredacted value found" >&2
    exit 1
  fi
done

echo "sensitive output gate passed: $# file(s) scanned"
