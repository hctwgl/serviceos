package com.serviceos.authorization.api;

import java.util.List;

public record MeNavigationItemView(
        String pageId,
        String routeKey,
        String title,
        int order,
        String section,
        List<String> requiredCapabilities
) {
    public MeNavigationItemView {
        requiredCapabilities = requiredCapabilities == null ? List.of() : List.copyOf(requiredCapabilities);
    }
}
