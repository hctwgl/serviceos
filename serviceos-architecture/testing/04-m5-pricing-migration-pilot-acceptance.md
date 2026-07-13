---
title: M5 事实、试算、迁移与试点验收矩阵
version: 0.1.0
status: Proposed
---

# M5 事实、试算、迁移与试点验收矩阵

## 1. 履约事实与试算

| ID | Priority | 场景 | 前置 | 操作 | 预期证据 |
|---|---|---|---|---|---|
| FACT-001 | P0 | 标准事实提取 | 表单、Visit、资料审核已确认 | 执行提取 | 来源版本、策略、trace 和事实完整 |
| FACT-002 | P0 | 多来源冲突 | 表单长度与物料长度不同 | 提取 | FACT_SOURCE_CONFLICT，不最后写入覆盖 |
| FACT-003 | P0 | 零与缺失 | 一个真实 0，一个无来源 | 建 snapshot/试算 | 0 可计价，缺失按策略 NOT_CALCULABLE |
| FACT-004 | P0 | 事实更正 | 已确认线缆长度错误 | 审批更正 | 旧事实保留、新事实替代、影响分析创建 |
| FACT-004A | P0 | 更正与结算收集竞态 | validated run 可被收集 | 并发提交更正与 collect | 更正事务先建 hold；旧 run 不可能新增有效 line，释放/转换有审计 |
| FACT-005 | P0 | 事实集合冻结 | S1 已用于试算 | 新事实产生 | S1 不变，新试算使用 S2 |
| FACT-006 | P0 | 资料失效影响 | 可计价事实依赖资料 V1 | V1 被受控作废 | 事实/试算影响可追踪，不静默改金额 |
| CALC-001 | P0 | 确定性试算 | 相同 fact set/context/plan/engine | 重复执行 | 金额、明细、trace 摘要一致 |
| CALC-002 | P0 | 双向隔离 | 同一 FactSet | 分别对上/对下计算 | 独立 context/plan/run/items，无金额串用 |
| CALC-003 | P0 | 阶梯与舍入边界 | 免费量临界、阶梯边界 | 计算 | 与发布样例一致，中间值可解释 |
| CALC-004 | P0 | 事实更正重算 | 已有 validated run | 更正事实 | 旧 run STALE/影响，新 run 只追加 |
| CALC-006 | P0 | 影子无副作用 | SHADOW 模式 | 批量历史试算 | 无 Statement lock/财务/通知/车企写回 |
| CALC-007 | P1 | 新旧价格比较 | 当前/候选版本 | 批量 comparison | 事实/规则/明细/舍入差异分类 |
| CALC-008 | P0 | 权限隔离 | 客服无成本权限 | 查询对下 run | 拒绝/脱敏且有审计 |
| CALC-009 | P0 | 服务端解析价格上下文 | 工单锁定配置和有效合同 | 普通请求试图提交候选 plan/party/contract | 字段被拒绝；服务端唯一解析并保存 trace |
| CALC-010 | P0 | 影子结果不可结算 | 候选 override run 已 VALIDATED | 评估资格/collect | SHADOW_RESULT_NOT_SETTLEABLE，不能创建有效 line |

## 2. 对账与结算边界

| ID | Priority | Milestone | 场景 | 前置 | 操作 | 预期证据 |
|---|---|---|---|---|---|---|
| SET-001 | P0 | FORMAL_SETTLEMENT | 资格失败 | run 缺车企确认 | 收集批次 | 不纳入且原因明确 |
| SET-002 | P0 | FORMAL_SETTLEMENT | 防重复行 | ChargeItem 已在有效 Statement | 再次 collect | 唯一约束冲突/幂等，不重复金额 |
| SET-003 | P0 | FORMAL_SETTLEMENT | 方向隔离 | 同工单双向 run | 创建批次 | 对上/对下对象、行、权限独立 |
| SET-004 | P0 | FORMAL_SETTLEMENT | 单行争议 | Statement 已提交 | 对一行异议 | Case+Task，其他行保持确认能力 |
| SET-005 | P0 | FORMAL_SETTLEMENT | 调整唯一计入 | 已确认 line | 核减/补差 | 只创建 Adjustment；line XOR 引用，不派生第二个可结算 ChargeItem |
| SET-006 | P0 | FORMAL_SETTLEMENT | 锁定不可变 | Statement LOCKED | 普通撤回/重算替换 | 拒绝并要求 Adjustment |
| SET-006A | P0 | FORMAL_SETTLEMENT | 锁定后事实更正 | Statement 已 LOCKED | 更正事实 | 原 line 不变，只生成 Adjustment 处理要求 |
| SET-007 | P1 | FORMAL_SETTLEMENT | 财务交接幂等 | 锁定 Statement | 响应丢失后重试 | 一个外部业务结果和完整回执 |
| SET-008 | P0 | M5 | 试算导出标识 | validated run | 导出 | 明确 SHADOW/未结算，不产生锁定 |
| SET-009 | P0 | M5 | 正式结算功能未启用 | M5 默认 feature set | 调用 Statement/Adjustment/Handoff 写命令 | FEATURE_NOT_ENABLED 且零领域写入/副作用 |

## 3. 迁移

