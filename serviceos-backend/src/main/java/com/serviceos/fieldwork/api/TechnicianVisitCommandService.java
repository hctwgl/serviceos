package com.serviceos.fieldwork.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

/**
 * Technician Portal 的 Visit 写边界。
 *
 * <p>专用边界先校验 {@code X-Technician-Context} 对应的 ACTIVE 师傅网点关系及资源网点，
 * 再委托 Visit 聚合执行当前责任、Capability、幂等、审计与 Outbox 校验。客户端不能仅凭知道资源 ID
 * 绕过当前 Portal 上下文。</p>
 */
public interface TechnicianVisitCommandService {
    VisitCommandReceipt checkIn(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String technicianContextHeader,
            CheckInVisitCommand command);

    VisitCommandReceipt checkOut(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String technicianContextHeader,
            CheckOutVisitCommand command);

    VisitCommandReceipt interrupt(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String technicianContextHeader,
            InterruptVisitCommand command);
}
