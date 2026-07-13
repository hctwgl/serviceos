---
title: M16 配置发布解析与 BYD 工单接入验收矩阵
version: 0.1.0
status: Proposed
---

# M16 配置发布解析与 BYD 工单接入验收矩阵

## 1. Gate

所有 P0 场景必须在真实 PostgreSQL 18 上执行；Testcontainers 因 Docker 不可用而跳过，不构成通过证据。

| ID | 优先级 | 场景 | 权威断言 |
|---|---|---|---|
| M16-CFG-001 | P0 | 发布资产并重复发布相同版本 | 返回同一 versionId，只保留一行 |
| M16-CFG-002 | P0 | 同版本不同内容摘要 | 拒绝，不覆盖已发布内容 |
| M16-CFG-003 | P0 | 发布 Bundle 引用跨租户/未发布资产 | 拒绝，不生成 Bundle |
| M16-CFG-004 | P0 | 同 scope 有效期重叠 | advisory lock 串行后仅一个成功 |
| M16-CFG-005 | P0 | 省份精确与通配同时命中 | 唯一选择精确版本；不得误报为多命中 |
| M16-CFG-006 | P0 | 零命中/同优先级多命中 | 失败关闭，不创建工单 |
| M16-CFG-007 | P0 | UPDATE/DELETE Published 配置 | 数据库触发器拒绝 |
| M16-WO-001 | P0 | 同租户同车企订单同载荷重放 | 返回同一 workOrderId，仅一行 |
| M16-WO-002 | P0 | 同租户同订单不同载荷 | `ORDER_CONFLICT`，原工单不变 |
| M16-WO-003 | P0 | 不同租户相同 client/order | 各自创建一张，互不冲突 |
| M16-WO-004 | P0 | tenant/project/bundle 交叉拼接 | 复合外键拒绝 |
| M16-HTTP-001 | P0 | 合法 BYD HTTP 请求 | 验签后创建一张锁定配置版本的 `RECEIVED` 工单 |
| M16-HTTP-002 | P0 | 相同 Nonce/载荷重放 | `REPLAYED`，防重放表和工单各一行 |
| M16-HTTP-003 | P0 | 新 Nonce/相同业务订单与载荷 | `REPLAYED`，仍为同一工单 |
| M16-HTTP-004 | P0 | 非法签名或非法业务载荷 | 不占 Nonce，不建工单 |
| M16-HTTP-005 | P0 | 无配置命中 | 不占 Nonce，不建工单，可修复配置后重试 |
| M16-HTTP-006 | P0 | 工单写入后事务异常 | Nonce 与工单同时回滚，不返回伪成功 |
| M16-MIG-001 | P0 | PostgreSQL 18 空库迁移 | current=`014`，applied=`16`，重复 migrate=0 |
| M16-MIG-002 | P0 | 旧工单表有未回填数据 | V014 失败关闭并输出显式回填要求 |
| M16-DEP-001 | P0 | staging 独立迁移 | configuration/integration/workorder 表均存在后才启动 backend |

## 2. 执行命令

```bash
./mvnw clean verify
serviceos-deploy/staging/verify-rehearsal.sh
```

局部 PostgreSQL 回归：

```bash
./mvnw -pl serviceos-backend \
  -Dit.test=ConfigurationPublicationPostgresIT,WorkOrderCommandPostgresIT,BydCpimInboundOrderHttpPostgresIT \
  verify
```

## 3. 完成判定

- P0 PostgreSQL 用例 0 failure、0 error、0 skipped；
- Modulith/ArchUnit 边界通过；
- staging 空库迁移、smoke、回滚和恢复通过；
- 敏感输出扫描不出现手机号、地址、VIN、签名或密钥；
- 文档、迁移版本、部署 Gate 和实现保持一致。
