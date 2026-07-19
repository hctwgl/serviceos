---
title: M320 多 OEM 并行建单冒烟
status: Implemented
milestone: M320
lastUpdated: 2026-07-19
relatedMilestones: [M267, M272, M311]
---

# M320 多 OEM 并行建单冒烟

## 目标

在同一 Spring 运行时并行冒烟 BYD / REFERENCE_OEM / GEELY 入站建单，证明三适配器共存并共用通用管道。不声称吉利 Sandbox 真实联调。

## 范围

- `MultiOemParallelCreateSmokePostgresIT`
- 三租户/三 Bundle/三入站端点同测
- 断言三工单 `client_code` 与三 `connector_version_id`

## 明确未实现

- 吉利 Sandbox；跨 OEM 提审/回调同测矩阵；生产凭据

## 验证

```bash
bash scripts/agent-verify.sh it MultiOemParallelCreateSmokePostgresIT
```
