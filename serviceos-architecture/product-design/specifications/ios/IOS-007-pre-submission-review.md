# IOS-007 提交前检查

状态：Accepted
设计等级：A

## 1. 页面定位

“提交前检查”是当前责任师傅结束本次现场任务前的最终业务门禁。它汇总 Task、Visit、FieldOperation、FormSubmission、EvidenceSlot、EvidenceRevision、上传、客户确认、异常和责任事实，让师傅在提交前清楚知道：

1. 哪些完成条件已经由平台确认；
2. 哪些成果仍只保存在本机；
3. 哪些资料仍在上传、扫描或机器校验；
4. 哪些缺失项、冲突或规则正在阻止提交；
5. 本次提交将冻结哪些精确版本；
6. 提交成功后当前 Task 会进入什么业务结果；
7. 是否还会进入审核、整改、外部回传或后续任务。

页面不是一个“全部打勾就结束工单”的本地清单。最终是否允许创建资料快照和完成 Task，必须由服务端使用当前权威事实重新计算。

## 2. 产品边界

IOS-007 负责：

- 展示完整的提交前检查清单；
- 区分本机预检和平台权威检查；
- 定位到 IOS-003 至 IOS-006、IOS-008 和 IOS-010 修复缺失项；
- 展示将使用的最新有效 FormSubmission 和 Evidence Revision；
- 创建不可变 `TASK_SUBMISSION` EvidenceSetSnapshot；
- 在精确版本和幂等边界内完成当前 HUMAN Task；
- 处理 Snapshot 已创建但 Task 完成失败等部分阶段结果；
- 在有效工作包允许时保存有依赖的离线提交命令；
- 展示安全完成收据和服务端返回的下一业务动作；
- 明确 Task 完成、Visit 签退、审核通过和工单完成的区别。

IOS-007 不负责：

- 编辑表单或资料正文；
- 客户端自行挑选不符合资格的 Revision；
- 伪造 Snapshot purpose、成员摘要或 Task 结果引用；
- 通过置灰检查项或自由文本绕过阻止规则；
- 将任务完成等同于审核通过、工单完成或履约结束；
- 在没有真实 FieldOperation `operationRefs` 时签退 Visit；
- 强制通过 Rule、ExecutionGuard、责任或权限门禁；
- 修改已创建 Snapshot、FormSubmission、Revision 或完成收据；
- 在离线命令尚未被平台接受时显示“提交完成”。

## 3. 访问与实时校验

在线读取和提交必须同时满足：

```text
authenticated principal
+ trusted Technician Context
+ ACTIVE TechnicianProfile
+ ACTIVE NetworkTechnicianMembership
+ current ACTIVE technician responsibility
+ required capability
+ RUNNING HUMAN Task
+ no blocking ExecutionGuard
+ current task allowed-action
```

页面打开、返回前台、完成一项修复和点击最终提交时，都要刷新服务端权威检查。

服务端必须重新验证：

- 当前责任网点和师傅；
- Task、Visit 和 FieldOperation 状态；
- Task 并发版本；
- 工单绑定的配置、流程、表单和资料版本；
- 当前 Evidence resolution generation；
- FormSubmission 和 EvidenceRevision 的资格；
- 预约、客户确认和异常政策；
- 上传、扫描和机器校验状态；
- 冻结 Rule 和完成门禁；
- 操作者权限和 Project Scope。

客户端曾经显示“可以提交”不构成服务端授权。

## 4. 页面结构

```text
任务与同步摘要
→ 提交结论：可以提交 / 尚有问题 / 等待平台 / 发生冲突
→ 表单完整性
→ 资料完整性
→ 上传、扫描和机器校验
→ Visit、现场操作和责任
→ 客户确认、预约和异常
→ Rule、Guard 和业务警告
→ 将冻结的提交内容摘要
→ 提交影响与下一阶段
→ 固定底部主操作
```

