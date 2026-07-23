---
title: ServiceOS 产品、架构与交付决策基线
version: 1.0.0
status: Accepted
lastUpdated: 2026-07-22
owner: Product Owner
---

# ServiceOS 产品、架构与交付决策基线

## 1. 文档目的

本文件汇总 ServiceOS 项目已经由产品负责人明确确认、或在多个 Accepted/Implemented 文档中反复成立的产品、架构和交付决策。

它解决三个问题：

1. 防止后续 Agent、研发和测试只围绕局部技术切片实施，却偏离最终产品；
2. 防止把“接口存在、路由可打开、自动测试通过”误写成“产品已经完成”；
3. 防止讨论中的建议、历史实现和当前事实相互混淆。

本文件是决策入口，不替代更细的 ADR、领域模型、OpenAPI、事件 Schema、Flyway、产品规格和验收矩阵。需要实施具体能力时，必须继续读取相应事实源。

## 2. 决策状态与使用规则

本文件使用以下状态：

- `CONFIRMED`：产品负责人已确认，或已存在 Accepted/Implemented 事实源；后续实现必须遵守。
- `CONFIRMED_PENDING_INTEGRATION`：产品负责人已确认，但对应 ADR/代码/文档尚未完全合入主干；不得当作已实施事实。
- `IMPLEMENTATION_PRINCIPLE`：实现方式可以演进，但不得违背该原则表达的目标和安全边界。
- `OPEN`：尚待产品负责人或架构负责人决定；Agent 不得自行补成结论。

冲突处理顺序继续遵守仓库根 `AGENTS.md`：

1. 当前任务中产品负责人明确批准的最新决策；
2. 已接受 ADR；
3. Accepted/Implemented 架构与验收文档；
4. OpenAPI、事件 Schema、Flyway 等机器契约；
5. 自动化测试证明的行为；
6. 当前代码；
7. README、注释和历史说明。

代码与文档冲突时，不得默认代码正确。

---

# 第一部分：产品与业务边界

## PD-001 ServiceOS 产品定位

**状态：`CONFIRMED`**

ServiceOS 是面向新能源充电设施现场服务的可配置履约平台。首个落地行业是新能源汽车家充勘测、安装、维修和拆装，但平台边界面向可复用的现场履约能力，不为每一家车企复制一套业务代码。

ServiceOS 的核心价值是：

- 统一承接车企、品牌方和其他业务入口的服务订单；
- 通过可配置流程、任务、表单、资料、审核、整改、SLA、派单和集成规则完成履约；
- 为总部、网点、师傅和外部协作方提供各自独立的工作入口；
- 保留完整的责任、版本、操作、资料、异常和外部交付证据；
- 让不同客户项目复用同一执行内核，而不是通过复制代码实现差异。

ServiceOS 不是：

- 车企接口转发器；
- 单一项目定制系统；
- 以数据库字段和接口命令为中心的开发控制台；
- 把所有角色放入同一菜单树的万能门户；
- 只展示 KPI 的运营大屏；
- 允许用户直接编辑任意 JSON 的配置工具。

## PD-002 Tenant 是最高隔离边界

**状态：`CONFIRMED`**

`Tenant` 是 ServiceOS 最高层的数据、安全、配置和审计隔离边界。

当前本地开发主要使用 `tenant-local`，但本地示例不得削弱正式多租户约束。

Tenant 不等于：

- 项目；
- 车企；
- 品牌；
- 企业组织；
- 服务网点；
- 区域。

一个 Tenant 可以包含多个客户品牌、项目、组织、网点、师傅和工单。项目、区域、网点和人员权限均在 Tenant 内部继续计算。

任何请求中的 `tenantId`、`projectId`、`networkId`、`regionCode` 或操作人信息都不能作为客户端自报授权事实。

## PD-003 平台业务层级

**状态：`CONFIRMED`**

ServiceOS 的业务范围至少按以下层级表达：

