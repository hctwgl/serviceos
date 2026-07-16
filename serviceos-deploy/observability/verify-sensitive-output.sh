#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -eq 0 ]]; then
  echo "usage: $0 <log-or-trace-file> [...]" >&2
  exit 2
fi

# 门禁必须 fail closed：缺工具时不能把“扫不了”当成安全（if rg 会把 command-not-found 当成未命中）。
if ! command -v rg >/dev/null 2>&1; then
  echo "sensitive output gate failed: ripgrep (rg) is required" >&2
  exit 2
fi
if ! rg --pcre2 -q 'x' <<<'x' 2>/dev/null; then
  echo "sensitive output gate failed: ripgrep with --pcre2 support is required" >&2
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
  # 非结构化 VIN 至少含一个数字，避免把 OpenAPI 生成的 17 字符类型名片段误判为 VIN；
  # 极端的全字母 VIN 仍由下一条带字段名的规则失败关闭。
  '(?i)(?<![A-HJ-NPR-Z0-9])(?=[A-HJ-NPR-Z0-9]{17}(?![A-HJ-NPR-Z0-9]))(?=[A-HJ-NPR-Z0-9]*[0-9])[A-HJ-NPR-Z0-9]{17}'
  '(?i)(vin|vehicleIdentificationNumber)\s*[:=]\s*(?!\[REDACTED\])[A-HJ-NPR-Z0-9]{17}'
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
