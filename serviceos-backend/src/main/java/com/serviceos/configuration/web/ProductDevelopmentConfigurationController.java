package com.serviceos.configuration.web;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.configuration.application.ProductDevelopmentConfigurationFoundationService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CorrelationIds;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.UUID;

/**
 * 本地产品场景所需的最小配置基线编排。
 *
 * <p>该适配器只在 local profile 注册，只处理身份、授权和协议映射。
 * 生产环境不会暴露此入口。</p>
 */
@Profile("local")
@RestController
@RequestMapping("/api/v1/product-development/projects/{projectId}")
final class ProductDevelopmentConfigurationController {
    private final ProductDevelopmentConfigurationFoundationService foundations;
    private final AuthorizationService authorization;
    private final CurrentPrincipalProvider principals;

    ProductDevelopmentConfigurationController(
            ProductDevelopmentConfigurationFoundationService foundations,
            AuthorizationService authorization,
            CurrentPrincipalProvider principals
    ) {
        this.foundations = foundations;
        this.authorization = authorization;
        this.principals = principals;
    }

    @PostMapping("/configuration-foundation")
    ResponseEntity<FoundationResponse> publish(
            @PathVariable UUID projectId,
            @RequestParam String projectCode,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) throws IOException {
        CurrentPrincipal principal = principals.current();
        authorization.require(principal, AuthorizationRequest.projectCapability(
                "configuration.publish", principal.tenantId(),
                "Project", projectId.toString(), projectId.toString()), correlationId);

        var result = foundations.publish(principal.tenantId(), projectId, projectCode);
        return ResponseEntity.ok(new FoundationResponse(
                result.workflowAssetVersionId(), result.sourceBundleId()));
    }

    record FoundationResponse(UUID workflowAssetVersionId, UUID sourceBundleId) {
    }
}
