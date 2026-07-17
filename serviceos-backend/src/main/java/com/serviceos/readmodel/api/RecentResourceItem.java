package com.serviceos.readmodel.api;

import java.time.Instant;

/**
 * 单条最近访问摘要。displayRef 仅为非敏感短标签；deepLink 为 Admin 前端相对路径。
 */
public record RecentResourceItem(
        RecentResourceType resourceType,
        String resourceId,
        String pageId,
        String displayRef,
        Instant lastVisitedAt,
        String deepLink
) {
}
