---
title: M14 容器、迁移、staging 与回滚验收矩阵
version: 0.1.0
status: Implemented
---

# M14 容器、迁移、staging 与回滚验收矩阵

| ID | Priority | 场景 | 预期证据 | 自动化层次 |
|---|---|---|---|---|
| M14-IMG-001 | P0 | 可重复构建后端镜像 | Maven Wrapper 在固定 JDK digest 构建 fat jar | Docker build + CI |
| M14-IMG-002 | P0 | 非 root/只读运行 | UID/GID 10001、read-only、tmpfs、cap drop、NNP | Docker inspect + smoke |
| M14-IMG-003 | P0 | 配置和 secret 外置 | 镜像环境无数据库/签名 secret，正式模式要求 digest | Negative shell gate |
| M14-MIG-001 | P0 | 同镜像独立迁移 | migrate/backend 实际 image ID 相同 | Docker inspect |
| M14-MIG-002 | P0 | 空库迁移 | V001～V011 成功，当前版本 011 | PostgreSQL 18 + Flyway |
| M14-MIG-003 | P0 | 应用账号最小权限 | runtime 可 SELECT、不可 CREATE TABLE | PostgreSQL negative |
| M14-MIG-004 | P0 | 迁移失败关闭 | 非零退出或版本不匹配时不替换 backend | Deployment negative |
| M14-HLT-001 | P0 | 运行 smoke | live/ready 为 UP，correlationId 回显可信值 | HTTP smoke |
| M14-HLT-002 | P0 | 指标保护 | 匿名 Prometheus 返回 401 | HTTP negative |
| M14-RBK-001 | P0 | 旧应用回滚 | 指定旧 commit 镜像替换成功且 live | Container rehearsal |
| M14-RBK-002 | P0 | 当前版本恢复 | 恢复当前镜像并重新执行完整 smoke | Container rehearsal |
| M14-EVD-001 | P0 | 发布证据 | commit/image ID/迁移版本/环境/checks 写入 JSON | Release manifest |
| M14-CI-001 | P0 | CI 全链路 | build→migrate→smoke→rollback→restore→negative gate | GitHub Actions |

## 完整验收命令

```bash
bash -n serviceos-deploy/staging/*.sh serviceos-deploy/staging/init/*.sh
docker compose --env-file serviceos-deploy/staging/staging.env.example \
  -f serviceos-deploy/compose.staging.yaml config --quiet
serviceos-deploy/staging/verify-rehearsal.sh
```

`staging.env.example` 仅用于 Compose 静态解析；实际演练必须使用 `generate-local-env.sh` 生成随机 secret，且不得提交生成文件。

## 2026-07-13 本地验收证据

| 证据 | 结果 |
|---|---|
| Docker build | 固定 Temurin 21 JDK/JRE digest；非 root 镜像构建成功 |
| 空库迁移 | PostgreSQL 18.4，11 个迁移成功，当前版本 `011` |
| 迁移门禁 | 数据库未健康时不运行；非零退出/版本不一致阻止 backend 替换 |
| 数据库权限 | runtime 读取成功；`CREATE TABLE` 被 PostgreSQL 拒绝 |
| 容器安全 | UID/GID 10001、read-only、cap drop ALL、no-new-privileges 通过 |
| HTTP smoke | live/ready、correlationId、metrics 401 通过 |
| 同镜像证明 | migrate/backend image ID 完全一致 |
| 回滚 | 上一 commit `79f1b29` 候选启动且 live；随后恢复当前镜像并完整 smoke |
| 失败关闭 | 期望迁移版本 `999` 被拒绝，原 backend 容器 ID 不变且 ready |
| 发布证据 | `target/staging-rehearsal/*/release-manifest.json` 可生成 |

本地证据不等于远端 workflow、生产 registry、PITR、容量、灾备或正式 staging 签署已经完成。
