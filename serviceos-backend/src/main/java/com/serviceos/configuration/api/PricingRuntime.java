package com.serviceos.configuration.api;

/**
 * PRICING 运行时：冻结价目、条件匹配、minor 合计与解释；不落账。
 */
public interface PricingRuntime {
    PricingResolution resolve(PricingResolveCommand command);
}