```text
Tenant
  ├─ 客户/品牌
  ├─ 项目
  │   ├─ 服务产品/工单类型
  │   ├─ 履约方案（多个）
  │   ├─ 区域与服务网络
  │   └─ 工单
  ├─ 企业组织
  ├─ 合作组织/服务网点
  ├─ 用户、师傅与授权
  └─ 平台配置、审计与集成
```

项目是 Tenant 内的业务运营与配置范围，不得承担 Tenant 的安全隔离职责。

---

# 第二部分：Portal 与客户端策略

## PD-004 独立 Portal，而不是一个应用切换所有角色

**状态：`CONFIRMED`**

总部、网点和师傅面对不同任务密度、设备、网络条件和数据范围，必须拥有独立 Portal，同时共享服务端领域动作、契约类型和设计语言。

正式边界：

| Portal / Client | 主要用户 | 产品职责 |
|---|---|---|
| `ADMIN` | 总部运营、调度、审核、配置、权限和审计人员 | 高密度队列、协调、审核、项目配置、治理与观测 |
| `NETWORK` | 网点负责人和获授权协作人员 | 本网点工单、派师傅、预约协调、补资料、整改和人员管理 |
| `TECHNICIAN_WEB` | 师傅、开发、测试、产品和受控运营人员 | 在线参考实现、契约联调、产品走查、自动化回归和有限应急操作 |
| `TECHNICIAN_IOS` | 师傅 | 正式现场作业、相机/GPS、安全存储、后台上传和离线执行 |
| `TECHNICIAN_ANDROID` | 师傅 | iOS 试点稳定后复用已接受契约实现 |
| `EXTERNAL` | 用户、车企或受控协作方 | 确认、签字、有限状态和回执 |

不得通过前端角色判断把同一应用动态变形成所有 Portal，也不得把 H5 包装进 WebView 后宣称具备正式原生现场可靠性。

## PD-005 Technician H5、iOS、Android 策略

**状态：`CONFIRMED`**

- Technician H5 与原生 iOS 长期并行，不删除 H5 已实现能力。
- H5 是在线业务参考实现、契约联调和产品/测试走查入口，也可在明确边界下承载轻量应急操作。
- iOS 是首个正式原生现场生产客户端。
- Android 当前为 Deferred，在 iOS 试点稳定后评估和实施。
- H5 与 iOS 使用同一 `TECHNICIAN` Persona、服务端动作语义、Page ID、Action Code 和 Schema 语义，但属于两个独立发布物。
- H5 不承诺长时间断网恢复、后台可靠大文件上传、设备级数据撤销和与原生相同的采集可信度。
- iOS 负责 Keychain、原生相机/GPS、本地加密数据库、后台上传、OfflineCommand、冲突恢复和设备撤销。

交付顺序：

```text
服务端契约与权限边界
→ Technician H5 在线参考实现
→ iOS 在线实现
→ iOS 离线与后台上传增强
→ 真机弱网与 TestFlight 试点
→ Android 后续评估
```

## PD-006 跨 Portal 共享与禁止共享

**状态：`CONFIRMED`**

可以共享：

- OpenAPI/JSON Schema 生成类型和客户端；
- Problem Details、错误码、状态枚举和格式化；
- Design Token、图标、文案和无角色假设的基础组件；
- Page ID、Feature ID、Action Code 和版本兼容规则；
- 脱敏样例、验收用例和可观测字段。

不得共享：

- 整页路由和角色菜单；
- 含 Tenant/Project/Network/Technician 数据范围假设的 Store；
- 通过前端角色判断替代服务端授权的逻辑；
- iOS 本地数据库、相机、定位、后台上传和 OfflineCommand 实现；
- 未经产品审查的页面副本。

---

# 第三部分：身份、用户中心与授权

## PD-007 Keycloak 与 ServiceOS 的职责边界

**状态：`CONFIRMED`**

Keycloak/企业 IdP 负责：

- 登录认证；
- OIDC/JWT；
- SSO；
- MFA；
- 登录会话和令牌生命周期；
- 可选的粗粒度客户端准入。

ServiceOS 不保存登录密码、密码摘要、MFA 密钥或 IdP 会话。

ServiceOS 是以下业务身份与授权事实的权威来源：