页面默认只展开阻止项和警告；已通过项以简洁摘要展示，避免用户在长清单中找不到真正问题。

## 5. 页面级读模型

建议使用：

```text
TechnicianSubmissionReviewView
├── taskSummary
├── responsibilitySummary
├── visitSummary
├── fieldOperationSummary
├── formReadiness
├── evidenceReadiness
├── transferReadiness
├── confirmationReadiness
├── exceptionReadiness
├── ruleAndGuardReadiness
├── localPendingSummary
├── proposedSubmissionSummary
├── completionImpact
├── readinessVersion
├── allowedActions
└── blockingReasons
```

`readinessVersion` 或等价令牌绑定本次检查所使用的 Task、责任、表单、资料 resolution 和规则版本。任何相关事实变化后，旧检查结果立即过期。

前端不能自行聚合多个接口后决定是否允许提交，也不能根据所有卡片颜色计算本地 `canSubmit` 取代服务端 allowed-action。

## 6. 检查结果语义

每项检查使用以下业务状态：

- 平台已确认满足；
- 本机预检通过，等待平台确认；
- 仍在上传或处理；
- 需要补充；
- 需要用户确认警告；
- 被业务规则阻止；
- 发生版本或责任冲突；
- 当前无法获得权威结果；
- 当前任务不适用。

“当前无法获得权威结果”不能显示为已通过。“本机预检通过”也不能使用与“平台已确认满足”相同的绿色完成样式。

## 7. 表单完整性

表单区检查：

- Task 是否要求 FormVersion；
- 是否存在同一 Task、Project 和锁定 FormVersion 的最新有效 FormSubmission；
- FormSubmission 是否为服务端 `VALIDATED`；
- submissionVersion 和 contentDigest 是否仍为当前选择；
- 条件、必填、类型和跨字段规则是否全部通过；
- 预填冲突是否已经处理；
- 是否存在更新的本机草稿尚未提交；
- 是否存在整改要求或被替代版本；
- 当前提交是否会改变资料条件事实。

如果本机草稿比平台 FormSubmission 更新，页面必须明确显示：

> 还有较新的本机修改尚未提交，当前平台版本不能代表本次作业结果。

用户返回 IOS-006 处理，不能直接忽略本机修改或默认为放弃。

`VALIDATED` 只表示结构和业务校验通过，不表示审核通过。

## 8. 资料完整性

资料区必须使用当前权威 Evidence resolution generation 检查：

- 所有活动 EvidenceSlot；
- 每个槽位 minCount 和 maxCount；
- 每个 EvidenceItem 当前可提交 Revision；
- Revision 是否属于同一 Task、Project 和活动槽位；
- Revision 是否为 `VALIDATED` 且未失效；
- 同一 Item 是否只选择一个 Revision；
- 是否存在缺失、重复或跨任务资料；
- 是否存在 `REVIEW_REQUIRED` 条件资料待处置；
- 是否存在整改中或尚未复核的 Revision；
- 是否存在更新的本机文件未进入平台。

客户端可以展示建议成员，但 Snapshot 创建时服务端必须重新验证资格和数量，任何一个成员失败都整体拒绝，不创建部分 Snapshot。

## 9. 上传、扫描和机器校验

以下状态必须阻止或按项目规则明确处理：

- 本机文件未排队；
- 上传会话待创建；
- 上传中或暂停；
- Finalize 尚未确认；
- 文件处于 STORED 或 VALIDATING；
- 安全扫描未完成；
- 文件被 QUARANTINED；
- 机器校验失败或需要人工确认；
- checksum 或本机文件状态冲突；
- 最终失败但尚未补拍。

本机上传进度不能替代平台文件状态。IOS-007 只展示摘要和“处理上传”入口，具体传输进入 IOS-008。

隔离、未扫描或未达到 Snapshot 资格的 Revision 不得被选择进入提交集合。

## 10. Visit 与到场检查

Visit 区检查：

