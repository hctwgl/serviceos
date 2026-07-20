package com.serviceos.configuration.web;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.configuration.api.WorkOrderFulfillmentSnapshot;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CorrelationIds;
import com.serviceos.shared.ProblemCode;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 工单履约配置快照。放在 configuration 模块以避免 workorder→configuration 反向依赖。
 */
@RestController
@RequestMapping("/api/v1/work-orders")
final class WorkOrderFulfillmentSnapshotController {
    private final JdbcClient jdbc;
    private final AuthorizationService authorization;
    private final CurrentPrincipalProvider principals;

    WorkOrderFulfillmentSnapshotController(
            JdbcClient jdbc,
            AuthorizationService authorization,
            CurrentPrincipalProvider principals
    ) {
        this.jdbc = jdbc;
        this.authorization = authorization;
        this.principals = principals;
    }

    @GetMapping("/{workOrderId}/fulfillment-snapshot")
    ResponseEntity<WorkOrderFulfillmentSnapshot> get(
            @PathVariable UUID workOrderId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        CurrentPrincipal principal = principals.current();
        WorkOrderFulfillmentSnapshot snapshot = jdbc.sql("""
                SELECT w.id, w.project_id, w.service_product_code, w.fulfillment_config_kind,
                       w.fulfillment_profile_id, w.fulfillment_revision_id, w.fulfillment_version,
                       w.configuration_bundle_id, w.configuration_bundle_version,
                       w.configuration_bundle_digest, w.received_at,
                       p.profile_name, r.manifest_json::text AS manifest_json, r.content_digest
                  FROM wo_work_order w
             LEFT JOIN cfg_project_fulfillment_profile p
                    ON p.profile_id = w.fulfillment_profile_id
             LEFT JOIN cfg_project_fulfillment_revision r
                    ON r.revision_id = w.fulfillment_revision_id
                 WHERE w.tenant_id = :tenantId AND w.id = :workOrderId
                """)
                .param("tenantId", principal.tenantId())
                .param("workOrderId", workOrderId)
                .query((rs, n) -> {
                    UUID projectId = rs.getObject("project_id", UUID.class);
                    authorization.require(principal, AuthorizationRequest.projectCapability(
                            "project.fulfillment.snapshot.read",
                            principal.tenantId(),
                            "WorkOrder",
                            workOrderId.toString(),
                            projectId.toString()), correlationId);
                    String kind = rs.getString("fulfillment_config_kind");
                    boolean legacy = "LEGACY_BUNDLE".equals(kind);
                    OffsetDateTime received = rs.getObject("received_at", OffsetDateTime.class);
                    return new WorkOrderFulfillmentSnapshot(
                            rs.getObject("id", UUID.class),
                            projectId,
                            rs.getString("service_product_code"),
                            kind,
                            rs.getObject("fulfillment_profile_id", UUID.class),
                            rs.getString("profile_name"),
                            rs.getObject("fulfillment_revision_id", UUID.class),
                            rs.getString("fulfillment_version"),
                            rs.getObject("configuration_bundle_id", UUID.class),
                            rs.getString("configuration_bundle_version"),
                            rs.getString("configuration_bundle_digest"),
                            rs.getString("manifest_json"),
                            rs.getString("content_digest"),
                            received == null ? null : received.toInstant(),
                            legacy
                                    ? "历史工单：该工单创建于项目履约方案能力上线前或项目尚未配置履约 Profile，继续按照原有冻结 Bundle 执行。"
                                    : null);
                })
                .optional()
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "工单不存在"));
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(snapshot);
    }
}