- `Principal`；
- `IdentityLink`；
- `PersonProfile`；
- `Persona`；
- 企业组织、任职和合作关系；
- `ServiceNetwork`、`NetworkMembership`、`TechnicianProfile`；
- `Role`、`Capability`、`RoleGrant`、`DataScope`、`Delegation`；
- Portal Context；
- 服务端 Navigation；
- 业务动作授权和阻塞原因。

不得把 Keycloak 用户、Group 或 Realm Role 直接当成 ServiceOS 业务人员、网点成员、师傅档案或完整业务角色。

## PD-008 主体、人员、业务身份和授权必须分离

**状态：`CONFIRMED`**

- `Principal` 是稳定操作主体；
- 一名自然人可以绑定多个外部登录身份；
- 一名自然人可以拥有多个 Persona；
- 同一主体可以在不同有效期和数据范围拥有不同 RoleGrant；
- 内部组织、合作组织、ServiceNetwork 和 TechnicianProfile 是不同对象；
- 网点不能伪装成内部部门；
- 师傅档案不能等同登录账号；
- 账号有效不代表师傅可接单。

师傅可接单至少要求：主体、师傅档案、当前网点关系、技能/资质和服务状态全部有效。

## PD-009 Portal Context 由服务端计算

**状态：`CONFIRMED`**

统一链路：

```text
Keycloak Identity
→ ServiceOS Principal
→ Persona
→ Membership / TechnicianProfile
→ RoleGrant / DataScope
→ PortalContext
→ Capabilities / Navigation / allowed-actions
```

最小服务端查询：

```text
GET /api/v1/me
GET /api/v1/me/contexts
GET /api/v1/me/capabilities
GET /api/v1/me/navigation
```

请求中的 Project、Network 或 Portal 只能选择服务端已返回的有效上下文，不能创建授权。

上下文版本过期、主体停用、授权撤销、成员关系失效或网点清退必须失败关闭。

## PD-010 菜单、路由和按钮不是授权事实

**状态：`CONFIRMED`**

- 菜单根据 Portal Context、Capability、Feature Gate 和 Page Registry 生成；
- Page ID 是稳定产品标识，不是授权能力；
- 隐藏菜单不能代替服务端鉴权；
- 知道路由不能绕过数据范围和动作授权；
- 前端不得根据角色名称猜测权限；
- 业务按钮必须优先由服务端 `allowed-actions` 和阻塞原因驱动；
- 服务端命令继续按 Capability、DataScope、当前责任、状态、版本和 obligations 实时判定。

Keycloak 中配置粗粒度客户端访问只代表“能否进入某客户端”，不能替代 ServiceOS 的业务授权。

## PD-011 统一用户中心的产品边界

**状态：`CONFIRMED`**

统一用户中心不是一个单纯的“用户表格”，而是身份、组织、网点、师傅、角色、授权和影响治理的统一运营工作区。

至少包括：

- 用户目录与用户详情；
- 身份绑定和主体生命周期；
- 企业组织架构与任职；
- 合作组织、服务网点和网点成员；
- 师傅档案、网点关系、技能与资质；
- 角色与 Capability 组合；
- 用户授权、临时授权、委托与撤销；
- 停用、离职、转岗、清退和撤权影响分析；
- 身份、组织和授权审计。

运营人员通过安全目录和业务名称选择对象，不以复制 UUID 作为主要交互方式。

“M187 Implemented”只表示该里程碑声明的纵向技术范围存在代码和测试证据，不代表统一用户中心已经达到完整产品验收。

---

# 第四部分：工单、任务与履约执行

## PD-012 WorkOrder 只保留稳定生命周期

**状态：`CONFIRMED`**

`WorkOrder` 表达稳定业务生命周期和履约主体，不把勘测、安装、审核、整改、预约、派单等全部塞入一个不断增长的工单状态枚举。

具体履约进度由以下对象表达：

- Workflow Instance；
- Stage；
- Task；
- TaskAssignment；
- Appointment；
- Visit；
- Evidence；
- Review/Correction；
- SLA；
- Outbound Delivery。

