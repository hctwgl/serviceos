---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-18
---

# ServiceOS 自主 Agent 交接

## 当前分支和 HEAD

- 分支：`cursor/bc-71d073b6-2d93-418e-a750-0b73ee12db1c-0a6a`
- HEAD：`994cb46f1e31ddc8a4364a776cc4849e766c3b6d`（M267 功能证据；随后有 baseline 回填提交）
- 主线：阶段一多车企平台内核（`roadmap/06`）

## 当前目标

**M267 已完成**。下一目标：**M268 配置治理 MVP**（workflow condition 与 SERVICEOS_EXPR_V1 对齐、Bundle 依赖闭包静态校验加厚）。

## 已完成内容

- 多车企程序交付计划与验收矩阵 Accepted
- ADR-085 通用 Connector SPI
- `integration.spi` + `InboundCreateWorkOrderPipeline`
- BYD CREATE_WORK_ORDER 入站委托管道（外部契约不变）
- ArchitectureTest 核心域防 OEM 适配包依赖
- 文档/索引/status/handoff 同步

## 未完成内容

- M268+：配置治理 MVP、条件 Transition、EXCLUSIVE_GATEWAY、WAIT_EVENT、标准安装模板、REFERENCE_OEM、双车企回归、第三家手册
- Track F 离线；Apple 签名真机 = `BLOCKED_EXTERNAL`
- BYD 回调/出站全面 SPI 化

## 关键设计决定

1. 用户提示词阶段一优先于 roadmap/05 Track F。
2. M267 仅强制 CREATE_WORK_ORDER 入站 SPI；不留空 Outbound 接口。
3. 适配器保留验签/Nonce；管道负责 Bundle→Canonical→领域命令。

## 已执行验证

```text
bash scripts/agent-verify.sh compile                         PASS
bash scripts/agent-verify.sh test ArchitectureTest,InboundCreateWorkOrderPipelineTest PASS
bash scripts/agent-verify.sh it BydCpimInboundOrderHttpPostgresIT,BydCpimReplayGuardPostgresIT PASS
相关 BYD 单测 / InboundMessageControllerSecurityTest PASS
bash scripts/agent-verify.sh docs                           PASS
bash scripts/verify-local.sh                                 PASS（L3，~379s）
```

## 失败与阻塞

- 无工程阻塞

## 下一步具体入口

1. `serviceos-architecture/configuration/schemas/workflow.schema.json` — 将 `transition.condition` 对齐 `{language,source}`
2. `ConfigurationAssetSchemaValidator` — EXCLUSIVE_GATEWAY 出边静态校验与依赖闭包
3. 新建 `281-m268-*` / `265-m268-*`
4. `ConfigurationPublicationPostgresIT` / `ConfigurationSchemaDriftTest`
5. 精准验证后提交并更新本文件
