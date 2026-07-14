# M31 预约联系与终态验收

| 用例 | 优先级 | 场景 | 预期 |
|---|---:|---|---|
| M31-CONTACT-001 | P0 | 三次未接通、第四次接通 | 四条事实按 `startedAt` 有序返回，首次联系时间可直接计算 |
| M31-CONTACT-002 | P0 | 同幂等键重放联系写入 | 返回首次冻结响应，只产生一条事实、审计和 Outbox |
| M31-CONTACT-003 | P0 | 尝试更新或删除联系事实 | PostgreSQL 不可变触发器拒绝 |
| M31-CANCEL-001 | P0 | 取消 PROPOSED/CONFIRMED 预约 | 新增 CANCEL 修订，聚合进入 CANCELLED，发布取消事件 |
| M31-NOSHOW-001 | P0 | 未结束窗口标记爽约 | 返回 `APPOINTMENT_WINDOW_NOT_ENDED`，事务无副作用 |
| M31-NOSHOW-002 | P0 | 已结束确认预约标记爽约 | 新增 NO_SHOW 修订并保留对象、原因、证据引用，发布事件 |
| M31-CONCURRENCY-001 | P0 | 陈旧 If-Match 执行终态命令 | 返回 `APPOINTMENT_VERSION_CONFLICT`，不覆盖获胜者 |
| M31-AUTH-001 | P0 | 无 grant、跨租户或 scope 不匹配 | 分别拒绝或按资源不存在关闭信息泄露 |
| M31-CONTRACT-001 | P0 | HTTP/事件契约 | OpenAPI 0.7.0 和三个新增事件样本通过治理门禁 |
| M31-MIGRATION-001 | P0 | 空库迁移与部署演练 | 33 个迁移成功，版本为 V031，失败路径保持 fail-closed |

完整门禁：`./mvnw verify` 与 `serviceos-deploy/staging/verify-rehearsal.sh`。
