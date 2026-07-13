---
title: M12 契约兼容 CI 与客户端生成验收矩阵
version: 0.2.0
status: Implemented
---

# M12 契约兼容 CI 与客户端生成验收矩阵

| ID | Priority | 场景 | 预期证据 | 自动化层次 |
|---|---|---|---|---|
| M12-OAS-001 | P0 | 当前 OpenAPI 3.1 | parser 无错误，关键路径存在 | JUnit |
| M12-OAS-002 | P0 | 当前契约相对 Git base 兼容 | oasdiff WARN/ERR 均为 0 | Shell + CI |
| M12-OAS-003 | P0 | 请求可选字段改为必填 | `request-property-became-required`，exit 1 | Synthetic negative probe |
| M12-EVT-001 | P0 | 新事件版本发布 | 文件名/$id/title/schemaVersion/eventType 自洽且有有效样本 | JUnit |
| M12-EVT-002 | P0 | 已发布 Schema 原地修改 | 明确指出文件，exit 1 | Synthetic negative probe |
| M12-EVT-003 | P0 | 新增 `-vN` 文件 | 不触碰旧版本时兼容门禁通过 | Synthetic positive probe |
| M12-GEN-001 | P0 | Maven clean verify | 固定 7.22.0 生成 `@serviceos/core-client@0.2.0` | Maven + JUnit |
| M12-GEN-002 | P0 | 清理后重新生成 | 两次完整生成树 SHA-256 相同 | Shell + CI |
| M12-ART-001 | P0 | CI 客户端制品 | 生成目录、files.sha256、manifest.json 被保留 14 天 | GitHub Actions |
| M12-ART-002 | P0 | 来源追踪 | manifest 含 contract/generator/package/tree 摘要 | Shell + jq |
| M12-SUP-001 | P0 | 下载 oasdiff | release archive SHA-256 不匹配即失败 | Shell |
| M12-CI-001 | P0 | PR/push 基线选择 | full history + base SHA/before SHA，缺对象时失败 | GitHub Actions |
| M12-CI-002 | P0 | PostgreSQL P0 | 契约通过后仍须 `docker info` + root `clean verify` | GitHub Actions |

## 验收命令

```bash
OASDIFF_BIN="$(serviceos-contracts/scripts/install-oasdiff.sh serviceos-contracts/target/contract-tools)" \
  serviceos-contracts/scripts/check-contract-compatibility.sh HEAD

OASDIFF_BIN="$(serviceos-contracts/scripts/install-oasdiff.sh serviceos-contracts/target/contract-tools)" \
  serviceos-contracts/scripts/test-contract-gates.sh

./mvnw --batch-mode --no-transfer-progress -pl serviceos-contracts -am clean verify

serviceos-contracts/scripts/verify-client-generation-reproducibility.sh
```

## 远端环境门禁

本地 contract 测试与生成不依赖 Docker；完整仓库 `clean verify` 必须在有容器运行时的环境执行 PostgreSQL IT。CI 在 Maven 前显式执行 `docker info`，避免把“容器不可用导致 IT 跳过”误报为绿色。

## 2026-07-13 本地验收证据

| 证据 | 结果 |
|---|---|
| 根仓库 `./mvnw clean verify` | SUCCESS |
| Backend 单元、架构与 Web 安全测试 | 34/34 通过，0 跳过 |
| PostgreSQL + Flyway 集成测试 | 22/22 通过，0 跳过；V001～V010 真实迁移 |
| Contract JUnit | 7/7 通过，0 跳过 |
| OpenAPI 破坏性负向探针 | 请求字段改为必填时 exit 1 |
| Event Schema 不可变负向探针 | 原地修改已发布版本时 exit 1 |
| Event Schema 新版本正向探针 | 新增 `-v2` 文件时通过 |
| TypeScript Fetch 重生成 | 两次完整文件树摘要一致 |

本地证据仍不能替代 GitHub 托管 runner 的绿色 workflow，也不能证明任何 Portal 已消费客户端 artifact。
