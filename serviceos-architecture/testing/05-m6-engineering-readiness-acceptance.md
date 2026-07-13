---
title: M6 工程、部署、安全与运行就绪验收矩阵
version: 0.1.0
status: Proposed
---

# M6 工程、部署、安全与运行就绪验收矩阵

## 1. 使用规则

- P0 是进入试点环境或生产 cohort 的阻断门禁；
- 测试必须引用真实 commit、镜像 digest、配置/迁移版本和环境；
- “代码存在”“人工看过”“启动成功”不能替代场景证据；
- 故障场景应验证最终业务状态、重复副作用和恢复任务，而不只验证 HTTP 状态码；
- 本矩阵不替代 M2～M5 业务验收，二者必须同时满足。

“适用”必须由 release manifest 中实际部署的组件、Portal、依赖和 cohort 推导；跳过 P0 需要写明未部署/不可触达证据并由测试与架构签署，不能由开发者自行标记不适用。

## 2. 工程与模块边界

| ID | Priority | 场景 | 操作 | 预期证据 |
|---|---|---|---|---|
| ENG-001 | P0 | 可复现构建 | 新环境使用 Wrapper 构建 | 编译、测试、契约、镜像一步通过 |
| ENG-002 | P0 | 禁止模块循环 | 人为增加 A↔B 依赖 | Modulith/ArchUnit 构建失败并指出路径 |
| ENG-003 | P0 | 禁止访问 internal | 跨模块引用 infrastructure/repository | 构建失败 |
| ENG-004 | P0 | allowedDependencies | 引入未声明模块 API | 构建失败 |
| ENG-005 | P0 | 模块独立启动测试 | 逐模块运行 ApplicationModuleTest | 公开端口可替换、无隐式 bean 依赖 |
| ENG-006 | P0 | Portal 独立 | 分别构建 Admin/Network/Technician | 独立产物、路由、权限和发布版本 |
| ENG-007 | P1 | 模块文档生成 | CI 生成依赖图 | 与批准清单一致、无未知根包 |
| ENG-008 | P0 | shared-kernel 约束 | 扫描 shared 类型 | 无业务服务、repository、车企枚举或可变规则 |

## 3. 数据库与迁移

| ID | Priority | 场景 | 操作 | 预期证据 |
|---|---|---|---|---|
| DB-001 | P0 | 空库安装 | 对空 PostgreSQL 执行迁移 | 完整成功、应用健康 |
| DB-002 | P0 | 发行版升级 | 从上一 release 数据快照升级 | 数据/约束保留、应用新旧兼容窗口成立 |
| DB-003 | P0 | 重复启动 | 同版本迁移重复执行 | 无重复 DDL/数据 |
| DB-004 | P0 | 失败迁移 | 中途注入错误 | 发布停止、状态可诊断、按计划恢复 |
| DB-005 | P0 | 应用账号最小权限 | 生产 profile 尝试 DDL/跨 schema 管理 | 拒绝并审计 |
| DB-006 | P0 | Decimal/时区 | 金额边界与跨时区日期样本 | 无浮点误差，UTC/业务日期语义正确 |
| DB-007 | P0 | 并发约束 | 容量、assignment、fact guard、line 并发 | 不变量保持且无无限死锁 |
| DB-008 | P1 | 大表在线变更 | staging 数据量执行 expand/contract | 锁时间和回退在签署阈值内 |

## 4. 事务、幂等与消息

| ID | Priority | 场景 | 故障注入 | 预期证据 |
|---|---|---|---|---|
| TX-001 | P0 | 同 key 同请求 | 重复/并发提交 | 同一资源或 operation |
| TX-002 | P0 | 同 key 不同请求 | 修改 payload 重用 key | IDEMPOTENCY_KEY_REUSED，零额外写入 |
| TX-003 | P0 | 聚合乐观锁 | 两方基于同版本更新 | 一个成功，一个 409，无覆盖 |
| TX-004 | P0 | Outbox 发布后崩溃 | 发布成功、保存 PUBLISHED 前终止 | 可重发，消费者只产生一个业务结果 |
| TX-005 | P0 | 消费成功 ack 失败 | 提交 Inbox 后断开 | 重投后幂等返回 |
| TX-006 | P0 | payload 篡改 | 同 eventId 不同 digest | 拒绝、P0/P1 异常和安全审计 |
| TX-007 | P0 | worker 租约 | claim 后进程退出 | 租约到期安全认领，attempt 可追踪 |
| TX-008 | P0 | 外部结果 UNKNOWN | 请求成功可能性下丢响应 | 查询/回执对账，不盲发第二业务结果 |
| TX-009 | P0 | 最终失败 | 重试耗尽 | OperationalException + handling Task |
| TX-010 | P0 | authority 切换 | 旧 worker 已取得执行信号 | 最终 fence 拒绝，无旧权威副作用 |
| TX-011 | P0 | 流程引擎不可用 | 停止引擎后提交领域命令 | 领域事务不丢，恢复后继续且不重复 Task |
| TX-012 | P1 | Broker 不可用 | 停止 Broker | Outbox 积压受监控，恢复后有序排空 |
| TX-013 | P0 | 新工单权威引导 | Reserve 后在 Create/Bind 各阶段崩溃，并发 expiry 后重试 | 同 creationBusinessKey 一个 assignment/工单；已引用 reservation 不被过期；LEGACY 不创建权威工单；真孤儿可恢复 |

