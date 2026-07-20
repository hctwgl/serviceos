package com.serviceos.configuration.api;

/** 单项配置差异（中文摘要）。 */
public record ProjectFulfillmentCompareChange(
        String category,
        String changeType,
        String summary,
        String detail
) {
}
