package com.serviceos.configuration.api;

/**
 * 履约事实提取 + PricingRuntime 试算 + CalculationSnapshot 持久化。
 *
 * <p>SHADOW 模式：可审计试算，不落账、不创建结算单。</p>
 */
public interface PricingCalculationSnapshotService {
    void capture(PricingCalculationSnapshotCommand command);
}
