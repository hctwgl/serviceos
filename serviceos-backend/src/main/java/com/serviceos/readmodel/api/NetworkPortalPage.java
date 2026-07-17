package com.serviceos.readmodel.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Network Portal 通用列表包装。
 *
 * <p>M230：可选 {@code technicians} 仅在工单/任务目录页填充；缺 NETWORK
 * {@code technician.readOwnNetwork} 时为 null（经 {@link JsonInclude.Include#NON_NULL} 省略）。</p>
 *
 * <p>M231：可选 {@code appointments} 仅在工单/任务目录页填充；缺 NETWORK
 * {@code networkPortal.manageAppointment} 时为 null（省略）。</p>
 *
 * <p>M232：可选 {@code contactAttempts} 仅在工单/任务目录页填充；缺 NETWORK
 * {@code networkPortal.manageAppointment} 时为 null（省略）。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NetworkPortalPage<T>(
        UUID networkId,
        List<T> items,
        Instant asOf,
        List<NetworkPortalTechnicianItem> technicians,
        List<NetworkPortalWorkspaceAppointmentSummary> appointments,
        List<NetworkPortalWorkspaceContactAttemptSummary> contactAttempts
) {
    public NetworkPortalPage {
        items = items == null ? List.of() : List.copyOf(items);
        technicians = technicians == null ? null : List.copyOf(technicians);
        appointments = appointments == null ? null : List.copyOf(appointments);
        contactAttempts = contactAttempts == null ? null : List.copyOf(contactAttempts);
    }

    /** 无旁载 enrichment 的列表页（纠正/异常/资质等）。 */
    public NetworkPortalPage(UUID networkId, List<T> items, Instant asOf) {
        this(networkId, items, asOf, null, null, null);
    }

    /** M230 兼容：仅师傅旁载。 */
    public NetworkPortalPage(
            UUID networkId,
            List<T> items,
            Instant asOf,
            List<NetworkPortalTechnicianItem> technicians
    ) {
        this(networkId, items, asOf, technicians, null, null);
    }

    /** M231 兼容：师傅 + 预约旁载。 */
    public NetworkPortalPage(
            UUID networkId,
            List<T> items,
            Instant asOf,
            List<NetworkPortalTechnicianItem> technicians,
            List<NetworkPortalWorkspaceAppointmentSummary> appointments
    ) {
        this(networkId, items, asOf, technicians, appointments, null);
    }
}
