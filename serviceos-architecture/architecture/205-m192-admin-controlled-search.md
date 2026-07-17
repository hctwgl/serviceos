---
title: M192 Admin 受控全局搜索
status: Implemented
milestone: M192
lastUpdated: 2026-07-17
relatedMilestones: [M189, M190, M191]
---

# M192 Admin 受控全局搜索

## 目标

在 Admin SavedView/偏好轨道之上，为 Admin Portal 提供失败关闭的受控全局搜索：经既有授权
查询端口 fan-in，不引入全文索引平台，不扩大 Capability 真相。

## 范围与非目标

- 范围：
  - 窄接受 API-06 §7 Admin 受控搜索与 DATA-06 §9 受控精确/前缀回退；
  - ADR-030：`search.read` HIGH + type 读能力降级省略；
  - Core OpenAPI `0.84.0` `GET /search`；
  - Flyway `V094`：仅种子 `search.read`（无搜索索引表 / 无审计表）；
  - `WORK_ORDER` / `EXTERNAL_ORDER` / `NETWORK` / `TECHNICIAN`；
  - Admin Web Search 页 + `ADMIN.SEARCH` 导航；
  - PostgreSQL IT、MVC Security、ArchitectureTest、Admin E2E。
- 明确不做：
  - `search_document` 索引 / Elasticsearch / 异步 indexer；
  - `VEHICLE` / `CHARGER`；
  - Network/Technician Portal 搜索 UI；
  - SavedView ORGANIZATION 共享、Consumer Identity、评分、BUSINESS SLA。

## 事实源

- `api/06-application-query-preference-http-api.md` §0 / §7.1（M192 Accepted）
- `data/06-application-projection-preference-logical-model.md` §0 / §9.1
- ADR-030

## 设计要点

- fan-in 既有授权端口：工单 UUID / `externalOrderCode` 精确；网点 code/name 前缀；
  师傅 displayName 前缀与主体 `employeeNumber` 精确；
- 未支持 type → 422；缺 `search.read` → 403；缺 type 能力 → 省略；
- 完整手机号形态拒绝，仅末四位；响应 meta 使用 `qDigest`，不回显敏感原文；
- 每 type 上限 10、总上限；跨租户隔离。

## 已实现

- [x] ADR-030
- [x] OpenAPI Core `0.84.0`
- [x] Flyway `V094`（`search.read`）
- [x] `ControlledSearchQueryService` / Controller
- [x] WorkOrderQuery `externalOrderCode` 精确筛选
- [x] Admin Web Search 页 + Page Registry `ADMIN.SEARCH`
- [x] ControlledSearchPostgresIT / Security MVC / ArchitectureTest / E2E

## 明确未实现

- `search_document` 物化与异步索引；
- `VEHICLE` / `CHARGER`；
- Network/Technician Portal 搜索；
- 敏感搜索审计表（本切片未证明需要独立审计表）。

## 工程证据

- Flyway：`db/migration/readmodel/V094__seed_search_read_capability.sql`
- OpenAPI：`serviceos-core-v1.yaml` 0.84.0
- IT：`ControlledSearchPostgresIT`
- MVC：`ControlledSearchControllerSecurityTest`
- E2E：`admin-controlled-search.spec.ts`

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs contracts arch
./mvnw -pl serviceos-backend -am test \
  -Dtest=ArchitectureTest,ControlledSearchPostgresIT,ControlledSearchControllerSecurityTest
bash scripts/verify-local.sh
```