- 当前任务是否需要到场；
- 当前责任下是否存在唯一有效 Visit；
- 平台是否已确认 Check-in；
- 到场范围例外是否已经完成必要说明、资料或批准；
- Visit 是否已中断、冲突或进入不允许提交的状态；
- 当前 Visit 版本是否仍有效；
- 是否存在离线签到待确认。

本机记录到场但平台尚未确认时，页面只能显示“等待平台确认”，不能把 Visit 检查标为通过。

## 11. FieldOperation 检查

现场操作区检查：

- 当前 Task 要求的 FieldOperation 是否存在；
- 操作类型、Task、Visit 和责任是否一致；
- 是否仍处于进行中、已提交、整改或异常状态；
- 必要业务结果是否已经形成不可变提交；
- 是否存在多个互斥进行中操作；
- 是否存在真实、权威且可用于后续 Visit 生命周期的 operationRefs 映射。

当前 Accepted 契约未建立 Task、Snapshot 与已完成 FieldOperation `operationRefs` 的权威映射时，IOS-007 不生成占位引用，也不把 Task、Snapshot 或 Evidence Revision URI 冒充现场操作。

因此，本页面可以完成已满足既有门禁的 Task，但不能顺带伪造 Visit 签退。Visit 签退和离场仍是独立业务事实，待权威 operationRefs 映射接受后再开放正式产品动作。

## 12. 责任、权限和 Guard

责任区检查：

- 当前责任网点是否仍有效；
- 当前责任师傅是否仍为本人；
- TechnicianProfile 和网点关系是否有效；
- Task 是否仍为 RUNNING HUMAN；
- 当前用户是否具有完成 Capability；
- Project Scope 是否覆盖当前任务；
- 是否存在 ACTIVE ExecutionGuard；
- 是否存在改派、撤权或设备撤销。

任一失败时停止提交并隐藏不再必要的敏感数据。客户端不能用离线工作包中的旧责任绕过在线实时校验。

## 13. 预约、确认和异常

根据项目政策检查：

- 当前 Appointment 是否处于允许完成的状态；
- 客户确认、签名或授权是否已经由正确主体完成；
- 师傅是否错误地代替客户确认；
- 提前、迟到、爽约或改约是否已经完成处置；
- 无法施工、中断或范围外到场是否具有标准原因；
- 所需异常资料和协调结果是否齐全；
- 是否存在尚未解决的安全阻断；
- 异常是否要求创建后续预约或协调任务。

异常说明不能只是一段自由文本。没有满足项目政策时，页面提供返回 IOS-003、IOS-004、IOS-005 或 IOS-006 的精确修复入口。

## 14. Rule 与完成门禁

Task 冻结 Rule 时，Snapshot 创建和 Task 完成都必须使用冻结 Bundle 中的同一规则失败关闭。

规则结果至少区分：

- 允许；
- 允许但需要确认警告；
- 需要批准；
- 阻止；
- 当前无法求值。

`BLOCK`、`REQUIRE_APPROVAL` 或求值失败不能由客户端降级。当前没有已接受的强制通过旁路时，页面不提供“仍然提交”按钮。

ExecutionGuard、Task guard 和领域完成校验属于不同门禁，页面可以统一展示业务原因，但不能在客户端合并成一个可手工解除的开关。

## 15. 业务警告确认

可提交警告必须由服务端明确标记为允许确认，不能由前端自行把错误变成警告。

确认警告时记录：

- 稳定警告代码；
- 中文业务说明；
- 用户看到的影响；
- 必要确认选项；
- 实际操作者；
- 确认时间；
- 使用的 readinessVersion。

任何相关事实变化后，旧警告确认失效，需要重新检查和确认。

## 16. 将提交的内容摘要

最终确认前展示业务化摘要：

