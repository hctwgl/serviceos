---
title: M9 身份授权与可靠消息验收矩阵
version: 0.1.0
status: Proposed
---

# M9 身份授权与可靠消息验收矩阵

## 1. 门禁

| ID | Priority | 场景 | 预期证据 | 当前自动化 |
|---|---|---|---|---|
| M9-SEC-001 | P0 | 无 Bearer token 调用命令 | 401 `UNAUTHENTICATED`，零业务写入 | Web MVC |
| M9-SEC-002 | P0 | JWT 无 tenant_id | 拒绝生成 CurrentPrincipal | Unit |
| M9-SEC-003 | P0 | 伪造 tenant/actor header | 服务端仍使用 JWT 主体 | Web MVC |
| M9-SEC-004 | P0 | 缺 capability | 403，独立 DENY 审计，零业务写入 | Unit + PostgreSQL IT |
| M9-SEC-005 | P0 | 跨租户但有 capability | DENY 优先，不能越权 | Unit |
| M9-SEC-006 | P0 | token 过期/签名/audience 错误 | Resource Server 拒绝 | 待正式 OIDC sandbox |
| M9-SEC-007 | P0 | RoleGrant 撤销但旧 token 仍声明 capability | 实时命令拒绝 | PostgreSQL IT |
| M9-REL-001 | P0 | 同 eventId/digest 重投 | Inbox REPLAY，一个结果 | PostgreSQL IT |
| M9-REL-002 | P0 | 同 eventId 不同 digest | 拒绝并分类安全异常 | PostgreSQL IT |
| M9-REL-003 | P0 | 两 worker 同时 claim | 同一时刻仅一个租约拥有者 | PostgreSQL IT 基线；并发压测待补 |
| M9-REL-004 | P0 | 发布失败 | FAILED/DEAD、attempt 可追踪 | Unit + PostgreSQL IT |
| M9-REL-005 | P0 | 发布成功、状态保存失败 | 不误记失败，租约恢复后同 eventId 重发 | Unit；故障注入待补 |
| M9-REL-006 | P0 | worker claim 后崩溃 | 过期租约可恢复 | PostgreSQL IT |
| M9-REL-007 | P0 | 无真实 publisher | worker 不启动、事件保持 PENDING | Context/配置检查待补 |
| M9-REL-008 | P0 | 重试耗尽 | DEAD + OperationalException + handling Task | DEAD 已实现；异常闭环待 E1 operations/task |

## 2. 退出条件

M9 参考实现可合并需要：

- 非容器单元、模块、Web MVC 与契约测试通过；
- 有容器 CI 的 PostgreSQL/Flyway/Inbox/Outbox IT 通过；
- OpenAPI 不再接受主体伪造头；
- CI 明确要求 Docker，不能静默跳过数据库 P0；
- 未实现的正式 OIDC、授权投影、Broker 和异常闭环仍标记缺口。

M9 通过不等于 M6 E1 或生产安全就绪。
