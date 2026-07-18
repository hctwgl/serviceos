---
title: 多客户端 Portal 程序级验收矩阵
version: 0.1.0
status: Accepted
lastUpdated: 2026-07-18
---

# 多客户端 Portal 程序级验收矩阵

## 1. 目的

本矩阵验证 Admin、Network、Technician H5 和 Technician iOS 的应用边界、共享契约、在线履约、离线运行时、版本兼容与试点准备。

本文件是程序级验收基线，不替代每个 `Mxxx` 的实现验收矩阵。程序条目可由多个里程碑逐步满足；只有具备代码、机器契约和自动化/真机证据的范围才能标记为 `IMPLEMENTED`。

## 2. 状态

| 状态 | 含义 |
|---|---|
| `NOT_STARTED` | 尚未开始 |
| `PARTIAL` | 已有可靠切片，但程序目标未闭合 |
| `PASS` | 适用验收证据完整 |
| `BLOCKED` | 依赖未接受契约、业务策略或基础设施 |
| `N/A` | 经评审确认当前阶段不适用 |

## 3. 应用与工程边界

| ID | 验收项 | 预期 | 证据 |
|---|---|---|---|
| MCP-APP-01 | Admin 独立构建 | 不依赖 Network/Technician 路由才能构建和运行 | CI build + route test |
| MCP-APP-02 | Network 独立构建 | 独立 AppShell、会话、环境和部署 | CI build + E2E |
| MCP-APP-03 | Technician H5 独立构建 | 独立 AppShell、Technician Context 和路由 | CI build + Playwright |
| MCP-APP-04 | Technician iOS 独立构建 | SwiftUI 工程可在模拟器和开发真机构建 | Xcode CI + device evidence |
| MCP-APP-05 | 单仓库共享边界 | 共享包不包含角色菜单或数据范围假设 | architecture test/review |
| MCP-APP-06 | 旧路由迁移 | 双运行验证后才删除 Admin 中正式 Portal 路由 | migration E2E |
| MCP-APP-07 | 无 WebView 替代 | iOS 核心现场能力为原生实现 | source/review/device test |

## 4. 身份、上下文与授权

| ID | 验收项 | 预期 | 证据 |
|---|---|---|---|
| MCP-AUTH-01 | OIDC PKCE | Web/iOS 使用受控授权码流程 | auth E2E |
| MCP-AUTH-02 | Portal Context | ADMIN/NETWORK/TECHNICIAN Context 不可跨用 | negative security tests |
| MCP-AUTH-03 | Capability | 菜单和动作消费服务端 capability/allowedActions | UI + API tests |
| MCP-AUTH-04 | 深链重鉴权 | 打开深链时重新验证身份、范围和当前责任 | security E2E |
| MCP-AUTH-05 | Token 存储 | Web 不持久化长效敏感 Token；iOS 使用 Keychain | security review |
| MCP-AUTH-06 | Context 切换 | 切换后清理相关查询缓存和敏感状态 | client tests |
| MCP-AUTH-07 | 旧师傅失权 | 改派后旧 Context 无法读取或写入新事实 | end-to-end negative tests |

## 5. 契约与客户端生成

| ID | 验收项 | 预期 | 证据 |
|---|---|---|---|
| MCP-CONTRACT-01 | TypeScript Client | 可从 Core OpenAPI 重复生成且无手工漂移 | M247 reproducibility + independent consumer gate |
| MCP-CONTRACT-02 | Swift Client | 可从同一 OpenAPI 重复生成 | M248 reproducibility + Swift 6 independent consumer gate |
| MCP-CONTRACT-03 | Error Model | H5/iOS 对 Problem Details 和 errorCode 语义一致 | contract/client tests |
| MCP-CONTRACT-04 | Page/Action Identity | 同一业务目标复用 pageId/actionCode | registry tests |
| MCP-CONTRACT-05 | Unknown Action | 客户端未知 action 安全降级，不自行猜测 | negative tests |
| MCP-CONTRACT-06 | Schema 版本 | 表单、Evidence 和 WorkPackage 版本可声明与校验 | schema tests |
| MCP-CONTRACT-07 | 兼容元数据 | clientKind/clientVersion/支持能力可观测 | API/telemetry evidence |
| MCP-CONTRACT-08 | Design Token | Web/iOS 从同一无角色假设机器源生成 | M249 reproducibility + CSS/Swift compile gate |

## 6. Technician H5 在线参考实现