- 表单名称和业务版本；
- FormSubmission 提交序号；
- 资料槽位满足情况；
- 将冻结的资料项和版本数量；
- 现场操作结果摘要；
- 客户确认或签名状态；
- 到场和异常结果；
- 本次任务完成后的下一业务阶段；
- 是否会创建审核任务；
- 是否仍需等待资料验证、外部回传或平台处理。

普通界面不展示 resultRef、digest、inputVersionRefs、Snapshot UUID 或成员技术引用。

## 17. 提交影响

页面必须明确说明：

- FormSubmission 和 EvidenceSetSnapshot 是不可变版本；
- 后续补传不会改变已经冻结的 Snapshot；
- Task 完成后不能回到普通草稿直接修改；
- 更正或驳回通过新 Submission、Revision、Snapshot、Review 和 Correction 形成历史链；
- 完成当前 Task 可能创建下一 Task 或进入审核；
- 完成当前 Task 不等于审核通过；
- 完成当前 Task 不一定等于工单完成；
- 完成当前 Task 不等于 Visit 已签退。

页面根据服务端 `completionImpact` 使用当前项目的真实业务语言，不能固定显示“工单已完成”。

## 18. 在线提交编排

在线链路为：

```text
刷新权威 readiness
→ 确认没有本机待同步成果
→ 确认最新 VALIDATED FormSubmission
→ 创建 TASK_SUBMISSION EvidenceSetSnapshot（如需要）
→ 再次校验 Task allowed-action 和版本
→ 完成当前 HUMAN Task
→ 返回安全完成收据
→ 刷新服务端下一业务状态
```

表单-only、资料-only 和双输入任务分别遵守：

### 18.1 表单-only

使用同 Task、Project 和锁定 FormVersion 的权威 FormSubmission 作为主结果，不携带无关 Snapshot。

### 18.2 资料-only

使用同 Task、Project、当前 resolution 和 `purpose=TASK_SUBMISSION` 的 EvidenceSetSnapshot，不携带无关 FormSubmission。

### 18.3 表单 + 资料

FormSubmission 是主结果，同时精确引用同 Task、Project 的 FormSubmission 和 EvidenceSetSnapshot。缺失、重复、错误类型、错误摘要或跨 Task 引用全部失败关闭。

客户端只提交已选择的对象标识和资源版本。规范 URI、digest、purpose、成员摘要和 `inputVersionRefs` 由服务端重读权威对象后构造，不能由客户端伪造。

## 19. Snapshot 创建

Snapshot 创建命令只提交当前页面确认的 Revision 标识，不提交：

- purpose；
- contentDigest；
- eligibilitySummary；
- member digest；
- uploader 或 reviewer；
- resultRef 或 inputVersionRefs。

服务端只接受：

- 同一 tenant、Project 和 Task；
- 当前 resolution generation；
- 活动 EvidenceSlot；
- `VALIDATED` 且未失效 Revision；
- 同一 Item 最多一个 Revision；
- 满足全部 minCount 和 maxCount；
- 通过冻结 Rule 门禁的完整成员集合。

任一成员失败时整体拒绝，不产生部分 Snapshot。

创建后 Snapshot 及成员不可修改。后续资料变化需要新 Snapshot。

## 20. Snapshot 已创建但完成失败

Snapshot 和 Task 属于不同聚合，页面不能假装二者必然在同一事务中同时成功。

可能出现：

```text
资料快照已创建
但任务完成因并发、责任、Guard 或规则变化被拒绝
```

页面必须展示真实阶段结果：

- Snapshot 已安全冻结；
- Task 仍未完成；
- 失败原因；
- 当前是否可复用该 Snapshot；
- 是否需要重新检查或创建新 Snapshot；
- 本机是否仍有未同步成果。

Snapshot 仍满足当前 Task、resolution、Rule 和成员资格时可以复用；任一相关事实变化后必须重新检查。禁止删除 Snapshot、修改成员或把已冻结快照伪装成未发生。

## 21. Task 完成事务

Task 完成必须在内核同一事务中原子提交：

