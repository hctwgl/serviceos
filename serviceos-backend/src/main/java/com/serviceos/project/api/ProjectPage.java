package com.serviceos.project.api;

import java.time.Instant;
import java.util.List;

/** projectCode/projectId 正序的授权项目页。 */
public record ProjectPage(List<ProjectView> items, String nextCursor, Instant asOf) {
    public ProjectPage {
        items = List.copyOf(items);
    }
}
