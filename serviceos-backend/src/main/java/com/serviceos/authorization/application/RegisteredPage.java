package com.serviceos.authorization.application;

import java.util.List;

/**
 * 代码注册的稳定页面目录项。routeKey 只允许已发布客户端路径键，不保存可执行组件路径。
 */
record RegisteredPage(
        String pageId,
        String portal,
        String routeKey,
        String defaultTitle,
        int defaultOrder,
        String section,
        List<String> requiredCapabilities,
        String featureGate
) {
    RegisteredPage {
        requiredCapabilities = requiredCapabilities == null ? List.of() : List.copyOf(requiredCapabilities);
    }
}