- 当前状态和责任校验；
- 乐观版本；
- FormSubmission / Snapshot 完成门禁；
- Rule 和 Guard；
- Task `COMPLETED`；
- 精确输入版本引用；
- 审计；
- 幂等结果；
- `task.completed` Outbox。

任一校验失败时，不更新 Task、Assignment、审计成功记录、Outbox 或幂等成功结果。

客户端使用独立、稳定的完成 `deviceCommandId` 和带引号资源版本。响应丢失后使用相同幂等键查询或重试，不能生成新命令重复完成。

## 22. 安全完成收据

平台成功后返回并展示最小完成收据：

- 任务已完成；
- 平台确认时间；
- 当前任务业务名称；
- 新的资源版本；
- 下一业务阶段或等待事项；
- 是否已创建审核或后续任务；
- 数据最新时间。

收据不回显：

- resultRef；
- digest；
- inputVersionRefs；
- 内部 Snapshot 或 Submission URI；
- 操作者技术标识；
- 敏感表单值和文件地址。

只有服务端返回权威收据后，页面才能显示“平台已确认提交”。

## 23. 工作流后续状态

Task 完成后，客户端重新读取服务端下一业务状态，不在本地预测流程出口。

可能结果：

- 进入下一现场任务；
- 等待平台审核；
- 等待网点或客户确认；
- 等待资料机器处理；
- 进入外部回传；
- 当前工单完成；
- 需要处理新的异常或整改。

页面只显示服务端已经建立的事实。不能因为流程图上看起来是最后一步就提前显示工单完成。

## 24. Visit 签退边界

Task 完成和 Visit 签退是两个独立命令、两个独立状态和两条审计事实。

Visit 签退至少需要真实已完成 FieldOperation `operationRefs`。在该权威映射尚未成为 Accepted 契约前：

- IOS-007 不展示伪签退按钮；
- 不使用 Task、FormSubmission、Snapshot 或 Evidence Revision URI 冒充 operationRef；
- 不为通过接口校验创建占位现场操作；
- Task 完成收据明确说明到场记录是否仍待后续处理。

正式离场动作需要另行接受 operationRefs 映射、位置政策、离线签退和失败恢复语义后再纳入产品基线。

## 25. 离线预检

有效工作包允许时，IOS-007 可以执行本机预检：

- 锁定表单和资料规则；
- 本机草稿和文件完整性；
- 本机命令依赖；
- 本地可执行的确定性校验；
- 最近已知责任、Visit 和 Task 版本；
- 本机存储和上传准备状态。

离线预检不能权威确认：

- 当前责任仍有效；
- 平台没有新 Guard；
- 最新 Rule 运行结果；
- 资料已经完成服务器扫描和验证；
- Snapshot 资格仍成立；
- Task 仍允许完成。

因此页面必须标记“本机检查结果，截至最近同步”。

## 26. 离线提交队列

只有工作包显式允许完整离线提交链时，按钮文案才可以是：

> 保存并等待同步

不得显示“提交完成”。

命令依赖至少为：

```text
待提交表单命令（如有）
→ 条件资料重解析
→ 资料 Begin / Upload / Finalize
→ 安全扫描和机器校验
→ Snapshot 创建
→ Task 完成
```

每个命令使用独立稳定 `deviceCommandId`，不能用一个幂等键代表整条不同领域命令。

平台逐条确认。任何前置命令失败或冲突时，后续 Snapshot 和 Task 完成停止，不得跳过依赖继续提交。

## 27. 离线同步结果

恢复网络后页面按真实阶段展示：

- 表单已接收；
- 资料正在上传；
- 资料正在验证；
- Snapshot 已创建；
- Task 已完成；
- 某一步被拒绝；
- 需要处理冲突。

部分成功不等于整体完成。已成功建立的不可变对象保留，失败步骤从权威结果恢复；不能回滚历史事实或重新创建重复版本。

责任、预约、Task、Visit、表单、资料、Rule 或 Guard 变化时，旧命令进入 IOS-010，不通过重写版本或更换操作者继续提交。

