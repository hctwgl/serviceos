---
title: Mxxx <一句话标题>
status: Draft
milestone: Mxxx
---

# Mxxx <一句话标题>

## 目标

<本里程碑要解决的一个问题；保持最小可靠纵向切片。>

## 范围与非目标

- 范围：
- 明确不做：

## 事实源

<Accepted 设计章节、ADR、契约、上位里程碑文档的精确路径。>

## 设计要点

<事务边界、幂等键、锁顺序、授权与失败关闭语义；复杂处用中文说明为什么。>

## 已实现

<按里程碑声明范围逐条列出，可追溯到代码/迁移/契约。>

## 明确未实现

<不得删除或模糊；后续里程碑补齐时回写指向。>

## 工程证据

<Flyway 版本、OpenAPI/事件 Schema 版本、PostgreSQL IT、MVC Security、契约/客户端生成、ArchitectureTest。>

## 验证命令

```bash
bash scripts/agent-verify.sh test <Class>
bash scripts/agent-verify.sh it <Class>
```
