# ServiceOS 待替换范围登记表

状态：Accepted  
更新日期：2026-07-21

## 1. 使用目的

本文件登记当前 `master` 中允许直接删除、重建或重新定义的范围。它不是迁移清单，不要求保持旧行为。

登记结果分为：

- `REBUILD`：保留业务目标，重新设计和实现；
- `DELETE`：直接删除，不提供替代入口；
- `REVIEW_KEEP`：审查后保留正确领域能力；
- `REDEFINE`：重新定义契约或数据模型。

## 2. Web 前端

| 范围 | 处理 | 说明 |
|---|---|---|
| `serviceos-admin-web` 页面与路由 | REBUILD | 以 A+ 产品设计和新 Workspace 重建 |
| `serviceos-network-web` 页面与路由 | REBUILD | 重建为网点协作工作台 |
| `serviceos-technician-web` | REBUILD | 仅保留在线受限定位，不承担正式现场作业 |
| `serviceos-web-core` | REDEFINE | 拆分为认证、上下文、API、产品语言等明确包 |
| Token 登录页 | DELETE | 正式产品只使用真实认证 |
| Portal Stub | DELETE | 不进入新产品工程 |
| Demo Data 管理页 | DELETE | 场景数据由 Seed 和测试夹具提供 |
| Golden Path 演练页 | DELETE | 测试链路进入自动化和独立开发工具 |
| 按内部 ID 打开页 | DELETE | 不作为正式业务入口 |
| Capability/Context 展示页 | DELETE | 技术诊断进入独立受控工具 |
| 入站、出站、Envelope 等普通业务导航 | DELETE | 仅在审计与集成诊断中按业务语言呈现 |

## 3. 原生师傅端

| 范围 | 处理 | 说明 |
|---|---|---|
| 原生 iOS 应用 | REBUILD | 正式现场作业客户端 |
| iOS 离线、后台上传、相机、定位、安全 | REVIEW_KEEP | 保留正确能力，按新业务工作环重构 |
| iOS 旧页面和导航 | REBUILD | 不兼容旧信息架构 |
| Android | DELETE | 当前不建设，不保留占位工程 |

## 4. 后端与接口

| 范围 | 处理 | 说明 |
|---|---|---|
| Tenant 隔离 | REVIEW_KEEP | 属于系统核心安全边界 |
| Portal Context | REVIEW_KEEP | 保留服务端权威语义，产品界面不直接展示技术概念 |
| allowed-actions | REVIEW_KEEP | 作为动作权限和状态约束事实源 |
| 幂等、事务、并发和审计 | REVIEW_KEEP | 必须保留并强化 |
| 页面自行拼装的领域 API | REDEFINE | 增加页面级读模型 |
| 仅为旧页面存在的 DTO | DELETE | 不保留字段别名和 Adapter |
| 英文枚举直接面向用户 | REDEFINE | API 保留稳定代码，并提供中文业务标签或产品语言映射 |
| 新接口失败后调用旧接口 | DELETE | 禁止双轨和回退 |

## 5. 数据库

| 范围 | 处理 | 说明 |
|---|---|---|
| 探索期开发数据 | DELETE | 不迁移，重建场景化数据 |
| 演示租户和随机数据 | DELETE | 用正式业务故事 Seed 替换 |
| 模糊表名、字段名和 JSON 逃避建模 | REDEFINE | 重建明确领域结构 |
| 每个数据库对象的中文描述 | REDEFINE | 表、字段、函数、触发器、约束等必须完整注释 |
| 核心业务触发器 | REDEFINE | 核心流程由应用层领域命令完成 |
| 探索期 Flyway 长链 | REDEFINE | 正式发布前压缩为新的产品基线 |

## 6. 文档

| 范围 | 处理 | 说明 |
|---|---|---|
| 当前有效产品决定 | REVIEW_KEEP | 统一状态为 Accepted |
| 被新决定覆盖的 Proposed 文档 | DELETE | 不增加“旧版说明”维持双口径 |
| 逐字段 Mxxx 产品化文档 | DELETE | 不再作为产品进度事实源 |
| 旧截图和旧视觉金标 | DELETE | 新高保真和真实页面验收后重新建立 |
| 中文工程规范 | REBUILD | 形成唯一事实源并接入 CI |

## 7. 删除时机

本登记表不要求立即在同一 PR 删除所有运行代码。删除按完整产品链路进行，但不得为过渡增加兼容层。

每个替换 PR 必须明确：

- 删除了哪些旧入口；
- 新能力覆盖了哪个完整用户任务；
- 是否仍存在同义旧实现；
- 剩余旧实现在哪个后续 PR 删除。

一旦新链路通过产品验收，旧链路必须在同一 PR 或紧随其后的删除 PR 中清除。
