package com.serviceos.integration.byd.application;

import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationResolutionException;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.ResolveConfigurationBundleQuery;
import com.serviceos.integration.byd.api.BydCpimInboundOrderResponse;
import com.serviceos.integration.byd.api.BydCpimInstallOrderPayload;
import com.serviceos.integration.byd.api.BydCpimSignatureHeaders;
import com.serviceos.integration.byd.infrastructure.BydCpimPayloadDigest;
import com.serviceos.integration.byd.infrastructure.BydCpimReplayConflictException;
import com.serviceos.integration.byd.infrastructure.BydCpimSignatureVerifier;
import com.serviceos.integration.byd.infrastructure.JdbcBydCpimReplayGuard;
import com.serviceos.workorder.api.ExternalWorkOrderConflictException;
import com.serviceos.workorder.api.ReceiveExternalWorkOrderCommand;
import com.serviceos.workorder.api.WorkOrderCommandService;
import com.serviceos.workorder.api.WorkOrderReceipt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

/**
 * BYD CPIM 安装订单入站安全边界。
 *
 * <p>处理顺序固定为：验签 → 摘要 → DTO/试点校验 → 防重放 → 反腐层映射。
 * 非法业务载荷不得提前占用 Nonce，避免修正报文被错误判定为重放冲突。</p>
 */
@Service
public class BydCpimInboundOrderService {
    private final ObjectMapper objectMapper;
    private final JdbcBydCpimReplayGuard replayGuard;
    private final BydCpimOrderMapper mapper;
    private final BydCpimSignatureVerifier signatureVerifier;
    private final ConfigurationService configurationService;
    private final WorkOrderCommandService workOrderCommandService;
    private final String tenantId;
    private final String projectCode;

    public BydCpimInboundOrderService(
            ObjectMapper objectMapper,
            JdbcBydCpimReplayGuard replayGuard,
            ConfigurationService configurationService,
            WorkOrderCommandService workOrderCommandService,
            @Value("${serviceos.integration.byd.cpim.app-key:local-byd-app-key}") String appKey,
            @Value("${serviceos.integration.byd.cpim.app-secret:local-byd-app-secret-change-me}") String appSecret,
            @Value("${serviceos.integration.byd.cpim.allowed-clock-skew:PT5M}") Duration allowedClockSkew,
            @Value("${serviceos.integration.byd.cpim.tenant-id:tenant-byd-pilot}") String tenantId,
            @Value("${serviceos.integration.byd.cpim.project-code:BYD-OCEAN-SD-PILOT}") String projectCode) {
        this.objectMapper = objectMapper;
        this.replayGuard = replayGuard;
        this.configurationService = configurationService;
        this.workOrderCommandService = workOrderCommandService;
        this.mapper = new BydCpimOrderMapper();
        this.signatureVerifier = new BydCpimSignatureVerifier(
                appKey, appSecret, Clock.systemUTC(), allowedClockSkew);
        this.tenantId = requiredText(tenantId, "tenantId");
        this.projectCode = requiredText(projectCode, "projectCode");
    }

