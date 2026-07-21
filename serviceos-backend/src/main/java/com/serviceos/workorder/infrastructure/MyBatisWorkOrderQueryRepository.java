package com.serviceos.workorder.infrastructure;

import com.serviceos.workorder.api.WorkOrderView;
import com.serviceos.workorder.application.WorkOrderQueryRepository;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
final class MyBatisWorkOrderQueryRepository implements WorkOrderQueryRepository {
    private final WorkOrderQueryMapper mapper;
    MyBatisWorkOrderQueryRepository(WorkOrderQueryMapper mapper) { this.mapper = mapper; }
    @Override public List<WorkOrderView> findPage(String tenantId, boolean tenantWide,
            List<UUID> projectIds, String clientCode, UUID projectId, String status,
            String externalOrderCode, String provinceCode, String cityCode, String districtCode,
            boolean applyStageFilter, List<UUID> stageWorkOrderIds,
            boolean applyNetworkFilter, List<UUID> networkWorkOrderIds,
            boolean applyTechnicianFilter, List<UUID> technicianWorkOrderIds,
            boolean applySlaRiskFilter, List<UUID> slaRiskWorkOrderIds,
            Instant receivedFromInclusive, Instant receivedToExclusive,
            Instant cursorReceivedAt, UUID cursorId, int fetchSize) {
        return mapper.findPage(tenantId, tenantWide, projectIds.stream().map(UUID::toString).toList(),
                clientCode, projectId, status, externalOrderCode, provinceCode, cityCode, districtCode,
                applyStageFilter, stageWorkOrderIds.stream().map(UUID::toString).toList(),
                applyNetworkFilter, networkWorkOrderIds.stream().map(UUID::toString).toList(),
                applyTechnicianFilter, technicianWorkOrderIds.stream().map(UUID::toString).toList(),
                applySlaRiskFilter, slaRiskWorkOrderIds.stream().map(UUID::toString).toList(),
                receivedFromInclusive, receivedToExclusive,
                cursorReceivedAt, cursorId, fetchSize)
                .stream().map(MyBatisWorkOrderQueryRepository::view).toList();
    }

    @Override
    public int countMatching(String tenantId, boolean tenantWide, List<UUID> projectIds,
            String clientCode, UUID projectId, String status, String externalOrderCode,
            String provinceCode, String cityCode, String districtCode,
            boolean applyStageFilter, List<UUID> stageWorkOrderIds,
            boolean applyNetworkFilter, List<UUID> networkWorkOrderIds,
            boolean applyTechnicianFilter, List<UUID> technicianWorkOrderIds,
            boolean applySlaRiskFilter, List<UUID> slaRiskWorkOrderIds,
            Instant receivedFromInclusive, Instant receivedToExclusive) {
        return mapper.countMatching(tenantId, tenantWide, projectIds.stream().map(UUID::toString).toList(),
                clientCode, projectId, status, externalOrderCode, provinceCode, cityCode, districtCode,
                applyStageFilter, stageWorkOrderIds.stream().map(UUID::toString).toList(),
                applyNetworkFilter, networkWorkOrderIds.stream().map(UUID::toString).toList(),
                applyTechnicianFilter, technicianWorkOrderIds.stream().map(UUID::toString).toList(),
                applySlaRiskFilter, slaRiskWorkOrderIds.stream().map(UUID::toString).toList(),
                receivedFromInclusive, receivedToExclusive);
    }

    @Override public Optional<WorkOrderView> findById(String tenantId, UUID workOrderId) {
        return Optional.ofNullable(mapper.findById(tenantId, workOrderId)).map(MyBatisWorkOrderQueryRepository::view);
    }

    @Override
    public Optional<RawCustomerContact> findRawCustomerContact(String tenantId, UUID workOrderId) {
        Map<String, Object> row = mapper.findRawCustomerContact(tenantId, workOrderId);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new RawCustomerContact(
                uuid(row, "workOrderId"),
                string(row, "customerName"),
                string(row, "customerMobile"),
                string(row, "serviceAddress")));
    }
    private static WorkOrderView view(Map<String,Object> r) {
        return new WorkOrderView(uuid(r,"id"), string(r,"tenantId"), uuid(r,"projectId"),
                string(r,"clientCode"), string(r,"brandCode"), string(r,"serviceProductCode"),
                string(r,"externalOrderCode"), string(r,"status"), uuid(r,"configurationBundleId"),
                string(r,"configurationBundleCode"), string(r,"configurationBundleVersion"),
                string(r,"configurationBundleDigest"), string(r,"provinceCode"), string(r,"cityCode"),
                string(r,"districtCode"), instant(r,"externalDispatchedAt"), instant(r,"receivedAt"),
                instant(r,"updatedAt"), instant(r,"activatedAt"), instant(r,"fulfilledAt"),
                ((Number)r.get("version")).longValue());
    }
    private static String string(Map<String,Object> r,String k) { Object v=r.get(k); return v==null?null:v.toString(); }
    private static UUID uuid(Map<String,Object> r,String k) { Object v=r.get(k); return v instanceof UUID u?u:UUID.fromString(v.toString()); }
    private static Instant instant(Map<String,Object> r,String k) {
        Object v=r.get(k); if(v==null)return null; if(v instanceof Instant i)return i;
        if(v instanceof OffsetDateTime o)return o.toInstant(); if(v instanceof LocalDateTime l)return l.toInstant(ZoneOffset.UTC);
        if(v instanceof java.sql.Timestamp t)return t.toInstant(); return Instant.parse(v.toString());
    }
}
