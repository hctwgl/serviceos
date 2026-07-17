package com.serviceos.readmodel.api;

/**
 * 记录一次资源访问。pageId/displayRef 可选；displayRef 不得含完整电话/地址/价格。
 */
public record RecentResourceTouch(
        RecentResourceType resourceType,
        String resourceId,
        String pageId,
        String displayRef
) {
}