## 28. 并发与结果过期

以下变化使旧 readiness 和警告确认失效：

- Task、Visit 或 FieldOperation 版本变化；
- 责任或权限变化；
- 新 FormSubmission；
- Evidence resolution generation 变化；
- 新 Revision 或 Revision 作废；
- 上传、扫描或机器校验状态变化；
- Rule 或 Guard 结果变化；
- Appointment、客户确认或异常状态变化；
- 其他设备完成同一 Task。

点击提交时出现并发冲突，页面保留本机上下文并刷新权威状态。若其他设备已经使用同一业务结果完成 Task，服务端返回现有完成事实；若输入不同则进入 IOS-010，不能把两个版本合并成一个完成结果。

## 29. 错误与恢复状态

| 状态 | 页面表现 | 允许动作 |
|---|---|---|
| 可以提交 | 展示最终摘要和影响 | 确认提交 |
| 缺少表单 | 定位具体表单 | 返回 IOS-006 |
| 缺少资料 | 定位具体槽位 | 返回 IOS-006 |
| 文件上传中 | 展示数量和进度摘要 | 进入 IOS-008 |
| 平台验证中 | 说明处理阶段 | 等待、刷新 |
| 本机有未同步成果 | 说明不会自动忽略 | 先同步或处理 |
| Rule 阻止 | 展示业务原因 | 修复、等待批准 |
| Guard 阻止 | 展示负责方和恢复方式 | 等待协调 |
| Snapshot 已创建 | 明确 Task 尚未完成 | 重新检查、继续完成 |
| 完成响应丢失 | 不重复生成命令 | 查询或幂等重试 |
| 平台已确认提交 | 展示安全收据 | 查看下一业务状态 |
| 责任或版本冲突 | 停止提交 | 进入 IOS-010 |
| 无法获取权威结果 | 不显示可以提交 | 重试、联系网点 |

所有错误使用中文业务说明、稳定错误码和明确下一步，不显示原始 HTTP、异常类名或 JSON。

## 30. 命令边界

IOS-007 可以调用：

- 创建 `TASK_SUBMISSION` EvidenceSetSnapshot；
- 完成当前 HUMAN Task；
- 确认可提交业务警告；
- 查询幂等提交结果；
- 有效工作包允许的 Snapshot / Task 完成离线命令。

IOS-007 不提供：

- 通用修改 FormSubmission 或 EvidenceRevision；
- 客户端指定 Snapshot purpose、digest 或资格摘要；
- 客户端指定 resultRef、resultDigest 或 inputVersionRefs；
- 强制忽略 Rule、Guard、责任或权限；
- 直接审核通过；
- 直接关闭工单；
- 无真实 operationRefs 的 Visit 签退；
- 通用“标记全部完成”。

## 31. 安全、隐私和审计

Snapshot 创建、警告确认和 Task 完成必须记录：

- 租户、Project、工单、Task 和 Visit；
- 当前责任网点和师傅；
- 操作者和受信 Context；
- 使用的配置、Rule、resolution 和资源版本；
- FormSubmission、Snapshot 和成员资格摘要；
- 业务动作、警告、阻止原因和稳定错误码；
- 在线、离线和设备命令来源；
- `capturedAt`、`receivedAt` 和服务器时间基准；
- 幂等、事务、审计和 Outbox 结果。

普通日志、埋点和收据不得记录完整表单值、文件正文、客户敏感信息、原始坐标、上传凭据、resultRef、digest 或 inputVersionRefs。

## 32. 无障碍和确认交互

- 检查状态不能只依赖红黄绿色；
- VoiceOver 能读取每个阻止项、警告和修复入口；
- 动态字体放大后保留最终影响和主操作；
- 提交确认使用项目真实业务名称，不使用技术对象术语；
- 处理中禁止重复点击，但提供网络和阶段说明；
- 长清单默认展开问题项并支持快速定位；
- 重要警告需要明确确认，不使用预选勾选框；
- 提交成功使用文字和可访问反馈，不只使用动画或触觉。

