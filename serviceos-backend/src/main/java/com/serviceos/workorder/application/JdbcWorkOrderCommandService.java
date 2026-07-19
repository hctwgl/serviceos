package com.serviceos.workorder.application;

import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.shared.Sha256;
import com.serviceos.workorder.api.ActivateWorkOrderCommand;
import com.serviceos.workorder.api.CancelWorkOrderCommand;
import com.serviceos.workorder.api.ExternalWorkOrderConflictException;
import com.serviceos.workorder.api.FulfillWorkOrderCommand;
import com.serviceos.workorder.api.ReceiveExternalWorkOrderCommand;
import com.serviceos.workorder.api.ReopenWorkOrderCommand;
import com.serviceos.workorder.api.UpdateExternalWorkOrderCommand;
import com.serviceos.workorder.api.WorkOrderActivatedPayload;
import com.serviceos.workorder.api.WorkOrderActivationReceipt;
import com.serviceos.workorder.api.WorkOrderCancellationReceipt;
import com.serviceos.workorder.api.WorkOrderCancelledPayload;
import com.serviceos.workorder.api.WorkOrderCommandService;
import com.serviceos.workorder.api.WorkOrderExternalDetailsUpdatedPayload;
import com.serviceos.workorder.api.WorkOrderFulfilledPayload;
import com.serviceos.workorder.api.WorkOrderFulfillmentReceipt;
import com.serviceos.workorder.api.WorkOrderReceipt;
import com.serviceos.workorder.api.WorkOrderReceivedPayload;
import com.serviceos.workorder.api.WorkOrderReopenReceipt;
import com.serviceos.workorder.api.WorkOrderReopenedPayload;
import com.serviceos.workorder.api.WorkOrderUpdateReceipt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** WorkOrder 当前事实、业务幂等以及领域事件原子提交的唯一命令实现。 */
@Service
final class JdbcWorkOrderCommandService implements WorkOrderCommandService {
    private static final String RECEIVED_EVENT = "workorder.received";
    private static final String ACTIVATED_EVENT = "workorder.activated";
    private static final String FULFILLED_EVENT = "workorder.fulfilled";
    private static final String CANCELLED_EVENT = "workorder.cancelled";
    private static final String REOPENED_EVENT = "workorder.reopened";
    private static final String EXTERNAL_DETAILS_UPDATED_EVENT = "workorder.external-details-updated";

    private final JdbcClient jdbc;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    JdbcWorkOrderCommandService(JdbcClient jdbc, OutboxAppender outbox, ObjectMapper objectMapper) {
        this(jdbc, outbox, objectMapper, Clock.systemUTC());
    }

    JdbcWorkOrderCommandService(
            JdbcClient jdbc,
            OutboxAppender outbox,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.jdbc = jdbc;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
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
                    configuration_bundle_digest, province_code, city_code, district_code,
                    customer_name, customer_mobile, service_address, vehicle_vin,
                    external_dispatched_at, received_at, version
                ) VALUES (
                    :id, :tenantId, :projectId, :clientCode, :brandCode, :serviceProductCode,
                    :externalOrderCode, :payloadDigest, 'RECEIVED', :bundleId,
                    :bundleCode, :bundleVersion, :bundleDigest,
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
                .param("bundleDigest", command.configurationBundleDigest())
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
            appendReceivedEvent(id, command, receivedAt);
            return receipt(id, command, false, receivedAt);
        }

