package com.serviceos.workorder.application;

import com.serviceos.workorder.api.ReceiveExternalWorkOrderCommand;
import com.serviceos.workorder.api.ExternalWorkOrderConflictException;
import com.serviceos.workorder.api.WorkOrderCommandService;
import com.serviceos.workorder.api.WorkOrderReceipt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
final class JdbcWorkOrderCommandService implements WorkOrderCommandService {
    private final JdbcClient jdbc;
    private final Clock clock;

    @Autowired
    JdbcWorkOrderCommandService(JdbcClient jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    JdbcWorkOrderCommandService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional(noRollbackFor = ExternalWorkOrderConflictException.class)
    public WorkOrderReceipt receive(ReceiveExternalWorkOrderCommand command) {
        UUID id = UUID.randomUUID();
        Instant receivedAt = clock.instant();
        int inserted = jdbc.sql("""
                INSERT INTO wo_work_order (
                    id, tenant_id, project_id, client_code, brand_code, service_product_code,
                    external_order_code, payload_digest, status, configuration_bundle_id,
                    configuration_bundle_code, configuration_bundle_version,
                    province_code, city_code, district_code, customer_name, customer_mobile,
                    service_address, vehicle_vin, external_dispatched_at, received_at, version
                ) VALUES (
                    :id, :tenantId, :projectId, :clientCode, :brandCode, :serviceProductCode,
                    :externalOrderCode, :payloadDigest, 'RECEIVED', :bundleId,
                    :bundleCode, :bundleVersion,
                    :provinceCode, :cityCode, :districtCode, :customerName, :customerMobile,
                    :serviceAddress, :vehicleVin, :externalDispatchedAt, :receivedAt, 1
                ) ON CONFLICT (tenant_id, client_code, external_order_code) DO NOTHING
                """)
                .param("id", id)
                .param("tenantId", command.tenantId())
                .param("projectId", command.projectId())
                .param("clientCode", command.clientCode())
                .param("brandCode", command.brandCode())
                .param("serviceProductCode", command.serviceProductCode())
                .param("externalOrderCode", command.externalOrderCode())
                .param("payloadDigest", command.payloadDigest())
                .param("bundleId", command.configurationBundleId())
                .param("bundleCode", command.configurationBundleCode())
                .param("bundleVersion", command.configurationBundleVersion())
                .param("provinceCode", command.provinceCode())
                .param("cityCode", command.cityCode())
                .param("districtCode", command.districtCode())
                .param("customerName", command.customerName())
                .param("customerMobile", command.customerMobile())
                .param("serviceAddress", command.serviceAddress())
                .param("vehicleVin", command.vehicleVin())
                .param("externalDispatchedAt", command.externalDispatchedAt())
                .param("receivedAt", java.sql.Timestamp.from(receivedAt))
                .update();

        if (inserted == 1) {
            return receipt(id, command, false, receivedAt);
        }

        Existing existing = jdbc.sql("""
                SELECT id, tenant_id, project_id, payload_digest, status, configuration_bundle_id,
                       configuration_bundle_code,
                       configuration_bundle_version, received_at
                  FROM wo_work_order
                 WHERE tenant_id = :tenantId AND client_code = :clientCode
                   AND external_order_code = :externalOrderCode
                """)
                .param("tenantId", command.tenantId())
                .param("clientCode", command.clientCode())
                .param("externalOrderCode", command.externalOrderCode())
                .query((rs, rowNum) -> new Existing(
                        rs.getObject("id", UUID.class),
                        rs.getString("tenant_id"),
                        rs.getObject("project_id", UUID.class),
                        rs.getString("payload_digest"),
                        rs.getString("status"),
                        rs.getObject("configuration_bundle_id", UUID.class),
                        rs.getString("configuration_bundle_code"),
                        rs.getString("configuration_bundle_version"),
                        rs.getTimestamp("received_at").toInstant()))
                .single();

        if (!existing.payloadDigest().equals(command.payloadDigest())) {
            throw new ExternalWorkOrderConflictException(
                    "external order already exists with a different payload");
        }
        if (!existing.projectId().equals(command.projectId())
                || !existing.bundleId().equals(command.configurationBundleId())
                || !existing.bundleCode().equals(command.configurationBundleCode())
                || !existing.bundleVersion().equals(command.configurationBundleVersion())) {
            throw new ExternalWorkOrderConflictException(
                    "external order replay attempted with a different project or configuration bundle");
        }
        return new WorkOrderReceipt(existing.id(), existing.tenantId(), existing.projectId(),
                command.externalOrderCode(), existing.status(), existing.bundleId(),
                existing.bundleCode(), existing.bundleVersion(), true, existing.receivedAt());
    }

    private static WorkOrderReceipt receipt(
            UUID id, ReceiveExternalWorkOrderCommand command, boolean replay, Instant receivedAt) {
        return new WorkOrderReceipt(id, command.tenantId(), command.projectId(),
                command.externalOrderCode(), "RECEIVED", command.configurationBundleId(),
                command.configurationBundleCode(), command.configurationBundleVersion(), replay, receivedAt);
    }

    private record Existing(
            UUID id, String tenantId, UUID projectId, String payloadDigest, String status,
            UUID bundleId, String bundleCode, String bundleVersion, Instant receivedAt) {
    }
}
