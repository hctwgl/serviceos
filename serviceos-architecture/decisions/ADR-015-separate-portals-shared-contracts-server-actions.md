# ADR-015：Portal 独立，契约共享，动作由服务端决定

- 状态：Proposed
- 日期：2026-07-13

## 背景

总部、网点和师傅使用场景差异显著。把所有页面放入一个应用并按角色隐藏菜单，会把权限假设、发布节奏和现场离线复杂度耦合在一起；完全复制三套业务逻辑又会造成动作语义漂移。

## 决策

Admin Web、Network Web 和 Technician App 作为独立 Portal、独立构建与发布单元。它们共享 OpenAPI/事件生成类型、错误模型、设计 token 和无角色假设的基础组件，不共享整页路由和含角色假设的业务 store。

资源可执行动作由服务端 `allowed-actions` 返回 actionCode、inputSchema、obligations 和资源版本。前端使用显式 renderer registry；未知 actionCode 安全降级，不能使用万能命令表单。

## 约束

- 菜单和按钮不是授权边界；
- 所有命令服务端复核 capability、scope、Task action、field policy、feature 和 authorityVersion；
- Network Portal 只读取当前网点范围，不显示其他网点和内部/对上价格；
- Technician App 必须拥有独立离线、工作包、上传和冲突模型；
- push/deep link 只提示重新授权查询，不把 payload 当权威状态；
- Portal 共享组件不得嵌入角色名或固定项目规则；
- 新 actionCode 先更新契约和兼容矩阵，再派发给支持它的客户端。

## 后果

三个 Portal 可以针对用户旅程独立优化和发布，后端动作语义保持一致；代价是需要维护契约生成、设计系统、客户端兼容和跨端端到端测试。

## 复审触发

- 两个 Portal 的用户、设备、旅程和发布责任长期完全一致；
- 共享页面逻辑显著超过独立逻辑且不含权限/离线假设；
- 客户端兼容成本超过独立发布收益；
- 新类型 Portal 需要统一 shell，但仍必须保持服务端授权边界。