前端必须把这些对象组合为用户可理解的业务进度和下一步，而不是要求用户在多个技术页面中自行拼接。

## PD-013 Task 是执行与责任核心

**状态：`CONFIRMED`**

任务是日常运营和现场执行的主要工作单元。

产品必须优先回答：

- 当前要做什么；
- 谁负责；
- 谁可以领取或执行；
- 最晚什么时候完成；
- 为什么被阻塞；
- 完成后进入哪里；
- 当前允许哪些操作。

队列、工作台和详情页应围绕 Task、Exception、责任和 SLA 组织，而不是先展示抽象工单状态。

Task Assignment、候选人、领取、开始、完成、释放、改派和失权均由服务端规则决定，前端不能自行推断。

## PD-014 Workflow 由配置驱动，运行时锁定版本

**状态：`CONFIRMED`**

ServiceOS 当前采用可配置 Stage/Task 执行模型。

配置解析至少考虑：

```text
client/brand
+ project
+ serviceProduct / workOrderType
+ region
+ effectiveTime
```

并解析出唯一可运行配置 Bundle。

继承和覆盖发生在草稿、校验和发布阶段；运行时不得动态继承并重新解释配置。

建单后必须冻结实际使用的配置版本、流程版本和运行快照，确保：

- 新配置只影响符合生效条件的新工单；
- 历史工单可解释；
- 重放和审计可还原；
- 运行时不因配置变更产生漂移。

## PD-015 项目履约配置不是 JSON 编辑器

**状态：`CONFIRMED`**

履约方案属于项目详情，用于管理当前项目的多个履约方案；每个方案可发布、可校验、可预览、可比较、可恢复和可审计的完整方案版本。底层可以继续使用以下配置资产与 Bundle 作为机器运行模型：

- Workflow；
- Form；
- Evidence Requirement；
- Rule；
- SLA；
- Dispatch；
- Notification；
- Pricing；
- Integration Mapping；
- Calendar。

Manifest、Digest、内部引用和原始 JSON 是机器运行和诊断事实，不是普通项目配置管理员的主要操作界面。

产品界面必须在明确项目上下文中提供：

- 业务化草稿编辑；
- 结构化校验结果；
- 运行说明书；
- 与当前发布版本的真实差异；
- 发布影响分析；
- 生效时间；
- 已发布版本和使用范围；
- 工单冻结快照及解释。

前端不得自行解析 Manifest 形成第二套业务解释逻辑；应由服务端返回产品化 Preview、Compare、Impact 和 Snapshot DTO。

当前阶段不设置 Admin 一级“配置中心”，也不建设跨项目公共流程、表单、资料、SLA 或通知模板库。项目配置入口固定为“客户与项目 → 项目详情 → 履约方案”；跨项目配置问题只在工作台形成任务投影和业务深链接。公共模板确有复用需求时进入下一期单独确认，不提前暴露底层资产目录。

---

# 第五部分：Admin 产品设计与信息架构

## PD-016 Admin 是运营工作平台，不是开发调试页面

**状态：`CONFIRMED`**

ServiceOS Admin 是平台内部统一运营管理后台，用于长期、日常、高频运营。

普通业务页面不得成为：

- API Explorer；
- 数据库管理器；
- 领域对象浏览器；
- Capability/RoleGrant 调试器；
- Manifest/JSON 编辑器；
- 内部状态机和技术枚举展示页。

页面必须首先回答：

1. 这是什么业务对象；
2. 当前处于什么状态；
3. 当前由谁负责；
4. 下一步应该做什么；
5. 有什么风险或阻塞；
6. 最晚什么时候完成；
7. 当前允许执行哪些动作；
8. 操作成功后会发生什么。

UUID、API Path、If-Match、Idempotency-Key、aggregateVersion、内部 code、Manifest JSON、表达式和 correlationId 字段名只能进入诊断或高级信息区域。

## PD-017 Admin 一级信息架构

**状态：`CONFIRMED`**

Admin 一级菜单冻结为：

