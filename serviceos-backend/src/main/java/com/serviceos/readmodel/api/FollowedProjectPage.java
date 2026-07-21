package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Admin 个人关注项目页。 */
public record FollowedProjectPage(List<FollowedProjectItem> items, Instant asOf) {
    public FollowedProjectPage {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        Objects.requireNonNull(asOf, "asOf");
    }
}
