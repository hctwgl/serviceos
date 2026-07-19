package com.serviceos.workorder.api;

import java.util.UUID;

/** 工单冻结的配置 Bundle 绑定；供配置运行时在无 Principal 上下文中读取。 */
public record WorkOrderConfigurationBinding(
        UUID workOrderId,
        UUID projectId,
        UUID configurationBundleId,
        String configurationBundleDigest
) {
}