| ID | 验收项 | 预期 | 证据 |
|---|---|---|---|
| MCP-H5-01 | Feed/日程/任务详情 | 当前责任、tombstone、深链和 asOf 正确 | Playwright |
| MCP-H5-02 | 联系/预约 | 仅使用已接受权威引用和服务端动作 | contract + E2E |
| MCP-H5-03 | Visit | 明确浏览器定位限制，不伪造原生可信度 | E2E + UX review |
| MCP-H5-04 | 动态表单 | 按冻结 Schema 渲染和校验 | component/E2E |
| MCP-H5-05 | Evidence | 在线会话、checksum、finalize 状态可见 | E2E |
| MCP-H5-06 | 提交/整改 | exact versions、单项补传和多轮历史 | E2E |
| MCP-H5-07 | 状态语义 | 本地、上传、服务器接收、业务接受不混淆 | UX assertions |
| MCP-H5-08 | 能力边界 | 页面明确 H5 不承诺后台上传和完整离线 | content/UX review |
| MCP-H5-09 | 异常恢复 | 403/404/409/412/5xx/投影延迟有可行动提示 | Playwright |

## 7. Technician iOS 工程基础

| ID | 验收项 | 预期 | 证据 |
|---|---|---|---|
| MCP-IOS-01 | 环境配置 | Local/Dev/Test/Staging/Prod 不可误混 | build/config tests |
| MCP-IOS-02 | 登录和刷新 | 登录、刷新、过期、注销和撤销可靠 | XCTest/XCUITest |
| MCP-IOS-03 | Keychain | Token/密钥不进入普通偏好或日志 | security tests |
| MCP-IOS-04 | Swift Client | 生成客户端与服务端契约一致 | CI |
| MCP-IOS-05 | Trace/日志 | 错误可定位且无 PII/Token/文件路径 | log review/tests |
| MCP-IOS-06 | 可访问性 | Dynamic Type、VoiceOver、触控区域通过 | accessibility tests |
| MCP-IOS-07 | TestFlight | 内部测试包可安装、升级和回滚 | distribution evidence |

## 8. Technician iOS 在线履约

| ID | 验收项 | 预期 | 证据 |
|---|---|---|---|
| MCP-ONLINE-01 | 当前任务 | 只显示当前 ACTIVE 责任任务 | API + device E2E |
| MCP-ONLINE-02 | 联系预约 | 拨号返回、联系结果、预约修订和并发正确 | device E2E |
| MCP-ONLINE-03 | Check-in | 主动采集时间/GPS/精度，策略可解释 | device/location tests |
| MCP-ONLINE-04 | Visit | 无法施工、中断、Check-out 和版本校验正确 | device E2E |
| MCP-ONLINE-05 | 表单 | 本地草稿、条件、单位和服务端校验一致 | XCTest/device E2E |
| MCP-ONLINE-06 | 相机与资料 | 原生采集、metadata、checksum 和 Slot 规则正确 | real-device tests |
| MCP-ONLINE-07 | 在线上传 | 进度、失败、重试、finalize 和服务端校验正确 | upload tests |
| MCP-ONLINE-08 | 提交门禁 | 必需表单/资料/Visit 未满足时不能误完成 | E2E |
| MCP-ONLINE-09 | 整改 | 仅处理驳回项，产生新 revision 并保留历史 | E2E |

## 9. iOS 离线与本地数据

| ID | 验收项 | 预期 | 证据 |
|---|---|---|---|
| MCP-OFF-01 | 加密数据库 | 数据库和密钥受系统安全存储保护 | security/device tests |
| MCP-OFF-02 | DB 迁移 | 带未同步数据升级不丢失、不破坏状态 | migration tests |
| MCP-OFF-03 | WorkPackage | 最小化、版本锁定、过期和签名/摘要校验 | contract/device tests |
| MCP-OFF-04 | LocalDraft | 断网、杀进程、重启不丢表单和采集事实 | recovery tests |
| MCP-OFF-05 | OfflineCommand | deviceCommandId、依赖、幂等和重试正确 | integration tests |
| MCP-OFF-06 | Sync Center | 待发送、处理中、冲突、失败、完成可解释 | device E2E |
| MCP-OFF-07 | 版本冲突 | 不使用最后写入覆盖，提供受控解决路径 | conflict tests |
| MCP-OFF-08 | 多账号隔离 | 不同账号不共享工作包和文件 | security tests |
| MCP-OFF-09 | 注销/撤销 | 未同步资料进入受控恢复，不静默删除 | device tests |

## 10. 上传与文件

| ID | 验收项 | 预期 | 证据 |
|---|---|---|---|
| MCP-UP-01 | 后台上传 | App 进入后台后按系统约束继续或恢复 | real-device tests |
| MCP-UP-02 | 断点续传 | 分片重试不创建重复 EvidenceRevision | integration tests |
| MCP-UP-03 | checksum | 同文件/同命令绑定既有结果 | idempotency tests |
| MCP-UP-04 | 杀进程恢复 | 重启后队列、进度和依赖可恢复 | real-device tests |
| MCP-UP-05 | 网络策略 | Wi-Fi/蜂窝/暂停/继续按策略执行 | device tests |
| MCP-UP-06 | 磁盘不足 | 拍摄前预警，已有数据不被破坏 | device tests |
| MCP-UP-07 | 文件清理 | 服务器确认及本地策略满足后才清理 | lifecycle tests |
| MCP-UP-08 | 视频预算 | 大小、时长、压缩和失败原因可解释 | device tests |

