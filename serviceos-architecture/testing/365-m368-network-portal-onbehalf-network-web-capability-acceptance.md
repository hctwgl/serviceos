---
title: M368 Network Portal on-behalf NETWORK_WEB 能力门禁验收矩阵
status: Implemented
milestone: M368
lastUpdated: 2026-07-20
---

# M368 Network Portal on-behalf NETWORK_WEB 能力门禁验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M368-01 | begin/finalize/snapshot/resubmit 且 `X-ServiceOS-Client-Kind=NETWORK_WEB`，槽位 PHOTO 兼容 | 成功（既有 M201 语义保留） | `NetworkPortalEvidenceOnBehalfPostgresIT` |
| M368-02 | ClientKind 缺失/UNKNOWN/TECHNICIAN_* | 422 `CLIENT_CAPABILITY_UNSUPPORTED`，不调用领域写命令 | `DefaultNetworkPortalEvidenceServiceTest` + Security |
| M368-03 | 槽位 SIGNATURE（NETWORK_WEB 目录不支持） | 422 `CLIENT_CAPABILITY_UNSUPPORTED` | PostgresIT |
| M368-04 | EVIDENCE 资产定向仅 `TECHNICIAN_WEB` | on-behalf 仍允许（定向不约束网点代补） | PostgresIT / Gate unit |
| M368-05 | OpenAPI 1.0.59 登记 ClientKind + 422 | 契约门禁通过 | `serviceos-core-v1.yaml` + contracts |
| M368-06 | ArchitectureTest | evidence → configuration::api 边界保持 | ArchitectureTest |

## 明确不验收

- 表单代改、Visit、iOS 执行器、要求资产声明 NETWORK_WEB 定向。