1. 工作台；
2. 工单运营；
3. 服务履约；
4. 审核与整改；
5. 客户与项目；
6. 组织与资源；
7. 系统管理；
8. 审计与监控。

菜单围绕运营任务组织，不按后端模块、数据库表或领域类名机械生成。

服务端 Navigation 是可见页面事实源；前端应使用服务端返回的 section/pageId/route/title/order 进行展示，不得再通过前端正则表达式自行推断页面归属并形成第二套信息架构。

## PD-018 六张母版优先

**状态：`CONFIRMED`**

优先完成并逐张验收以下母版：

1. 运营工作台；
2. 用户管理；
3. 项目管理；
4. 工单中心；
5. 工单详情；
6. 项目履约方案。

母版未通过产品负责人批准前：

- 不得批量复制页面结构；
- 不得把现有截图建立为视觉金标；
- 不得宣称整个平台产品化完成。

## PD-019 页面围绕用户任务，而不是后端对象

**状态：`CONFIRMED`**

错误路径：

```text
后端增加一个对象
→ 前端增加一个菜单
→ 表格展示全部字段
→ 页面提供若干命令按钮
```

正确路径：

```text
明确目标角色和真实任务
→ 设计队列/工作区/详情/向导
→ 组合多个领域查询和命令
→ 服务端返回允许动作与解释
→ 技术细节渐进披露
→ 真实业务场景验收
```

复杂流程使用全页专用工作区或向导；Modal 只用于短确认。创建和编辑使用标准表单，不把长表单永久放在列表首屏。

## PD-020 产品中文化与业务语义

**状态：`CONFIRMED`**

面向中国运营人员的正式页面必须使用一致、自然、可理解的中文业务语言。

用户看到：

- 分配服务网点；
- 安排师傅；
- 联系预约；
- 提交安装资料；
- 审核通过；
- 驳回整改；
- 撤销授权。

用户不应直接看到：

- `assign-candidates`；
- `submit-evidence`；
- `reject-review`；
- `principalId`；
- `scopeRef`；
- `aggregateVersion`；
- 英文后端枚举和异常原文。

后端协议必须使用英文 code 时，由 Presenter/字典层统一转换，不允许页面各自实现翻译。

---

# 第六部分：技术架构与工程原则

## PD-021 模块化单体优先，API/Worker 当前不拆分

**状态：`CONFIRMED`**

ServiceOS 当前采用模块化单体和单一应用镜像策略，不是无边界单体。

- 模块通过公开 API、领域事件或明确 SPI 协作；
- 禁止跨模块访问内部包、内部 Repository 或内部表；
- Controller 只做协议适配；
- Application Service 编排用例和事务；
- Domain 维护规则与不变量；
- Infrastructure 隔离技术实现；
- Spring Modulith 模块边界失败是阻断问题。

API/Worker 当前不拆分，继续遵守 ADR-016 的单一镜像、独立迁移和失败关闭发布策略。以后是否拆分必须基于真实容量、故障隔离和团队边界证据，通过新 ADR 决定。

## PD-022 Java 与基础技术栈

**状态：`CONFIRMED`**

当前目标技术栈：

- Java 21；
- Spring Boot；
- Spring Modulith；
- PostgreSQL；
- Flyway；
- OpenAPI / JSON Schema；
- OIDC / JWT；
- Inbox / Outbox；
- Capability + Data Scope；
- Vue 3 + TypeScript；
- Ant Design Vue 作为 Admin 基础组件库。

版本升级必须通过依赖兼容、契约、迁移和完整验证，不得仅因“更新”而升级。

Ant Design Vue 是组件实现，不等于产品信息架构或页面设计。

## PD-023 业务数据访问统一为 jOOQ

**状态：`CONFIRMED_PENDING_INTEGRATION`**

产品负责人已经确认业务数据访问层收敛到：

```text
jOOQ
+ Spring Transaction
+ Flyway
+ HikariCP
+ PostgreSQL
```

目标是删除 MyBatis、Mapper/XML 和业务 JdbcClient 三套并存状态。

迁移原则：

