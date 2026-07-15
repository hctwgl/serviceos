# Agent 上下文路由

本目录把“当前任务需要读取什么”从长篇历史文档中分离出来，降低每个里程碑的冷启动检索成本。

## 固定启动顺序

开始任务时默认只读取：

1. 根目录 `AGENTS.md`；
2. `current-baseline.md`；
3. 当前任务对应的 `milestones/<ID>.md`；
4. Context Pack 列出的模块卡片和直接事实源。

只有出现契约冲突、状态机变化、跨模块依赖、破坏性迁移、安全边界或历史兼容问题时，才扩大到完整 Architecture Book、历史里程碑和相邻领域。

## 目录职责

```text
context/
├── current-baseline.md       当前基线的轻量快照
├── milestones/               每个里程碑或工程改进的 Context Pack
└── modules/                  模块事实所有权、入口和测试路由
```

- `current-baseline.md` 只保存当前事实和最近变化，不保存完整历史；
- `milestones/<ID>.md` 冻结目标、非目标、必读文件、按需文件、影响模块和验证范围；
- `modules/catalog.tsv` 提供脚本可读的模块索引；
- 详细历史仍保留在 `docs/implementation-status.md`、实现文档和验收矩阵中，但不再作为每次任务的默认首读全文。

## 命令

```bash
bash scripts/plan-context.sh CTX-001
bash scripts/plan-impact.sh master
bash scripts/init-agent-session.sh CTX-001
```

`plan-context.sh` 输出本次任务的最小阅读集合和验证提示；`plan-impact.sh` 根据 Git diff 推导受影响模块和最低风险等级；`init-agent-session.sh` 创建本地、被 Git 忽略的会话缓存。

## Context Pack 规则

每个新里程碑在编码前应创建 Context Pack，至少包含：

- 目标和明确非目标；
- 风险等级和初始阅读预算；
- 影响模块；
- 必须阅读和按需阅读文件；
- 已冻结决策；
- 代码入口；
- 必须验证；
- 需要确认的问题。

Context Pack 是路由索引，不复制源文档的完整语义。源文档更新后应同步修正引用，不在 Context Pack 中维护另一套业务规则。
