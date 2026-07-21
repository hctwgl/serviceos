package com.serviceos.workorder.api;

/**
 * 工单目录当前服务责任摘要（网点 + 师傅）。
 *
 * <p>显示名由服务端解析；缺档时对应 displayName 为 null，不发明名称。</p>
 */
public record WorkOrderDirectoryServiceResponsibility(
        String networkId,
        String networkDisplayName,
        String technicianId,
        String technicianDisplayName
) {
}