## 33. 与其他页面的关系

```text
IOS-005 作业步骤
    → IOS-006 表单与资料采集
    → IOS-007 提交前检查
        → IOS-008 上传中心
        → IOS-009 离线恢复
        → IOS-010 同步冲突
    → 平台确认 Task 完成
    → 审核 / 后续任务 / 外部协同 / 工单完成
```

Admin、Network Portal 和 iOS 读取同一 Task、FormSubmission、EvidenceSetSnapshot、Review 和 Workflow 事实，不建立“iOS 已提交但平台未提交”的平行权威状态。

## 34. 验收标准

### 检查清单

- [ ] 页面级读模型返回服务端权威 readiness；
- [ ] 本机预检和平台确认使用不同状态表达；
- [ ] 表单、资料、上传、Visit、FieldOperation、责任、Rule 和 Guard 均有独立检查；
- [ ] 无法获取权威结果时不能显示可以提交；
- [ ] 每个阻止项提供精确修复入口；
- [ ] 相关事实变化后旧检查和警告确认立即过期。

### Snapshot 和 Task 完成

- [ ] Snapshot 只冻结当前 Task、resolution 和 VALIDATED Revision；
- [ ] 任一成员不合格时不创建部分 Snapshot；
- [ ] 表单-only、资料-only 和双输入门禁分别正确；
- [ ] resultRef、digest 和 inputVersionRefs 由服务端构造；
- [ ] Snapshot 创建不隐式完成 Task；
- [ ] Snapshot 已创建但完成失败时展示真实部分阶段结果；
- [ ] Task、审计、幂等结果和 Outbox 同事务提交；
- [ ] 重复完成命令返回同一权威结果。

### 结果语义

- [ ] 平台返回收据前不显示提交完成；
- [ ] Task 完成不显示为审核通过；
- [ ] Task 完成不默认显示为工单完成；
- [ ] Task 完成不等于 Visit 签退；
- [ ] 下一业务阶段由服务端事实决定；
- [ ] 普通收据不暴露内部引用和摘要。

### 离线和冲突

- [ ] 离线按钮文案为“保存并等待同步”；
- [ ] 不同领域命令使用独立稳定 deviceCommandId；
- [ ] 离线命令严格遵守表单、上传、验证、Snapshot 和完成依赖；
- [ ] 前置失败会停止后续命令；
- [ ] 部分成功对象保留，不伪造整体完成；
- [ ] 责任、版本或多设备冲突进入 IOS-010，不使用最后写入覆盖。

### Visit 与安全

- [ ] 没有真实 operationRefs 时不展示 Visit 签退；
- [ ] 不使用 Task、Snapshot 或 Evidence URI 冒充 operationRef；
- [ ] Rule、Guard、责任和权限不能由客户端绕过；
- [ ] 敏感表单值、文件和内部结果引用不进入日志；
- [ ] 无法确认业务事实时失败关闭。

## 35. 禁止实现

禁止：

- 由前端卡片颜色计算权威 canSubmit；
- 本机清单通过即显示提交完成；
- 自动忽略尚未同步的草稿或文件；
- 选择非 VALIDATED、跨 Task 或停用槽位 Revision；
- 创建部分 EvidenceSetSnapshot；
- 修改已创建 Snapshot 成员；
- 客户端构造 resultRef、digest 或 inputVersionRefs；
- Snapshot 创建成功即把 Task 标为完成；
- 捕获 Task 完成异常后显示默认成功；
- 用新幂等键反复完成同一业务操作；
- 把 Task 完成写成审核通过或工单完成；
- 为签退创建占位 FieldOperation 或 operationRef；
- 离线排队后显示平台已提交；
- 为旧 H5、旧开发数据或假设客户端保留双轨提交模型。