- 当前在途里程碑完成后启动；
- 单批次完成主干切换；
- 合并前删除旧依赖、Mapper/XML 和业务 JdbcClient；
- 不设置长期双轨、兼容层或静默回退；
- 旧实现只保留在 Git 历史；
- IT/E2E、性能抽查、依赖清零、生成物一致和全量验证必须通过。

对应 ADR-091 尚需完成主干集成，未合入前不得宣称代码已经迁移完成。

## PD-024 可靠性与失败关闭

**状态：`CONFIRMED`**

- 聚合修改、审计、幂等结果和 Outbox 同事务提交；
- 外部请求、事件和回调定义幂等键和重复语义；
- 消费者使用 Inbox 或等价唯一约束去重；
- Worker 使用可恢复的 claim/lease/retry，并有上限、退避和人工接管；
- 并发写入必须包含状态、版本或等价条件；
- 外部网络调用不处于持有数据库锁的长事务；
- 不得吞异常、伪造成功或把未知结果当成功；
- 授权、上下文、数据范围和敏感访问默认失败关闭。

## PD-025 新系统不保留假兼容

**状态：`CONFIRMED`**

ServiceOS 是新系统。默认直接修正模型、契约、迁移、测试和调用方，不为假设中的旧客户端或脏数据增加永久兼容。

禁止：

- 长期保留新旧字段、接口、状态、模型或持久化双轨；
- 缺值、非法值或未知状态时静默补默认值；
- 吞异常后返回空对象、空集合或默认成功；
- 没有真实对象的 `legacy`、`compat`、`fallback`、`temporary` 分支；
- “先兼容以后再删”但没有期限和退出证据。

真实外部系统、已发布客户端或生产数据确需兼容时，必须有 ADR、明确对象、边界适配层、期限、观测指标和删除计划。

---

# 第七部分：AI 与 Agent 接入原则

## PD-026 AI 不另建一套业务系统

**状态：`IMPLEMENTATION_PRINCIPLE`**

AI/Agent 通过 ServiceOS Intelligence Gateway、Capability Registry 或等价受控入口接入现有执行内核，不另建一套绕过领域规则的系统。

Agent 只能通过受控能力发起系统指令，并经过：

```text
身份透传
→ Portal/Context 校验
→ Capability/DataScope 校验
→ 策略与 HITL
→ 幂等与版本检查
→ 正式领域命令
→ 审计与可观测记录
```

Agent 不得：

- 直接修改数据库；
- 绕过 Application Service；
- 根据自然语言自行扩大数据范围；
- 伪造用户身份或责任；
- 在未知结果下宣称成功；
- 创建与正式配置中心平行的隐藏配置。

---

# 第八部分：完成状态与验收

## PD-027 技术完成不等于产品完成

**状态：`CONFIRMED`**

所有能力必须分别记录：

### 技术状态

- `TECH_NOT_STARTED`
- `BACKEND_IMPLEMENTED`
- `API_AVAILABLE`
- `RUNTIME_CONNECTED`

### 前端状态

- `FRONTEND_NOT_STARTED`
- `FRONTEND_SHELL_ONLY`
- `FRONTEND_CONNECTED`
- `FRONTEND_COMPLETE`

### 产品状态

- `PRODUCT_NOT_DESIGNED`
- `PRODUCT_DESIGNED`
- `READY_FOR_REVIEW`
- `PRODUCT_ACCEPTED`
- `PRODUCT_REJECTED`

### 质量状态

- `TEST_NOT_RUN`
- `TEST_PASSED`
- `VISUAL_NOT_REVIEWED`
- `VISUAL_APPROVED`
- `A11Y_NOT_REVIEWED`
- `A11Y_APPROVED`

面向产品负责人报告“已完成”的唯一条件：

```text
API_AVAILABLE 或 RUNTIME_CONNECTED
+ FRONTEND_COMPLETE
+ PRODUCT_ACCEPTED
+ TEST_PASSED
+ VISUAL_APPROVED
+ A11Y_APPROVED
```

路由存在、AppShell 接入、PageContainer 使用、接口可调用或自动化测试通过，均不能单独构成产品完成。