    @Transactional
    public BydCpimInboundOrderResponse receive(
            BydCpimSignatureHeaders headers,
            Map<String, Object> rawParameters,
            String correlationId) {
        String safeCorrelationId = requiredText(correlationId, "correlationId");
        var verification = signatureVerifier.verify(headers, rawParameters);
        if (!verification.valid()) {
            return BydCpimInboundOrderResponse.rejected(
                    verification.reason().name(), "request signature verification failed");
        }

        String payloadDigest;
        try {
            payloadDigest = BydCpimPayloadDigest.sha256(rawParameters);
        } catch (IllegalArgumentException exception) {
            return BydCpimInboundOrderResponse.rejected("INVALID_PAYLOAD", exception.getMessage());
        }

        final BydCpimMappedOrder mapped;
        try {
            BydCpimInstallOrderPayload payload = objectMapper.convertValue(
                    rawParameters, BydCpimInstallOrderPayload.class);
            mapped = mapper.map(payload);
        } catch (RuntimeException exception) {
            if (!isPayloadMappingFailure(exception)) {
                throw exception;
            }
            return BydCpimInboundOrderResponse.rejected("INVALID_ORDER", safeMessage(exception));
        }

        final ConfigurationBundleReference bundle;
        try {
            bundle = configurationService.resolve(new ResolveConfigurationBundleQuery(
                    tenantId,
                    projectCode,
                    mapped.brandCode(),
                    mapped.serviceProductCode(),
                    mapped.provinceCode(),
                    headers.currentTime()));
        } catch (ConfigurationResolutionException exception) {
            return BydCpimInboundOrderResponse.rejected(
                    "INVALID_ORDER",
                    "CONFIGURATION_" + exception.reason().name() + ": " + exception.getMessage());
        }

        try {
            var replayDecision = replayGuard.register(
                    headers.appKey(), headers.nonce(), headers.currentTime().getEpochSecond(), payloadDigest);
            WorkOrderReceipt receipt = workOrderCommandService.receive(new ReceiveExternalWorkOrderCommand(
                    tenantId,
                    bundle.projectId(),
                    mapped.clientCode(),
                    mapped.brandCode(),
                    mapped.serviceProductCode(),
                    mapped.externalOrderCode(),
                    payloadDigest,
                    bundle.bundleId(),
                    bundle.bundleCode(),
                    bundle.bundleVersion(),
                    bundle.manifestDigest(),
                    mapped.provinceCode(),
                    mapped.cityCode(),
                    mapped.districtCode(),
                    mapped.customerName(),
                    mapped.customerMobile(),
                    mapped.serviceAddress(),
                    mapped.vehicleVin(),
                    mapped.dispatchedAt(),
                    safeCorrelationId,
                    "byd-cpim:" + com.serviceos.shared.Sha256.digest(
                            headers.appKey() + "|" + headers.nonce() + "|"
                                    + headers.currentTime().getEpochSecond())));

            BydCpimInboundOrderResponse response = BydCpimInboundOrderResponse.accepted(
                    mapped.externalOrderCode(),
                    mapped.adapterVersion(),
                    mapped.mappingVersion(),
                    receipt.replay()
                            || replayDecision.kind()
                            == com.serviceos.integration.byd.infrastructure.BydCpimReplayDecision.Kind.REPLAY);

            replayGuard.complete(
                    headers.appKey(), headers.nonce(), headers.currentTime().getEpochSecond(),
                    responseDigest(response));
            return response;
        } catch (BydCpimReplayConflictException exception) {
            return BydCpimInboundOrderResponse.rejected(
                    "REPLAY_CONFLICT", "nonce was already used with a different payload");
        } catch (ExternalWorkOrderConflictException exception) {
            BydCpimInboundOrderResponse response = BydCpimInboundOrderResponse.rejected(
                    "REPLAY_CONFLICT", "ORDER_CONFLICT: " + exception.getMessage());
            replayGuard.complete(
                    headers.appKey(), headers.nonce(), headers.currentTime().getEpochSecond(),
                    responseDigest(response));
            return response;
        }
    }

    private static boolean isPayloadMappingFailure(RuntimeException exception) {
        return exception instanceof IllegalArgumentException
                || exception.getClass().getName().startsWith("tools.jackson.databind.");
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "request payload cannot be mapped" : message;
    }

    private static String responseDigest(BydCpimInboundOrderResponse response) {
        return BydCpimPayloadDigest.sha256(Map.of(
                "success", response.success(),
                "code", response.code(),
                "orderCode", response.orderCode() == null ? "" : response.orderCode(),
                "adapterVersion", response.adapterVersion() == null ? "" : response.adapterVersion(),
                "mappingVersion", response.mappingVersion() == null ? "" : response.mappingVersion()));
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
