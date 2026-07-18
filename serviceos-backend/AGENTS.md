# serviceos-backend 模块地图

本文件是后端工程的导航补充，帮助 Agent 不探索目录树就能定位模块。工程硬约束（模块边界、事务、持久化、验证阶梯）以根 `AGENTS.md` 为准，本文件不重复也不放宽。

任务路由先看 `serviceos-architecture/docs/agent-navigation.md`；里程碑文档索引用 `serviceos-architecture/docs/milestone-index.md`。

## 包结构约定

每个 Modulith 模块按统一分层组织：

```text
com.serviceos.<module>/
├── api/              # 公开 API：跨模块只允许依赖这里（或 spi/）
├── spi/              # 显式 SPI（仅部分模块）
├── application/      # Application Service：编排用例与事务
├── domain/ 或模型类   # 领域规则与不变量（不依赖 Web/MyBatis/外部 SDK）
├── infrastructure/   # Mapper/Repository 适配器等技术实现
├── web/              # Controller（仅协议适配，仅对外 HTTP 的模块有）
└── package-info.java # Spring Modulith 模块声明与 allowedDependencies
```

跨模块读数据必须经对方 `api` 包的最小端口（如 `TaskTimelineContextQuery`、`DeliveryTimelineContextQuery`），禁止跨模块访问内部包、Repository 或表。

## 模块一览

| 模块 | 职责 | 表前缀 | 对外端口 |
|---|---|---|---|
| appointment | 预约修订、联系尝试、终态动作 | `apt_` | api |
| audit | 业务审计事实 | `aud_` | api |
| authorization | Capability/RoleGrant、Tenant/Project/REGION/NETWORK Scope 实时授权 | `auth_` | api |
| bootstrap | 应用装配与全局配置 | 无自有表 | — |
| configuration | 不可变配置资产、Bundle 发布解析、表达式子集 | `cfg_` | api |
| dispatch | ServiceAssignment、容量权威、改派 Inbox Saga | `dsp_` | api |
| evidence | 资料槽位/Item/Revision/Snapshot、ReviewCase/CorrectionCase | `evd_` | api |
| fieldwork | Visit 签到/签退/中断现场生命周期 | `fld_` | api |
| files | 安全文件 Begin/Finalize/隔离/扫描/授权下载 | `fil_` | api |
| forms | 动态表单资产、不可变 FormSubmission | `frm_` | api |
| identity | OIDC/JWT 主体上下文、统一主体目录与任职终止停用端口 | `idn_` | api |
| organization | 企业组织、OrgUnit closure、任职与目录同步收据 | `org_` | api |
| network | 合作组织、ServiceNetwork、网点成员、师傅档案与资质 | `net_` | api |
| integration | BYD CPIM 入站 Envelope/Canonical、OutboundDelivery/Attempt/Ack；M267 起 CREATE_WORK_ORDER 通用管道与 `spi` | `int_` | api、spi；`byd` 为模块内 OEM 适配器 |
| operations | OperationalException 运营异常工作台 | `ops_` | api |
| project | Project 核心事实、REGION/NETWORK 范围关系与修订收据 | `prj_` | api |
| readmodel | 工单时间线/工作区/专项队列只读投影（不回写领域事实） | `rdm_` | api |
| reliability | Inbox/Outbox、幂等结果、可靠发布 | `rel_` | api、spi |
| shared | 无业务归属的最小公共能力 | 无自有表 | — |
| sla | SLA 时钟、segment/milestone、到期对账 | `sla_` | api |
| task | Task 命令/Assignment/ExecutionGuard/Attempt，唯一重试时钟 | `tsk_` | api、spi |
| workflow | 线性 Stage/Task 工作流运行时（不改业务表） | `wfl_` | api |
| workorder | WorkOrder 权威聚合与生命周期 | `wo_` | api |

新增模块、职责调整或依赖方向变化必须同步架构文档与 `package-info.java`，重大变化更新 ADR；不得通过扩大 `allowedDependencies`、开放内部包或把业务代码移入 `shared` 绕过边界。

## 测试布局

- `src/test/java/com/serviceos/<module>/`：模块测试；`*Test.java` 为单元测试，`*PostgresIT.java` 为真实 PostgreSQL Testcontainers 集成测试；
- `src/test/java/com/serviceos/ArchitectureTest.java`：Spring Modulith 模块边界测试；
- SQL、锁、claim/lease、唯一约束、迁移语义必须由 `*PostgresIT` 证明，不得只用 H2 或 Mock。

## 精准验证命令

统一使用仓库脚本（Apple Silicon/OrbStack 下 PostgreSQL IT 必须走 `it` 入口）：

```bash
bash scripts/agent-verify.sh compile          # 编译
bash scripts/agent-verify.sh test <Class>     # 单元测试（精准）
bash scripts/agent-verify.sh it <Class>       # PostgreSQL IT（精准，含架构修正）
bash scripts/agent-verify.sh arch             # ArchitectureTest
bash scripts/agent-verify.sh docs             # 文档/脚本/索引静态检查
bash scripts/verify-local.sh                  # L3 全量 verify（里程碑门禁）
```