## PD-028 真实角色场景是产品验收终点

**状态：`CONFIRMED`**

验收必须由不了解代码实现的真实目标角色完成业务任务。

Admin V1 至少证明：

- 找到待处理工单；
- 查看当前责任、SLA、风险和下一步；
- 分配网点和师傅；
- 联系预约并跟踪现场履约；
- 审核资料并发起整改；
- 创建项目；
- 配置项目工单类型、流程、表单、资料和 SLA；
- 创建用户并授予角色和数据范围；
- 查询配置、授权和操作变更记录。

跨端黄金链路至少证明：

```text
车企/外部工单进入
→ 总部受理与审核
→ 分配网点
→ 网点分配师傅
→ 师傅预约与上门
→ 表单和资料提交
→ 审核/整改
→ 回传与完结
```

用户不能完成完整链路时，不得用单个接口测试或数据库结果代替产品验收。

## PD-029 视觉基线建立顺序

**状态：`CONFIRMED`**

正确顺序：

```text
产品设计
→ 真实实现
→ 人工产品审查
→ 修复
→ 产品负责人批准
→ 建立视觉金标
→ 自动视觉回归防退化
```

视觉回归不能把未经审查的半成品自动升级为产品标准。

关键页面至少提供：

- 1440×1024 正常状态；
- 1280px 正常状态；
- 空、加载、错误、无权限、只读、冲突和失效状态；
- 真实或脱敏业务样例；
- 主要抽屉、弹窗、向导或全页流程；
- 键盘、200% 缩放和适用读屏证据。

---

# 第九部分：当前状态纠偏

## PD-030 当前前端不是产品金标

**状态：`CONFIRMED`**

现有 Admin、Network 和 Technician 页面是有价值的实现输入和迁移基线，但不能整体视为最终产品设计。

当前已出现并需要持续纠正的问题包括：

- 页面按后端对象和 API 生长；
- 菜单与领域模块一一对应；
- 技术字段、内部 ID、JSON 和英文枚举直接暴露；
- 工作台缺少真实待办和运营上下文；
- 用户需要跨多个页面自行拼接一张工单的状态；
- 页面存在路由和接口，却没有完整正常/空/错/无权限/冲突状态；
- 自动测试通过后被错误描述为产品完成；
- 前端自行推断菜单分组、业务解释和 Manifest 内容。

PR #202 形成了 Design Token、AppShell、基础页面模式和部分页面改造，是产品化基础设施，不等于所有页面已经通过产品验收。

M383 项目履约配置当前已有真实纵向能力，但产品审查结论仍是 `PRODUCT_REJECTED`；必须完成结构化草稿、产品化预览、真实差异、allowed-actions、配置向导、去 JSON 化和人工视觉验收后才能接受。

## PD-031 统一用户中心当前状态表述

**状态：`CONFIRMED`**

正确表述：

- 身份、组织、网点、师傅、角色、授权和 Portal Context 纵向能力已经具备较多实现；
- Admin 已存在对应路由、API 客户端和部分 E2E；
- 服务端 Navigation 根据 Context 和 Capability 计算；
- 但统一入口、菜单组织、业务化授权体验和完整页面产品验收仍需继续完善。

禁止表述：

- “统一用户中心已经全部完成”；
- “Keycloak 中分配角色就完成了 ServiceOS 业务授权”；
- “能看到页面就表示拥有业务操作权限”；
- “M187 Implemented 表示产品体验已经验收”。

---

# 第十部分：实施顺序

## PD-032 产品化实施顺序

**状态：`CONFIRMED`**

后续前端实施按照以下顺序推进：

### 阶段一：统一产品骨架

- 落实八个一级菜单，并把项目履约方案收回项目详情；
- 统一 Navigation section、Page ID 和 route；
- 清理正式导航中的开发工具和技术页面；
- 建立统一 AppShell、页面头、范围栏、操作区和状态容器；
- 统一中文业务词汇、状态 Presenter 和错误解释。

### 阶段二：完成六张母版

