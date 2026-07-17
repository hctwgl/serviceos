package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.List;

/** 当前主体在指定 pageId 下的个人 SavedView 列表。 */
public record SavedViewPage(List<SavedView> items, Instant asOf) {
    public SavedViewPage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
