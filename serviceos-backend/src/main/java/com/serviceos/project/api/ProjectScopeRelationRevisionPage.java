package com.serviceos.project.api;

import java.time.Instant;
import java.util.List;

/** aggregateVersion 倒序的不可变范围修订历史页。 */
public record ProjectScopeRelationRevisionPage(
        List<ProjectScopeRelationRevisionView> items,
        String nextCursor,
        Instant asOf
) {
    public ProjectScopeRelationRevisionPage {
        items = List.copyOf(items);
    }
}