## 5. 身份、授权与隐私

| ID | Priority | 场景 | 主体/操作 | 预期证据 |
|---|---|---|---|---|
| M6-SEC-001 | P0 | 跨租户 IDOR | 已知其他租户 UUID 直查 | 404/403，无存在性泄露，审计 |
| M6-SEC-002 | P0 | 跨区域/网点 | 网点访问其他网点工单 | 拒绝，列表/详情/导出一致 |
| M6-SEC-003 | P0 | 旧参与人 | 改派后原网点/师傅执行 | 实时拒绝，不依赖缓存撤销完成 |
| M6-SEC-004 | P0 | 金额隔离 | 客服查询对下价格 | 拒绝/脱敏，trace 不泄露 |
| M6-SEC-005 | P0 | 字段权限 | 普通用户读取手机号/VIN/签字 | 按策略掩码或拒绝 |
| M6-SEC-006 | P0 | 导出 | 无 export capability/超范围导出 | 拒绝；合法导出带字段策略和审计 |
| M6-SEC-007 | P0 | token 过期/撤销 | 使用旧 access/refresh token | 拒绝，refresh rotation 生效 |
| M6-SEC-008 | P0 | 高风险 MFA | 发布价格/切换/大批导出 | 未满足 MFA/审批时拒绝 |
| M6-SEC-009 | P0 | 恶意文件 | 病毒、伪 MIME、超大/压缩炸弹 | 隔离/拒绝，不可业务引用 |
| M6-SEC-010 | P0 | 预签名 URL | 过期、改 object key、跨租户 | 拒绝，桶无公共访问 |
| M6-SEC-011 | P0 | 日志敏感信息 | 执行全链路并扫描 logs/traces | 无 token、手机号、地址、VIN、价格 payload |
| M6-SEC-012 | P0 | secret 扫描 | 在代码/镜像注入测试 secret | CI 阻止，运行凭据来自 secret manager |
| M6-SEC-013 | P1 | break-glass | 激活紧急账号 | 双人审批、短期、告警、完整审计 |

## 6. API 与契约

| ID | Priority | 场景 | 操作 | 预期证据 |
|---|---|---|---|---|
| M6-API-001 | P0 | OpenAPI 实现一致 | contract test 调用所有首切片端点 | 状态、Schema、错误码一致 |
| M6-API-002 | P0 | 事件 Schema 兼容 | 删除/改变必需字段 | CI 阻止破坏性变更 |
| M6-API-003 | P0 | Problem Details | 触发 validation/auth/concurrency | 稳定 errorCode/correlation，无内部泄露 |
| M6-API-004 | P0 | 限流 | 用户/连接器突发 | 429/退避契约，无全局雪崩 |
| M6-API-005 | P0 | 分页/导出边界 | 无界 page/大范围请求 | 上限、异步 operation 和授权生效 |
| M6-API-006 | P1 | 旧客户端兼容 | 上一发布客户端调用 | 兼容窗口内工作或明确版本拒绝 |

## 7. 性能与韧性

