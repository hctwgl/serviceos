#!/usr/bin/env bash
set -euo pipefail

# ADR-091 P0：以 Flyway 迁移基线重新生成 jOOQ 代码（com.serviceos.jooq.generated）。
#
# 生成器本体：serviceos-backend/src/test/java/com/serviceos/codegen/JooqCodegen.java
# （test classpath，复用 testcontainers/postgresql/flyway 依赖，不进入运行时镜像）。
#
# 环境与镜像约定完全复用 scripts/verify-local.sh：Apple Silicon/OrbStack 下清理
# DOCKER_DEFAULT_PLATFORM、复用本地原生 postgres:18-alpine 镜像、不强制 linux/amd64。
# 可用 SERVICEOS_TEST_POSTGRES_IMAGE 覆盖镜像（与 verify-local 相同变量）。
#
# 用法：bash scripts/generate-jooq.sh
# 幂等可重复：同一迁移基线下重复运行不应产生 git diff；与基线的一致性由
# scripts/check-jooq-generated.sh 门禁校验。

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${root}"

bash scripts/verify-local.sh -pl serviceos-backend test-compile exec:java

generated_dir="serviceos-backend/src/generated/java/com/serviceos/jooq/generated"
table_count="$(find "${generated_dir}/tables" -maxdepth 1 -name '*.java' | wc -l | tr -d ' ')"
echo "jOOQ 生成物已更新：${table_count} 张表（${generated_dir}）。"
