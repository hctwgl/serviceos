# M32 Visit 现场生命周期验收

| 用例 | 优先级 | 场景 | 自动化证据与预期 |
|---|---:|---|---|
| M32-VIS-001 | P0 | 当前责任技师对有效预约签到、签退 | `VisitPostgresIT`：captured/received 时间、位置、事实、Appointment 状态和两个 Outbox 原子提交 |
| M32-VIS-002 | P0 | 重放相同 deviceCommandId | 返回首次冻结响应，只创建一个 Visit、签到事实、审计和 Outbox |
| M32-VIS-003 | P1 | 围栏外且项目策略为 WARN | 创建 Visit，冻结 `OUTSIDE_GEOFENCE + WARNING` 和策略版本 |
| M32-VIS-004 | P0 | 围栏异常且项目策略为 BLOCK | 返回 `VISIT_GEOFENCE_REJECTED`，Appointment 保持 CONFIRMED 且无 Visit 污染 |
| M32-VIS-005 | P0 | 中断后创建二次上门预约 | 新 Visit ID 和 task 级递增序号，旧 Visit 异常及证据保持不可变 |
| M32-VIS-006 | P0 | 改派后旧技师上传离线签到 | 返回 `TECHNICIAN_ASSIGNMENT_CHANGED`，无 Visit、事实、幂等占位和 Appointment 状态变化 |
| M32-VIS-007 | P0 | 更新/删除签到事实或 Visit 事实 | PostgreSQL 不可变触发器拒绝 |
| M32-CONCURRENCY-001 | P0 | 陈旧 If-Match 签退/中断 | 返回 `VISIT_VERSION_CONFLICT`，终态只能成功一次 |
| M32-AUTH-001 | P0 | 匿名、伪造租户或错误 scope | MVC Security/应用授权拒绝；tenant 和 actor 只来自 JWT |
| M32-CONTRACT-001 | P0 | HTTP、客户端和事件契约 | OpenAPI 0.8.0 可解析、客户端可重复生成、三个 Visit 样本通过 Schema 治理 |
| M32-MIGRATION-001 | P0 | 空库迁移与发布演练 | 34 个迁移成功，版本 V032；错误期望版本保持 fail-closed |

完整门禁：`./mvnw verify` 与 `serviceos-deploy/staging/verify-rehearsal.sh`。

M32 不宣称 FieldOperation 已闭环；`operationRefs` 的权威存在性、动态表单/资料完整性和离线工作包有效期由后续里程碑验收。
