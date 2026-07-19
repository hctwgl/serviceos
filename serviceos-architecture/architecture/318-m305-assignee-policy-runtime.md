---
title: M305 ASSIGNEE_POLICY 运行时
status: Implemented
milestone: M305
lastUpdated: 2026-07-19
relatedMilestones: [M21, M295, M303]
---

# M305 ASSIGNEE_POLICY 运行时

## 目标

从冻结 Bundle 加载 ASSIGNEE_POLICY，按 priority 求值 when，产出候选 USER 列表、Fallback 与解释；不绕过 TaskAssignment。

## 范围

- `AssigneePolicyRuntime.resolve`
- 策略 USER/ROLE 通过调用方 principalsByRoleCode 解析；ORGANIZATION/NETWORK 本切片解释后走 Fallback
- Fallback：MANUAL_INTERVENTION / ROLE_POOL
- 版本锁定：assetVersionId + contentDigest

## 明确未实现

- 运行时直读组织/网点当前成员表；自动调用 assignCandidates；DISPATCH 评分引擎

## 验证

```bash
bash scripts/agent-verify.sh test DefaultAssigneePolicyRuntimeTest
bash scripts/agent-verify.sh it AssigneePolicyRuntimePostgresIT
```