| ID | Priority | 场景 | 前置 | 操作 | 预期证据 |
|---|---|---|---|---|---|
| MIG-001 | P0 | 批次重跑幂等 | snapshot 固定 | 同批执行两次 | 目标 ID 相同，无重复对象 |
| MIG-002 | P0 | 源记录改变 | 同 source key 摘要变化 | 重跑 | 幂等冲突并人工确认 |
| MIG-003 | P0 | 字段血缘 | 旧字段转换为标准字段 | 查询 lineage | 源快照/表/行/字段/脚本可反查 |
| MIG-004 | P0 | 在途恢复点 | 旧工单待安装审核 | 迁移 | 只创建批准阶段/Task，不伪造历史事件 |
| MIG-005 | P0 | 无法映射状态 | 旧状态语义未知 | dryRun | 标记阻塞，留旧系统完成 |
| MIG-006 | P0 | 文件校验 | 资料含缺失/损坏 | 复制验证 | checksum、缺失清单、MIGRATED_LEGACY 标记 |
| MIG-007 | P0 | 锁定历史金额 | 已结算工单 | 迁移 | MIGRATED_LOCKED，不重算覆盖 |
| MIG-008 | P0 | 增量水位 | 全量后仍有新增 | 追平增量 | 无遗漏/重复，水位可证明 |
| MIG-009 | P1 | 数据权限 | 迁移旧网点工单 | 用户查询 | 新数据范围正确，越权拒绝 |
| MIG-010 | P0 | 敏感暂存清理 | 迁移签署完成 | 到期清理 | 暂存删除审计、正式数据保留正确 |

## 4. 灰度、切换与回退

| ID | Priority | 场景 | 前置 | 操作 | 预期证据 |
|---|---|---|---|---|---|
| CUT-001 | P0 | cohort 确定性 | 相同业务键/规则版本 | 多次 evaluate | 命中结果一致并可解释 |
| CUT-002 | P0 | 新工单锁定 authority | cohort PILOT_ACTIVE | 创建工单 | ServiceOS 唯一权威和副作用所有者 |
| CUT-003 | P0 | 影子副作用隔离 | SHADOW | 尝试通知/回传/锁定 | Fence 拒绝并审计 |
| CUT-004 | P0 | 普通命令双写防护 | 工单已归 ServiceOS | 旧系统/影子任务尝试改 Task、资料或事实 | 领域事务校验 authorityVersion，业务表和 Outbox 均无写入 |
| CUT-005 | P0 | 扩大只影响新工单 | 10%→30% | 发布 cohort V2 | 已有工单 authority 不漂移 |
| CUT-006 | P0 | 最终增量 | 切换窗口 | 停旧写、追平、切路由 | 水位和 smoke test 通过 |
| CUT-007 | P0 | 安全回退 | 尚在回退窗口 | 执行 rollback | ServiceOS 增量反向同步、路由恢复、无双写 |
| CUT-008 | P0 | 不安全回退阻止 | 外部副作用无法反向同步 | 请求 rollback | ROLLBACK_NOT_SAFE，转向前修复 |
| CUT-009 | P0 | 门禁阻止扩大 | P0 异常未闭环 | 请求 EXPAND | No-go/Hold，cohort 不变 |
| CUT-010 | P1 | disaster recovery | 试点数据和队列 | 恢复演练 | 达到签署 RPO/RTO、无重复副作用 |
| CUT-011 | P0 | 权威切换在途副作用 | 旧版本存在待发送/结果未知 delivery | 切换 authority | 先 DRAINING；排空/对账前不能发布新版本，无旧权威新增发送 |
| CUT-012 | P0 | 最终门禁 TOCTOU | 执行器取得 ALLOW 后 authorityVersion 改变 | 尝试发送/激活/锁定 | 最终事务拒绝；外部幂等键含版本且不重复副作用 |
| CUT-013 | P0 | 回退计划完整性 | cohort 请求回退 | prepare/execute/verify | 副作用清单、反向同步、水位、阻塞、审批和验证齐全；BLOCKED 不执行 |

## 5. 生产试点证据

每个 P0 场景至少提供一种：自动化测试报告、历史回放报告、影子对比、预生产故障注入、生产 cohort 观察或正式演练记录。

门禁报告至少包含：

- 测试版本、配置包、价格、映射和 cohort 版本；
- 样本和生产业务量；
- 未解释事实/金额差异；
- 重复副作用计数；
- P0/P1 OperationalException；
- 数据迁移和附件差异；
- SLI/SLO 窗口；
- 业务、财务、技术和安全签署。

## 6. 退出条件

- 所有 `Milestone=M5` 的 P0 及其他未单列 Milestone 的 M5 P0 自动化/演练通过；`FORMAL_SETTLEMENT` 用例属于二期启用门禁，不阻塞 M5 试算试点，但启用前必须全部通过；
- 真实脱敏历史对上/对下结果完成回放和差异解释；
- 影子运行无真实副作用；
- cohort 小流量真实履约闭环并达到签署门禁；
- 迁移数据、文件、待办和金额完成业务/财务核对；
- 未解释金额差异为零或在书面接受阈值内；
- 回退和灾备演练通过；
- 无重复派单、通知、车企回传或正式结算；
- 试点负责人签署扩大/正式切换；
- 遗留问题进入有负责人、期限和风险的债务清单。
