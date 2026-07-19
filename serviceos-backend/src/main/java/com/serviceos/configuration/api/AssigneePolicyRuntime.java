package com.serviceos.configuration.api;

/**
 * ASSIGNEE_POLICY 运行时。
 *
 * <p>从冻结 Bundle 读取策略；按 priority 求值 when；产出候选解释与 USER 列表。
 * 不得绕过 TaskAssignment / 授权模型。</p>
 */
public interface AssigneePolicyRuntime {
    AssigneePolicyResolution resolve(AssigneePolicyResolveCommand command);
}