1. 运营工作台；
2. 用户管理；
3. 项目管理；
4. 工单中心；
5. 工单详情；
6. 项目详情内的履约方案工作区。

每张母版单独进行产品负责人验收。

### 阶段三：按黄金业务链路交付

- 收单与初审；
- 分配网点与师傅；
- 预约与上门；
- 表单、资料和现场事实；
- 审核与整改；
- 外部回传、异常和完结。

### 阶段四：扩展治理与运营能力

- 项目履约方案的完整资产管理；
- SLA、派单、通知和日历策略；
- 审计与监控；
- 网点 Portal 产品化；
- iOS 在线、离线和弱网；
- 结算、对账与外部协同按独立事实源继续推进。

跨项目公共模板不属于当前阶段；只有形成真实复用需求并由产品负责人确认后，才进入下一期设计。

---

# 第十一部分：尚待确认事项

以下事项保持 `OPEN`，不得由 Agent 擅自决定：

1. 首个正式试点客户、品牌、项目和地区；
2. 首个生产级黄金链路的准确业务规则和 SLA 数值；
3. 车企协同 Portal 的完整产品范围；
4. C 端用户登录、客户主数据、车辆/地址关系、隐私同意和注销保留策略；
5. Android 启动条件和正式时间；
6. 正式 HR/OA/企业微信等组织目录 Connector；
7. 生产 Keycloak/企业 IdP 方案、MFA 策略和客户端粗粒度准入配置；
8. 结算价格、争议、调整和对账的正式业务基线；
9. API/Worker 未来拆分条件；
10. AI 自动执行的风险等级、HITL 边界和可授予 Capability 清单；
11. 完整 NFR：容量、延迟、可用性、RPO/RTO、文件规模和保留周期；
12. 六张母版逐张视觉批准结果。

这些事项未确认前，可以做可逆的技术准备和原型，但不得写入“已完成”或生成不可逆生产语义。

---

# 第十二部分：实施前检查清单

任何新里程碑、页面或 Agent 任务开始前，至少检查：

```text
[ ] 目标角色和真实任务是否明确
[ ] 所属 Portal 和 Tenant/Project/Network 数据范围是否明确
[ ] 对应产品页面和信息架构位置是否明确
[ ] 服务端 Capability、DataScope 和 allowed-actions 是否明确
[ ] 使用的配置版本和运行快照语义是否明确
[ ] 正常、空、错、无权限、冲突和失效状态是否定义
[ ] 是否暴露内部 ID、API、JSON、技术枚举或英文错误
[ ] 是否存在重复导航、重复业务解释或前端权限推断
[ ] 技术、前端、产品和质量状态是否分别记录
[ ] 真实角色端到端验收路径是否定义
[ ] 本次明确不做的边界是否写清楚
[ ] 相关 ADR、OpenAPI、Flyway、测试和实施状态是否同步
```

## 相关事实源

- `serviceos-architecture/architecture/00-product-constitution.md`
- `serviceos-architecture/architecture/05-configuration-version-center.md`
- `serviceos-architecture/architecture/06-work-order-task-execution-kernel.md`
- `serviceos-architecture/architecture/07-identity-authorization-audit.md`
- `serviceos-architecture/product/01-cross-portal-information-architecture.md`
- `serviceos-architecture/product/02-admin-operations-portal-spec.md`
- `serviceos-architecture/product/03-network-portal-spec.md`
- `serviceos-architecture/product/04-technician-mobile-app-spec.md`
- `serviceos-architecture/product/05-cross-portal-interaction-state-spec.md`
- `serviceos-architecture/product/06-design-system-accessibility-spec.md`
- `serviceos-architecture/product/07-page-action-permission-matrix.md`
- `serviceos-architecture/product/admin/README.md`
- `serviceos-architecture/roadmap/03-identity-organization-governance-delivery-plan.md`
- `serviceos-architecture/docs/implementation-status.md`
- `serviceos-architecture/docs/implementation-traceability-matrix.md`
- `serviceos-architecture/decisions/ADR-016-single-image-explicit-migration-fail-closed-deployment.md`
