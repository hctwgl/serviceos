package com.serviceos.configuration.api;

/**
 * INTEGRATION Mapping 运行时。
 *
 * <p>只从工单冻结 Bundle 读取 Mapping；Transform 白名单；失败关闭并可解释。</p>
 */
public interface IntegrationMappingRuntime {
    IntegrationMappingResult applyInbound(IntegrationMappingApplyCommand command);
}
