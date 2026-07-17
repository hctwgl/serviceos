#!/usr/bin/env bash
set -euo pipefail

# 对索引生成器做最小回归：聚合标题不得抢占具体里程碑，重复权威候选必须失败关闭。
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/serviceos-milestone-index-test.XXXXXX")"
trap 'rm -rf "${fixture_root}"' EXIT

mkdir -p \
  "${fixture_root}/scripts" \
  "${fixture_root}/serviceos-architecture/architecture" \
  "${fixture_root}/serviceos-architecture/testing" \
  "${fixture_root}/serviceos-architecture/docs"
cp "${root}/scripts/generate-milestone-index.sh" "${fixture_root}/scripts/"

cat > "${fixture_root}/serviceos-architecture/architecture/01-m164-example.md" <<'EOF'
---
title: M164 示例实现
milestone: M164
---
EOF
cat > "${fixture_root}/serviceos-architecture/testing/01-m164-example-acceptance.md" <<'EOF'
---
title: M164 示例验收
milestone: M164
---
EOF
cat > "${fixture_root}/serviceos-architecture/testing/program-acceptance.md" <<'EOF'
---
title: M164～M169 程序级验收基线
---
EOF

SERVICEOS_REPOSITORY_ROOT="${fixture_root}" \
  bash "${fixture_root}/scripts/generate-milestone-index.sh"
index_file="${fixture_root}/serviceos-architecture/docs/milestone-index.md"
grep -Fq '[01-m164-example-acceptance.md](../testing/01-m164-example-acceptance.md)' "${index_file}"
grep -Fq -- '- `serviceos-architecture/testing/program-acceptance.md`' "${index_file}"

cat > "${fixture_root}/serviceos-architecture/testing/02-m164-duplicate-acceptance.md" <<'EOF'
---
title: M164 重复验收
milestone: M164
---
EOF
if SERVICEOS_REPOSITORY_ROOT="${fixture_root}" \
    bash "${fixture_root}/scripts/generate-milestone-index.sh" >/dev/null 2>&1; then
  echo "索引生成器回归失败：重复里程碑候选未被拒绝" >&2
  exit 1
fi

echo "里程碑索引生成器回归通过。"
