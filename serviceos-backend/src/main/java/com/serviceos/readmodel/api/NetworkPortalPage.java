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
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NetworkPortalPage<T>(
        UUID networkId,
        List<T> items,
        Instant asOf,
        List<NetworkPortalTechnicianItem> technicians
) {
    public NetworkPortalPage {
        items = items == null ? List.of() : List.copyOf(items);
        technicians = technicians == null ? null : List.copyOf(technicians);
    }

    /** 无师傅旁载的列表页（纠正/异常/资质等）。 */
    public NetworkPortalPage(UUID networkId, List<T> items, Instant asOf) {
        this(networkId, items, asOf, null);
    }
}
