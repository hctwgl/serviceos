package com.serviceos.dispatch.infrastructure;

import com.serviceos.network.api.NetworkDirectoryLabelQuery;
import com.serviceos.workorder.api.WorkOrderDirectoryServiceResponsibility;
import com.serviceos.workorder.api.WorkOrderDirectoryServiceResponsibilityQuery;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * M439/M440：按工单批量解析 ACTIVE NETWORK / TECHNICIAN 责任及显示名，并按 ACTIVE NETWORK 筛选工单。
 *
 * <p>同级别多条 ACTIVE 时取 effective_from 最新；显示名经 network 目录标签端口解析。
 * 筛选不读 wo_work_order；项目范围由调用方授权 SQL 收敛。</p>
 */
@Component
final class JdbcWorkOrderDirectoryServiceResponsibilityQuery
        implements WorkOrderDirectoryServiceResponsibilityQuery {

    private static final String ASSIGNMENT_SQL = """
            SELECT DISTINCT ON (work_order_id, responsibility_level)
                   work_order_id AS work_order_id,
                   responsibility_level AS responsibility_level,
                   assignee_id AS assignee_id
              FROM dsp_service_assignment
             WHERE tenant_id = :tenantId
               AND work_order_id IN (:workOrderIds)
               AND status = 'ACTIVE'
               AND responsibility_level IN ('NETWORK', 'TECHNICIAN')
             ORDER BY work_order_id, responsibility_level, effective_from DESC NULLS LAST,
                      service_assignment_id DESC
            """;

    private static final String FILTER_BY_NETWORK_SQL = """
            SELECT work_order_id
              FROM (
                    SELECT DISTINCT ON (work_order_id)
                           work_order_id AS work_order_id,
                           assignee_id AS assignee_id
                      FROM dsp_service_assignment
                     WHERE tenant_id = :tenantId
                       AND work_order_id IS NOT NULL
                       AND status = 'ACTIVE'
                       AND responsibility_level = 'NETWORK'
                     ORDER BY work_order_id, effective_from DESC NULLS LAST,
                              service_assignment_id DESC
                   ) current_network
             WHERE assignee_id = :networkAssigneeId
            """;

    private final JdbcClient jdbc;
    private final NetworkDirectoryLabelQuery labels;

    JdbcWorkOrderDirectoryServiceResponsibilityQuery(
            JdbcClient jdbc, NetworkDirectoryLabelQuery labels
    ) {
        this.jdbc = jdbc;
        this.labels = labels;
    }

    @Override
    public List<UUID> findWorkOrderIdsByActiveNetworkId(String tenantId, UUID networkId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(networkId, "networkId must not be null");
        List<UUID> ids = new ArrayList<>();
        jdbc.sql(FILTER_BY_NETWORK_SQL)
                .param("tenantId", tenantId)
                .param("networkAssigneeId", networkId.toString())
                .query((rs, rowNum) -> {
                    UUID workOrderId = rs.getObject("work_order_id", UUID.class);
                    if (workOrderId != null) {
                        ids.add(workOrderId);
                    }
                    return null;
                })
                .list();
        return List.copyOf(ids);
    }

    @Override
    public Map<UUID, WorkOrderDirectoryServiceResponsibility> findActive(
            String tenantId, Collection<UUID> workOrderIds
    ) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workOrderIds, "workOrderIds must not be null");
        if (workOrderIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = List.copyOf(workOrderIds);
        Map<UUID, String> networkIds = new HashMap<>();
        Map<UUID, String> technicianIds = new HashMap<>();
        jdbc.sql(ASSIGNMENT_SQL)
                .param("tenantId", tenantId)
                .param("workOrderIds", ids)
                .query((rs, rowNum) -> {
                    UUID workOrderId = rs.getObject("work_order_id", UUID.class);
                    String level = rs.getString("responsibility_level");
                    String assignee = rs.getString("assignee_id");
                    if (workOrderId == null || level == null || assignee == null || assignee.isBlank()) {
                        return null;
                    }
                    String trimmed = assignee.trim();
                    if ("NETWORK".equals(level)) {
                        networkIds.put(workOrderId, trimmed);
                    } else if ("TECHNICIAN".equals(level)) {
                        technicianIds.put(workOrderId, trimmed);
                    }
                    return null;
                })
                .list();
        if (networkIds.isEmpty() && technicianIds.isEmpty()) {
            return Map.of();
        }
        Set<UUID> networkUuidSet = new HashSet<>();
        for (String raw : networkIds.values()) {
            UUID parsed = tryParseUuid(raw);
            if (parsed != null) {
                networkUuidSet.add(parsed);
            }
        }
        Set<UUID> techUuidSet = new HashSet<>();
        for (String raw : technicianIds.values()) {
            UUID parsed = tryParseUuid(raw);
            if (parsed != null) {
                techUuidSet.add(parsed);
            }
        }
        Map<UUID, String> networkNames = labels.findNetworkNames(tenantId, networkUuidSet);
        Map<UUID, String> techNames = labels.findTechnicianProfileDisplayNames(tenantId, techUuidSet);
        Map<UUID, WorkOrderDirectoryServiceResponsibility> result = new HashMap<>();
        Set<UUID> allWorkOrders = new HashSet<>();
        allWorkOrders.addAll(networkIds.keySet());
        allWorkOrders.addAll(technicianIds.keySet());
        for (UUID workOrderId : allWorkOrders) {
            String networkId = networkIds.get(workOrderId);
            String technicianId = technicianIds.get(workOrderId);
            String networkName = null;
            UUID networkUuid = tryParseUuid(networkId);
            if (networkUuid != null) {
                networkName = networkNames.get(networkUuid);
            }
            String technicianName = null;
            UUID techUuid = tryParseUuid(technicianId);
            if (techUuid != null) {
                technicianName = techNames.get(techUuid);
            }
            result.put(workOrderId, new WorkOrderDirectoryServiceResponsibility(
                    networkId, networkName, technicianId, technicianName));
        }
        return Map.copyOf(result);
    }

    private static UUID tryParseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