| ID | Priority | 场景 | 负载/故障 | 预期证据 |
|---|---|---|---|---|
| NFR-001 | P0 | Launch 档位 | 签署峰值与业务混合 | SLO、资源和锁等待满足目标 |
| NFR-002 | P0 | Burst 档位 | 收单+照片+回执短时叠加 | 无数据错乱，限流/积压可控 |
| NFR-003 | P0 | 外部依赖慢 | 车企/OCR/短信超时 | API 线程隔离，Task/Delivery 恢复 |
| NFR-004 | P0 | PostgreSQL 故障转移 | staging 主库切换 | 应用恢复、无重复领域结果 |
| NFR-005 | P0 | 对象存储故障 | 上传/finalize 中断 | 可恢复、无假 AVAILABLE 文件 |
| NFR-006 | P0 | worker backlog | 停止 worker 后恢复 | backlog age 告警，目标时间内安全排空 |
| NFR-007 | P0 | 缓存失效 | 清空/停 Redis | 正确性保持，仅性能可降级 |
| NFR-008 | P1 | 热点竞争 | 单区域/网点大量派单 | 公平、无超配、锁等待可诊断 |
| NFR-009 | P0 | 资源泄漏 | 长稳压测 | 连接、线程、内存、临时文件稳定 |

## 8. 部署、备份与恢复

| ID | Priority | 场景 | 操作 | 预期证据 |
|---|---|---|---|---|
| OPS-001 | P0 | 不可变产物 | 从 test 提升到 production-like | 镜像 digest 相同、配置外置 |
| OPS-002 | P0 | 健康与优雅停机 | 滚动替换 API/worker | 流量排空、claim 释放、无中断重复 |
| OPS-003 | P0 | canary 停止扩大 | Gate 失败 | 自动/人工 Hold，cohort 不扩大 |
| OPS-004 | P0 | 应用回滚 | 发布后发现错误 | 数据/消息兼容成立，回滚 smoke 通过 |
| OPS-005 | P0 | 数据库 PITR | 从真实备份恢复到目标点 | 满足签署 RPO/RTO，业务核对通过 |
| OPS-006 | P0 | 文件恢复 | 恢复对象与数据库引用 | checksum/数量/访问权限一致 |
| OPS-007 | P0 | 跨环境隔离 | test 尝试生产凭据/桶/队列 | 技术上不可访问 |
| OPS-008 | P0 | 告警 runbook | 触发 P0 告警 | 去重、通知 on-call、按 runbook 恢复 |
| OPS-009 | P0 | authority rollback | 使用 RollbackPlan 演练 | 副作用清点、反向同步、验证、无双写 |
| OPS-010 | P1 | 区域级灾难 | 模拟主要故障域不可用 | 达到签署灾备目标 |

## 9. Portal 与移动端

| ID | Priority | 场景 | 操作 | 预期证据 |
|---|---|---|---|---|
| UI-001 | P0 | 后端授权不可绕过 | 修改前端/直接调 API | 仍拒绝非法动作 |
| UI-002 | P0 | 契约类型 | 后端 Schema 改变 | 生成和 typecheck 阻止漂移 |
| UI-003 | P0 | 师傅离线草稿 | 断网、杀进程、重启 | 草稿和上传队列恢复 |
| UI-004 | P0 | 重复同步 | 客户端重发同 action | 一个业务结果 |
| UI-005 | P0 | 冲突 | 服务端版本已更新 | 不覆盖，展示刷新/解决路径 |
| UI-006 | P0 | 改派撤权 | 旧师傅离线后上线同步 | 本地任务失效，提交被后端拒绝且提示 |
| UI-007 | P0 | 无障碍/可用性 | 关键表单、审核和错误 | 键盘/可读标签/清晰错误达到签署标准 |

## 10. 证据包

每次 Gate 生成：

```text
release / commit / image digest
dependency lock / SBOM / scan result
database baseline + migration versions
configuration bundle + feature gates
test dataset/sample IDs
automated reports + failure injection logs
performance profile + query/lock evidence
backup/restore evidence
open defects and accepted risks
business/technology/security/operations sign-off
```

证据保存期和访问权限按发布/审计政策执行。

## 11. M6 退出条件

- ENG、DB、TX、SEC、API、NFR、OPS、UI 所有适用 P0 通过；
- M2～M5 首个纵向切片对应的 P0 场景通过；
- 真实 sandbox 收单/回传和真实脱敏资料样本通过；
- 影子价格无正式结算或其他真实副作用；
- staging 完成迁移、性能、故障、备份恢复、canary 和 rollback；
- 所有 P0/P1 缺陷关闭，或 P1 有书面风险接受且不破坏不变量；
- 生产试点负责人、技术、安全和运维签署进入小 cohort；
- 未完成业务类型明确标记未支持，不以通用代码存在推断已闭环。
