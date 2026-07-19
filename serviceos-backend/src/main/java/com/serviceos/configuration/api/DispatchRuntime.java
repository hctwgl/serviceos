package com.serviceos.configuration.api;

/**
 * DISPATCH 运行时：冻结 Bundle 策略、硬过滤、加权评分、并列处理与无候选降级。
 */
public interface DispatchRuntime {
    DispatchResolution resolve(DispatchResolveCommand command);
}
