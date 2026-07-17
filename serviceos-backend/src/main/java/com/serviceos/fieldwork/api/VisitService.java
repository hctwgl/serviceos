package com.serviceos.fieldwork.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

import java.util.List;
import java.util.UUID;

/** Visit 聚合的公开应用边界。 */
public interface VisitService {
    List<VisitView> listByWorkOrder(CurrentPrincipal principal, String correlationId, UUID workOrderId);

    /**
     * M222 Network Portal：以 NETWORK {@code visit.read} 鉴权后按工单列出本网点 Visit。
     * 调用方负责将结果再按 ACTIVE taskIds 过滤；本方法不投影 GPS/note/device。
     */
    List<VisitView> listByWorkOrderOnNetwork(
            CurrentPrincipal principal, String correlationId, UUID workOrderId, UUID networkId);

    /** 按 ID 读取 Visit；租户内不存在返回 404，缺权返回 403。 */
    VisitView get(CurrentPrincipal principal, String correlationId, UUID visitId);

    VisitCommandReceipt checkIn(
            CurrentPrincipal principal, CommandMetadata metadata, CheckInVisitCommand command);

    VisitCommandReceipt checkOut(
            CurrentPrincipal principal, CommandMetadata metadata, CheckOutVisitCommand command);

    VisitCommandReceipt interrupt(
            CurrentPrincipal principal, CommandMetadata metadata, InterruptVisitCommand command);
}