        Existing existing = findExisting(command.tenantId(), command.clientCode(), command.externalOrderCode());
        if (!existing.payloadDigest().equals(command.payloadDigest())) {
            throw new ExternalWorkOrderConflictException(
                    "external order already exists with a different payload");
        }
        if (!existing.projectId().equals(command.projectId())
                || !existing.bundleId().equals(command.configurationBundleId())
                || !existing.bundleCode().equals(command.configurationBundleCode())
                || !existing.bundleVersion().equals(command.configurationBundleVersion())
                || !existing.bundleDigest().equals(command.configurationBundleDigest())) {
            throw new ExternalWorkOrderConflictException(
                    "external order replay attempted with a different project or configuration bundle");
        }
        return new WorkOrderReceipt(existing.id(), existing.tenantId(), existing.projectId(),
                command.externalOrderCode(), existing.status(), existing.bundleId(),
                existing.bundleCode(), existing.bundleVersion(), existing.bundleDigest(),
                true, existing.receivedAt());
    }

    @Override
    @Transactional
    public WorkOrderActivationReceipt activate(ActivateWorkOrderCommand command) {
        Instant activatedAt = clock.instant();
        int updated = jdbc.sql("""
                UPDATE wo_work_order
                   SET status = 'ACTIVE', activated_at = :activatedAt,
                       version = version + 1
                 WHERE tenant_id = :tenantId AND id = :workOrderId
                   AND status = 'RECEIVED'
                """)
                .param("activatedAt", java.sql.Timestamp.from(activatedAt))
                .param("tenantId", command.tenantId())
                .param("workOrderId", command.workOrderId())
                .update();

        ActivationRow row = findActivation(command.tenantId(), command.workOrderId());
        if (updated == 0) {
            if (!"ACTIVE".equals(row.status())) {
                throw new ExternalWorkOrderConflictException(
                        "work order cannot be activated from status " + row.status());
            }
            return new WorkOrderActivationReceipt(
                    row.workOrderId(), row.status(), row.version(), true, row.activatedAt());
        }

        // 载荷 activatedAt 与信封 occurredAt 必须同源 Instant（写入前 clock）。
        // 禁止把 JDBC 回读的 row.activatedAt() 写入载荷：Timestamp↔Instant 往返可能产生纳秒级偏差，
        // 导致时间线消费者拒绝「发生时间与载荷不一致」。回执仍返回持久化时间，保证幂等重放一致。
        WorkOrderActivatedPayload payload = new WorkOrderActivatedPayload(
                row.workOrderId(), activatedAt);
        appendEvent(
                command.tenantId(), row.workOrderId(), row.version(), ACTIVATED_EVENT,
                command.correlationId(), command.triggerEventId().toString(), payload, activatedAt);
        return new WorkOrderActivationReceipt(
                row.workOrderId(), row.status(), row.version(), false, row.activatedAt());
    }

    @Override
    @Transactional
    public WorkOrderFulfillmentReceipt fulfill(FulfillWorkOrderCommand command) {
        Instant fulfilledAt = clock.instant();
        int updated = jdbc.sql("""
                UPDATE wo_work_order
                   SET status = 'FULFILLED', fulfilled_at = :fulfilledAt,
                       version = version + 1
                 WHERE tenant_id = :tenantId AND id = :workOrderId
                   AND status = 'ACTIVE'
                """)
                .param("fulfilledAt", java.sql.Timestamp.from(fulfilledAt))
                .param("tenantId", command.tenantId())
                .param("workOrderId", command.workOrderId())
                .update();

        FulfillmentRow row = findFulfillment(command.tenantId(), command.workOrderId());
        if (updated == 0) {
            if (!"FULFILLED".equals(row.status())) {
                throw new ExternalWorkOrderConflictException(
                        "work order cannot be fulfilled from status " + row.status());
            }
            return new WorkOrderFulfillmentReceipt(
                    row.workOrderId(), row.status(), row.version(), true, row.fulfilledAt());
        }

        // 与 activate 相同：载荷与信封共用写入前 Instant；回执用持久化时间保证幂等一致。
        WorkOrderFulfilledPayload payload = new WorkOrderFulfilledPayload(
                row.workOrderId(), command.workflowInstanceId(),
                command.completedStageCodes(), fulfilledAt);
        appendEvent(
                command.tenantId(), row.workOrderId(), row.version(), FULFILLED_EVENT,
                command.correlationId(), command.triggerEventId().toString(), payload, fulfilledAt);
        return new WorkOrderFulfillmentReceipt(
                row.workOrderId(), row.status(), row.version(), false, row.fulfilledAt());
    }

    @Override
    @Transactional
    public WorkOrderUpdateReceipt updateExternalDetails(UpdateExternalWorkOrderCommand command) {
        Instant updatedAt = clock.instant();
        UpdateRow current = findUpdateRow(command.tenantId(), command.workOrderId());
        if (command.updateDigest().equals(current.lastExternalUpdateDigest())) {
            return new WorkOrderUpdateReceipt(
                    current.workOrderId(), current.status(), current.version(), true, updatedAt);
        }
        int updated = jdbc.sql("""
                UPDATE wo_work_order
                   SET customer_name = :customerName,
                       customer_mobile = :customerMobile,
                       service_address = :serviceAddress,
                       province_code = :provinceCode,
                       city_code = :cityCode,
                       district_code = :districtCode,
                       last_external_update_digest = :updateDigest,
                       version = version + 1
                 WHERE tenant_id = :tenantId AND id = :workOrderId
                   AND status IN ('RECEIVED', 'ACTIVE')
                   AND version = :expectedVersion
                """)
                .param("customerName", command.customerName())
                .param("customerMobile", command.customerMobile())
                .param("serviceAddress", command.serviceAddress())
                .param("provinceCode", command.provinceCode())
                .param("cityCode", command.cityCode())
                .param("districtCode", command.districtCode())
                .param("updateDigest", command.updateDigest())
                .param("tenantId", command.tenantId())
                .param("workOrderId", command.workOrderId())
                .param("expectedVersion", command.expectedVersion())
                .update();
        UpdateRow row = findUpdateRow(command.tenantId(), command.workOrderId());
        if (updated == 0) {
            if (command.updateDigest().equals(row.lastExternalUpdateDigest())) {
                return new WorkOrderUpdateReceipt(
                        row.workOrderId(), row.status(), row.version(), true, updatedAt);
            }
            throw new ExternalWorkOrderConflictException(
                    "work order cannot be updated from status " + row.status()
                            + " at version " + row.version());
        }
        WorkOrderExternalDetailsUpdatedPayload payload = new WorkOrderExternalDetailsUpdatedPayload(
                row.workOrderId(), command.updateDigest(), command.provinceCode(),
                command.cityCode(), command.districtCode(), updatedAt);
        appendEvent(
                command.tenantId(), row.workOrderId(), row.version(), EXTERNAL_DETAILS_UPDATED_EVENT,
                command.correlationId(), command.causationId(), payload, updatedAt);
        return new WorkOrderUpdateReceipt(
                row.workOrderId(), row.status(), row.version(), false, updatedAt);
    }

    @Override
    @Transactional
    public WorkOrderCancellationReceipt cancel(CancelWorkOrderCommand command) {
        Instant cancelledAt = clock.instant();
        int updated = jdbc.sql("""
                UPDATE wo_work_order
                   SET status = 'CANCELLED',
                       cancelled_at = :cancelledAt,
                       cancel_reason_code = :reasonCode,
                       cancel_approval_ref = :approvalRef,
                       version = version + 1
                 WHERE tenant_id = :tenantId AND id = :workOrderId
                   AND status IN ('RECEIVED', 'ACTIVE')
                   AND version = :expectedVersion
                """)
                .param("cancelledAt", java.sql.Timestamp.from(cancelledAt))
                .param("reasonCode", command.reasonCode())
                .param("approvalRef", command.approvalRef())
                .param("tenantId", command.tenantId())
                .param("workOrderId", command.workOrderId())
                .param("expectedVersion", command.expectedVersion())
                .update();

        CancellationRow row = findCancellation(command.tenantId(), command.workOrderId());
        if (updated == 0) {
            if ("CANCELLED".equals(row.status())
                    && command.reasonCode().equals(row.cancelReasonCode())
                    && java.util.Objects.equals(command.approvalRef(), row.cancelApprovalRef())) {
                return new WorkOrderCancellationReceipt(
                        row.workOrderId(), row.status(), row.version(), true, row.cancelledAt());
            }
            throw new ExternalWorkOrderConflictException(
                    "work order cannot be cancelled from status " + row.status()
                            + " at version " + row.version());
        }

        WorkOrderCancelledPayload payload = new WorkOrderCancelledPayload(
                row.workOrderId(), command.reasonCode(), command.approvalRef(), cancelledAt);
        appendEvent(
                command.tenantId(), row.workOrderId(), row.version(), CANCELLED_EVENT,
                command.correlationId(), command.causationId(), payload, cancelledAt);
        return new WorkOrderCancellationReceipt(
                row.workOrderId(), row.status(), row.version(), false, row.cancelledAt());
    }

    @Override
    @Transactional
    public WorkOrderReopenReceipt reopen(ReopenWorkOrderCommand command) {
        Instant reopenedAt = clock.instant();
        int updated = jdbc.sql("""
                UPDATE wo_work_order
                   SET status = 'ACTIVE',
                       reopened_at = :reopenedAt,
                       reopen_approval_ref = :approvalRef,
                       activated_at = COALESCE(activated_at, :reopenedAt),
                       version = version + 1
                 WHERE tenant_id = :tenantId AND id = :workOrderId
                   AND status = 'CANCELLED'
                   AND version = :expectedVersion
                """)
                .param("reopenedAt", java.sql.Timestamp.from(reopenedAt))
                .param("approvalRef", command.approvalRef())
                .param("tenantId", command.tenantId())
                .param("workOrderId", command.workOrderId())
                .param("expectedVersion", command.expectedVersion())
                .update();

        ReopenRow row = findReopen(command.tenantId(), command.workOrderId());
        if (updated == 0) {
            if ("ACTIVE".equals(row.status())
                    && command.approvalRef().equals(row.reopenApprovalRef())) {
                return new WorkOrderReopenReceipt(
                        row.workOrderId(), row.status(), row.version(), true, row.reopenedAt());
            }
            throw new ExternalWorkOrderConflictException(
                    "work order cannot be reopened from status " + row.status()
                            + " at version " + row.version());
        }

        WorkOrderReopenedPayload payload = new WorkOrderReopenedPayload(
                row.workOrderId(),
                row.projectId(),
                new WorkOrderReceivedPayload.ConfigurationBundleRef(
                        row.bundleId(), row.bundleCode(), row.bundleVersion(), row.bundleDigest()),
                command.approvalRef(),
                reopenedAt);
        appendEvent(
                command.tenantId(), row.workOrderId(), row.version(), REOPENED_EVENT,
                command.correlationId(), command.causationId(), payload, reopenedAt);
        return new WorkOrderReopenReceipt(
                row.workOrderId(), row.status(), row.version(), false, row.reopenedAt());
    }

    private void appendReceivedEvent(
            UUID workOrderId,
            ReceiveExternalWorkOrderCommand command,
            Instant receivedAt
    ) {
        WorkOrderReceivedPayload payload = new WorkOrderReceivedPayload(
                workOrderId,
                command.projectId(),
                command.externalOrderCode(),
                command.clientCode(),
                command.brandCode(),
                command.serviceProductCode(),
                new WorkOrderReceivedPayload.ConfigurationBundleRef(
                        command.configurationBundleId(),
                        command.configurationBundleCode(),
                        command.configurationBundleVersion(),
                        command.configurationBundleDigest()),
                receivedAt);
        appendEvent(
                command.tenantId(), workOrderId, 1, RECEIVED_EVENT,
                command.correlationId(), command.causationId(), payload, receivedAt);
    }

    private void appendEvent(
            String tenantId,
            UUID workOrderId,
            long aggregateVersion,
            String eventType,
            String correlationId,
            String causationId,
            Object payload,
            Instant occurredAt
    ) {
        String payloadJson = serialize(payload);
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "workorder", eventType, 1,
                "WorkOrder", workOrderId.toString(), aggregateVersion, tenantId,
                correlationId, causationId, workOrderId.toString(), payloadJson,
                Sha256.digest(payloadJson), occurredAt));
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("WorkOrder event serialization failed", exception);
        }
    }

    private Existing findExisting(String tenantId, String clientCode, String externalOrderCode) {
        return jdbc.sql("""
                SELECT id, tenant_id, project_id, payload_digest, status, configuration_bundle_id,
                       configuration_bundle_code, configuration_bundle_version,
                       configuration_bundle_digest, received_at
                  FROM wo_work_order
                 WHERE tenant_id = :tenantId AND client_code = :clientCode
                   AND external_order_code = :externalOrderCode
                """)
                .param("tenantId", tenantId)
                .param("clientCode", clientCode)
                .param("externalOrderCode", externalOrderCode)
                .query((rs, rowNum) -> new Existing(
                        rs.getObject("id", UUID.class),
                        rs.getString("tenant_id"),
                        rs.getObject("project_id", UUID.class),
                        rs.getString("payload_digest"),
                        rs.getString("status"),
                        rs.getObject("configuration_bundle_id", UUID.class),
                        rs.getString("configuration_bundle_code"),
                        rs.getString("configuration_bundle_version"),
                        rs.getString("configuration_bundle_digest"),
                        rs.getTimestamp("received_at").toInstant()))
                .single();
    }

    private ActivationRow findActivation(String tenantId, UUID workOrderId) {
        return jdbc.sql("""
                SELECT id, status, version, activated_at
                  FROM wo_work_order
                 WHERE tenant_id = :tenantId AND id = :workOrderId
                """)
                .param("tenantId", tenantId)
                .param("workOrderId", workOrderId)
                .query((rs, rowNum) -> new ActivationRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("status"),
                        rs.getLong("version"),
                        rs.getTimestamp("activated_at") == null
                                ? null : rs.getTimestamp("activated_at").toInstant()))
                .single();
    }

    private FulfillmentRow findFulfillment(String tenantId, UUID workOrderId) {
        return jdbc.sql("""
                SELECT id, status, version, fulfilled_at
                  FROM wo_work_order
                 WHERE tenant_id = :tenantId AND id = :workOrderId
                """)
                .param("tenantId", tenantId)
                .param("workOrderId", workOrderId)
                .query((rs, rowNum) -> new FulfillmentRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("status"),
                        rs.getLong("version"),
                        rs.getTimestamp("fulfilled_at") == null
                                ? null : rs.getTimestamp("fulfilled_at").toInstant()))
                .single();
    }

    private CancellationRow findCancellation(String tenantId, UUID workOrderId) {
        return jdbc.sql("""
                SELECT id, status, version, cancelled_at, cancel_reason_code, cancel_approval_ref
                  FROM wo_work_order
                 WHERE tenant_id = :tenantId AND id = :workOrderId
                """)
                .param("tenantId", tenantId)
                .param("workOrderId", workOrderId)
                .query((rs, rowNum) -> new CancellationRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("status"),
                        rs.getLong("version"),
                        rs.getTimestamp("cancelled_at") == null
                                ? null : rs.getTimestamp("cancelled_at").toInstant(),
                        rs.getString("cancel_reason_code"),
                        rs.getString("cancel_approval_ref")))
                .single();
    }

    private UpdateRow findUpdateRow(String tenantId, UUID workOrderId) {
        return jdbc.sql("""
                SELECT id, status, version, last_external_update_digest
                  FROM wo_work_order
                 WHERE tenant_id = :tenantId AND id = :workOrderId
                """)
                .param("tenantId", tenantId)
                .param("workOrderId", workOrderId)
                .query((rs, rowNum) -> new UpdateRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("status"),
                        rs.getLong("version"),
                        rs.getString("last_external_update_digest")))
                .single();
    }

    private ReopenRow findReopen(String tenantId, UUID workOrderId) {
        return jdbc.sql("""
                SELECT id, project_id, status, version, reopened_at, reopen_approval_ref,
                       configuration_bundle_id, configuration_bundle_code,
                       configuration_bundle_version, configuration_bundle_digest
                  FROM wo_work_order
                 WHERE tenant_id = :tenantId AND id = :workOrderId
                """)
                .param("tenantId", tenantId)
                .param("workOrderId", workOrderId)
                .query((rs, rowNum) -> new ReopenRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("project_id", UUID.class),
                        rs.getString("status"),
                        rs.getLong("version"),
                        rs.getTimestamp("reopened_at") == null
                                ? null : rs.getTimestamp("reopened_at").toInstant(),
                        rs.getString("reopen_approval_ref"),
                        rs.getObject("configuration_bundle_id", UUID.class),
                        rs.getString("configuration_bundle_code"),
                        rs.getString("configuration_bundle_version"),
                        rs.getString("configuration_bundle_digest")))
                .single();
    }

    private static WorkOrderReceipt receipt(
            UUID id,
            ReceiveExternalWorkOrderCommand command,
            boolean replay,
            Instant receivedAt
    ) {
        return new WorkOrderReceipt(id, command.tenantId(), command.projectId(),
                command.externalOrderCode(), "RECEIVED", command.configurationBundleId(),
                command.configurationBundleCode(), command.configurationBundleVersion(),
                command.configurationBundleDigest(), replay, receivedAt);
    }

    private record Existing(
            UUID id,
            String tenantId,
            UUID projectId,
            String payloadDigest,
            String status,
            UUID bundleId,
            String bundleCode,
            String bundleVersion,
            String bundleDigest,
            Instant receivedAt
    ) {
    }

    private record ActivationRow(
            UUID workOrderId,
            String status,
            long version,
            Instant activatedAt
    ) {
    }

    private record FulfillmentRow(
            UUID workOrderId,
            String status,
            long version,
            Instant fulfilledAt
    ) {
    }

    private record CancellationRow(
            UUID workOrderId,
            String status,
            long version,
            Instant cancelledAt,
            String cancelReasonCode,
            String cancelApprovalRef
    ) {
    }

    private record UpdateRow(
            UUID workOrderId,
            String status,
            long version,
            String lastExternalUpdateDigest
    ) {
    }

    private record ReopenRow(
            UUID workOrderId,
            UUID projectId,
            String status,
            long version,
            Instant reopenedAt,
            String reopenApprovalRef,
            UUID bundleId,
            String bundleCode,
            String bundleVersion,
            String bundleDigest
    ) {
    }
}
