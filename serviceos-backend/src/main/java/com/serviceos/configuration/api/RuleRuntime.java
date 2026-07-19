package com.serviceos.configuration.api;

/**
 * RULE 运行时：冻结规则集、条件求值、严重级别聚合、可审计解释；无领域副作用。
 */
public interface RuleRuntime {
    RuleResolution resolve(RuleResolveCommand command);
}
