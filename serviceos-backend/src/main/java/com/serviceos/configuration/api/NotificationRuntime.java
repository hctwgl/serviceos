package com.serviceos.configuration.api;

/**
 * NOTIFICATION 运行时：冻结策略、条件过滤、收件人解析、通道发送、幂等与 UNKNOWN 人工接管。
 */
public interface NotificationRuntime {
    NotificationResolution resolveAndDispatch(NotificationResolveCommand command);
}
