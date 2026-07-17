package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.List;

/** 当前主体的最近访问列表（已按当前授权过滤）。 */
public record RecentResourcePage(List<RecentResourceItem> items, Instant asOf) {
    public RecentResourcePage {
        items = List.copyOf(items);
    }
}
