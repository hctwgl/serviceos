#!/usr/bin/env bash
set -euo pipefail

# 从生成式索引只返回命中的里程碑行与直接文档路径，避免 Agent 为定位任务
# 把整个 milestone-index.md 读入上下文。查询参数按 ripgrep 正则解释。

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
index_file="${root}/serviceos-architecture/docs/milestone-index.md"

if [[ "$#" -eq 0 ]]; then
  echo "用法: $0 <里程碑号|标题关键词|正则>" >&2
  exit 2
fi
[[ -f "${index_file}" ]] || {
  echo "里程碑索引不存在，请先运行 bash scripts/generate-milestone-index.sh。" >&2
  exit 2
}

query="$*"
set +e
raw_matches="$(rg -i --no-heading --color never -- "${query}" "${index_file}")"
rg_status=$?
set -e
if [[ "${rg_status}" -gt 1 ]]; then
  echo "里程碑查询正则无效：${query}" >&2
  exit 2
fi

matches="$(printf '%s\n' "${raw_matches}" | awk '/^\| M[0-9]+ \|/')"
if [[ -z "${matches}" ]]; then
  echo "没有找到匹配的里程碑：${query}" >&2
  exit 1
fi

printf '%s\n' "${matches}"
echo
echo "直接文档路径："
printf '%s\n' "${matches}" \
  | grep -oE '\]\(\.\./[^)]+\)' \
  | sed -e 's|^](\.\./|serviceos-architecture/|' -e 's|)$||' \
  | LC_ALL=C sort -u