## 11. 改派、撤权与安全

| ID | 验收项 | 预期 | 证据 |
|---|---|---|---|
| MCP-REV-01 | tombstone | 责任变化能到达 H5/iOS | integration tests |
| MCP-REV-02 | assignmentVersion | 旧命令被服务端拒绝 | negative tests |
| MCP-REV-03 | authorityVersion | 权限变化后旧授权不可继续写 | security tests |
| MCP-REV-04 | 本地隔离 | 未上传资料不自动归入新师傅任务 | device E2E |
| MCP-REV-05 | 敏感数据 | 旧师傅不继续获取联系方式和新资料 | security E2E |
| MCP-REV-06 | 设备撤销 | 撤销后会话和可清理数据按策略处理 | device tests |
| MCP-REV-07 | 日志与诊断 | 无 PII、Token、照片路径和表单敏感值 | audit |

## 12. 跨端业务闭环

| ID | 验收项 | 预期 | 证据 |
|---|---|---|---|
| MCP-E2E-01 | 派单/改派 | Admin/Network/旧师傅/新师傅一致 | program E2E |
| MCP-E2E-02 | 预约并发 | Network/H5/iOS 同时操作不覆盖修订 | concurrency E2E |
| MCP-E2E-03 | 现场提交 | iOS/H5 事实进入 Admin 工作区和审核 | program E2E |
| MCP-E2E-04 | 审核整改 | Admin 驳回后 H5/iOS 精确补传 | program E2E |
| MCP-E2E-05 | 异常恢复 | 失败进入可恢复 Task/Exception，不只改状态 | program E2E |
| MCP-E2E-06 | Freshness | 命令成功与投影延迟可区分 | E2E |
| MCP-E2E-07 | Trace | 跨端操作可通过 trace/correlation 定位 | observability evidence |

## 13. 版本兼容与发布

| ID | 验收项 | 预期 | 证据 |
|---|---|---|---|
| MCP-VER-01 | 最低客户端版本 | 服务端可声明最低支持版本 | contract tests |
| MCP-VER-02 | Action 支持 | 不向不支持必需 action 的客户端派任务 | assignment tests |
| MCP-VER-03 | Schema 支持 | 不兼容表单/Evidence/WorkPackage 安全失败 | compatibility tests |
| MCP-VER-04 | 强制升级 | 升级提示不导致任务被错误接受 | client E2E |
| MCP-VER-05 | 灰度 | cohort/feature gate 可独立控制 | rollout evidence |
| MCP-VER-06 | 回滚 | Web/iOS/契约回滚路径已演练 | release rehearsal |

## 14. 试点验收

| ID | 验收项 | 预期 | 证据 |
|---|---|---|---|
| MCP-PILOT-01 | 设备矩阵 | 试点设备和系统版本有记录 | matrix |
| MCP-PILOT-02 | 弱网场景 | 地下车库/网络切换/无网恢复通过 | field test |
| MCP-PILOT-03 | 真实工单 | 受控真实流程完成且可审计 | pilot report |
| MCP-PILOT-04 | 支持流程 | 崩溃、上传、同步和失权问题可诊断 | runbook |
| MCP-PILOT-05 | 培训 | 师傅理解本地/上传/服务端状态 | sign-off |
| MCP-PILOT-06 | 指标 | 完成率、失败率、恢复率、冲突率可统计 | dashboard/report |
| MCP-PILOT-07 | 扩大 Gate | P0/P1 问题关闭后才扩大 cohort | approval record |

## 15. 并发 Agent 和里程碑门禁

| ID | 验收项 | 预期 | 证据 |
|---|---|---|---|
| MCP-GOV-01 | 最新主干 | 每个新里程碑从最新 master 创建 | git ancestry |
| MCP-GOV-02 | 编号唯一 | 不与活跃 M245 或其他 Agent 冲突 | PR/branch review |
| MCP-GOV-03 | 非长堆叠 | PR 不携带大量已合并历史 | compare evidence |
| MCP-GOV-04 | 状态准确 | 设计 Accepted 与实现 Implemented 明确区分 | docs gate |
| MCP-GOV-05 | 未实现范围 | 离线、Android、通知等未完成项不被隐藏 | milestone docs |
| MCP-GOV-06 | 合并前更新 | 基于旧基线的文档 PR 在合并前更新到最新 master | merge-base/CI |

## 16. 程序退出标准

全部适用 P0 条目达到 `PASS`，并且：

1. Admin、Network、Technician H5 和 Technician iOS 可独立构建发布；
2. H5 在线参考实现覆盖现场主流程和异常态；
3. iOS 在线和离线现场闭环通过真实设备验证；
4. 改派失权、后台上传、升级迁移和冲突处理通过；
5. TestFlight 小 cohort 试点通过扩大发布 Gate；
6. Android、Consumer、External 和 Settlement 的 Deferred/Proposed 状态保持准确。
