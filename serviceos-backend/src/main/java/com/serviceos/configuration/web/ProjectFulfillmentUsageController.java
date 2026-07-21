package com.serviceos.configuration.web;

import com.serviceos.configuration.api.ProjectFulfillmentProfileService;
import com.serviceos.configuration.api.ProjectFulfillmentUsageSummary;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CorrelationIds;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** M422：项目履约配置中心使用中工单摘要 HTTP 适配。 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/fulfillment-usage-summary")
final class ProjectFulfillmentUsageController {
    private final ProjectFulfillmentProfileService profiles;
    private final CurrentPrincipalProvider principals;

    ProjectFulfillmentUsageController(
            ProjectFulfillmentProfileService profiles,
            CurrentPrincipalProvider principals
    ) {
        this.profiles = profiles;
        this.principals = principals;
    }

    @GetMapping
    ResponseEntity<ProjectFulfillmentUsageSummary> get(
            @PathVariable UUID projectId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(profiles.usageSummary(principals.current(), correlationId, projectId));
    }
}
