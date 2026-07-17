#!/usr/bin/env bash
set -euo pipefail

# 生成 serviceos-architecture/docs/milestone-index.md。
#
# 索引一行一个里程碑，供 Agent 用 Grep 按里程碑号、模块名或关键词定位
# 「实现文档 + 验收矩阵」，避免批量通读 architecture/ 与 testing/ 目录。
#
# 里程碑归属按以下优先级判定：
#   1. 文档 frontmatter 中的 `milestone: Mxx`；
#   2. 文件名中的 `-mN-` 片段（如 46-m33-...）；
#   3. frontmatter `title:` 或首个 Markdown 标题开头的 Mxx 词元。
# 无法归属的文档列入末尾附录，不静默丢弃。
# 同一里程碑、同一侧出现多个候选文档时直接失败，禁止以排序结果静默覆盖权威文档。
#
# 只使用 POSIX 工具与 LC_ALL=C 排序，保证 macOS 与 Linux CI 输出一致。

root="${SERVICEOS_REPOSITORY_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
cd "${root}"

output="serviceos-architecture/docs/milestone-index.md"
if [[ "${1:-}" == "--stdout" ]]; then
  output="/dev/stdout"
fi

work_dir="$(mktemp -d "${TMPDIR:-/tmp}/serviceos-milestone-index.XXXXXX")"
trap 'rm -rf "${work_dir}"' EXIT

rows="${work_dir}/rows"
orphans="${work_dir}/orphans"
: > "${rows}"
: > "${orphans}"

# 提取 frontmatter 中某个标量字段（只解析第一个 --- 块）。
frontmatter_field() {
  local file="$1" field="$2"
  awk -v field="${field}" '
    NR == 1 && $0 != "---" { exit }
    /^---$/ { n++; next }
    n == 1 && index($0, field ":") == 1 {
      sub(/^[^:]*:[[:space:]]*/, "")
      gsub(/^"|"$/, "")
      print
      exit
    }
  ' "${file}"
}

# 首个 Markdown 标题文本。
first_heading() {
  sed -n 's/^#*[[:space:]]*//p' "$1" | head -1
}

# 仅接受标题开头的 Mxx，避免“验收基线（含 M164）”等聚合文档被误归为单一里程碑。
leading_milestone_token() {
  # 单里程碑标题统一为“Mxx + 空白 + 标题”；“M135～M140”这类程序级范围不得抢占 M135。
  grep -E '^M[0-9]+[[:space:]]' | grep -oE '^M[0-9]+' | head -1 || true
}

collect() {
  local dir="$1" side="$2"
  local file base milestone title num pad
  for file in "${dir}"/*.md; do
    [[ -e "${file}" ]] || continue
    base="$(basename "${file}")"
    milestone="$(frontmatter_field "${file}" milestone || true)"
    title="$(frontmatter_field "${file}" title || true)"
    if [[ -z "${title}" ]]; then
      title="$(first_heading "${file}")"
    fi
    if [[ -z "${milestone}" ]]; then
      milestone="$(printf '%s' "${base}" | sed -n 's/.*-m\([0-9][0-9]*\)-.*/M\1/p' | head -1)"
    fi
    if [[ -z "${milestone}" ]]; then
      milestone="$(printf '%s\n%s\n' "${title}" "$(first_heading "${file}")" | leading_milestone_token)"
    fi
    if [[ -z "${milestone}" ]]; then
      printf '%s\n' "${file}" >> "${orphans}"
      continue
    fi
    num="$((10#${milestone#M}))"
    pad="$(printf '%04d' "${num}")"
    if [[ "${side}" == "architecture" ]]; then
      printf '%s\t%s\t%s\t%s\t%s\n' "${pad}" "${milestone}" "${title}" "${file}" "" >> "${rows}"
    else
      printf '%s\t%s\t%s\t%s\t%s\n' "${pad}" "${milestone}" "${title}" "" "${file}" >> "${rows}"
    fi
  done
}

collect "serviceos-architecture/architecture" "architecture"
collect "serviceos-architecture/testing" "testing"

# 一个里程碑可以有一份实现文档和一份验收矩阵，但同一侧不得有多个候选。
# 发生歧义时失败关闭，让维护者补充或纠正 frontmatter，而不是依赖路径排序挑选“最后一个”。
duplicate_candidates="$(awk -F'\t' '
  {
    side = ($4 != "") ? "architecture" : "testing"
    key = $2 SUBSEP side
    count[key]++
    paths[key] = paths[key] (paths[key] == "" ? "" : ", ") (($4 != "") ? $4 : $5)
  }
  END {
    for (key in count) {
      if (count[key] > 1) {
        split(key, parts, SUBSEP)
        printf "%s %s: %s\n", parts[1], parts[2], paths[key]
      }
    }
  }
' "${rows}" | LC_ALL=C sort)"
if [[ -n "${duplicate_candidates}" ]]; then
  echo "里程碑索引生成失败：同一里程碑存在多个同类候选文档：" >&2
  printf '%s\n' "${duplicate_candidates}" >&2
  exit 1
fi

{
cat <<'HEADER'
---
title: ServiceOS 里程碑索引
---

# ServiceOS 里程碑索引

本文件由 `scripts/generate-milestone-index.sh` 生成，禁止手工修改；内容变化请修改源文档 frontmatter 后重新生成。

用途：一行一个里程碑。Agent 用 Grep 按里程碑号、模块名或关键词定位「实现文档 + 验收矩阵」，
配合 [agent-navigation.md](agent-navigation.md) 的任务路由确定最小阅读集，不批量通读 `architecture/` 目录。

| 里程碑 | 标题 | 实现文档 | 验收矩阵 |
|---|---|---|---|
HEADER

# 按里程碑号（补零）排序，同一里程碑的 architecture/testing 行合并后直接输出 Markdown；
# 不经 shell read 中转，避免连续制表符被 IFS 折叠。
LC_ALL=C sort -t$'\t' -k1,1 -k4,4 -k5,5 "${rows}" | awk -F'\t' '
  function basename(p) { sub(/.*\//, "", p); return p }
  function relpath(p) { sub(/^serviceos-architecture\//, "", p); return p }
  function emit() {
    if (current == "") return
    arch_cell = (arch != "") ? "[" basename(arch) "](../" relpath(arch) ")" : ""
    test_cell = (test != "") ? "[" basename(test) "](../" relpath(test) ")" : ""
    printf "| %s | %s | %s | %s |\n", current, title, arch_cell, test_cell
  }
  {
    if ($2 != current) { emit(); current = $2; title = ""; arch = ""; test = "" }
    if ($4 != "") { arch = $4; if ($3 != "") title = $3 }
    if ($5 != "") { test = $5; if ($3 != "" && title == "") title = $3 }
  }
  END { emit() }
'

cat <<'ORPHAN_HEADER'

## 未关联里程碑的文档

以下文档未声明里程碑归属（总体设计、工程规范或程序级文档），按路径列出供检索：

ORPHAN_HEADER
LC_ALL=C sort "${orphans}" | while IFS= read -r orphan; do
  [[ -z "${orphan}" ]] && continue
  printf -- '- `%s`\n' "${orphan}"
done
} > "${work_dir}/index.md"

if [[ "${output}" == "/dev/stdout" ]]; then
  cat "${work_dir}/index.md"
else
  mv "${work_dir}/index.md" "${output}"
  echo "已生成 ${output}"
fi
