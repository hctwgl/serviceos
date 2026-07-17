---
title: M186 角色与授权治理验收矩阵
status: Implemented
milestone: M186
lastUpdated: 2026-07-17
---

# M186 角色与授权治理验收矩阵

| 编号 | 场景 | 预期 | 结果 |
|---|---|---|---|
| M186-01 | 创建租户角色 | 只能组合稳定 Capability，不能更改能力语义或风险级别 | pass |
| M186-02 | 申请高风险 RoleGrant | 申请人不能自批；审批者不能超过自己的可授予范围 | pass |
| M186-03 | 撤销 RoleGrant | 新请求立即失权；grant generation 推进；历史事件保留 | pass |
| M186-04 | 同主体多范围授权 | 同条维度 AND、多条 OR；显式 DENY 优先 | pass |
| M186-05 | 创建 Delegation | 能力/范围/期限不超过委托人；运行时合成授权 | pass |
| M186-06 | authorization:explain | 需 authorization.explain；返回脱敏匹配摘要 | pass（API+能力门禁；专项 IT 覆盖判定路径） |
| M186-SEC-01 | 缺少治理能力访问 API | 403，不泄露目录细节 | pass |
| M186-ARCH-01 | authorization 依赖 reliability::api | ArchitectureTest 通过；禁止越界内部包 | pass |

## 证据

- `./mvnw -pl serviceos-backend -am test -Dtest=ArchitectureTest,AuthorizationGovernanceControllerSecurityTest,RoleGrantGovernancePostgresIT,AuthorizationPolicyPostgresIT`
- `./mvnw -pl serviceos-contracts -am test`
- Contracts/docs/preflight + ArchitectureTest after OpenAPI/docs sync
