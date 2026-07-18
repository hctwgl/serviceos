---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-18
---

# ServiceOS 自主 Agent 交接

## 当前分支和 HEAD

- 分支：`cursor/bc-71d073b6-2d93-418e-a750-0b73ee12db1c-0a6a`
- PR：https://github.com/hctwgl/serviceos/pull/146
- 主线：阶段一多车企平台内核（`roadmap/06`）
- latestMilestone：**M268**（提交后回填 SHA）

## 当前目标

**M268 已完成（待提交）**。下一目标：**M269 条件 Transition + EXCLUSIVE_GATEWAY 运行时**。

## 已完成内容

### M267
- 多车企程序计划/验收、ADR-085、`integration.spi`、`InboundCreateWorkOrderPipeline`
- BYD 入站委托管道；ArchitectureTest 防污染；L3 PASS

### M268
- workflow `condition` → `{language,source}` + 内嵌 schema 漂移门禁
- `ConfigurationAssetSchemaValidator` WORKFLOW/EXCLUSIVE_GATEWAY 静态校验
- Parser 识别对象条件；线性运行时仍失败关闭
- 相关单测 + Configuration/Workflow PostgreSQL IT PASS

## 未完成内容

- M269+ 网关运行时、WAIT_EVENT、标准安装模板、REFERENCE_OEM、双车企回归、第三家手册
- Track F；Apple 签名真机 = `BLOCKED_EXTERNAL`

## 关键设计决定

1. 阶段一优先于 Track F。
2. M268 不启用完整 Workflow JSON Schema 强制（历史夹具未对齐），仅语义门禁。
3. M269 负责运行时唯一 true 出边与零/多命中失败关闭。

## 已执行验证

```text
M267: verify-local.sh L3 PASS
M268:
  ConfigurationSchemaDriftTest / ConfigurationAssetSchemaValidatorTest / WorkflowDefinitionParserTest PASS
  ConfigurationPublicationPostgresIT / WorkflowBootstrapPostgresIT / WorkflowLinearProgressionPostgresIT PASS
  ArchitectureTest + docs PASS
```

## 下一步具体入口

1. `WorkflowDefinitionParser` / `WorkflowTaskCompletedHandler` — EXCLUSIVE_GATEWAY 运行时
2. 复用 `ExpressionEvaluator` + `ExpressionContext`（工单冻结字段）
3. 新 `WorkflowExclusiveGatewayPostgresIT`
4. `282-m269-*` / `266-m269-*`
