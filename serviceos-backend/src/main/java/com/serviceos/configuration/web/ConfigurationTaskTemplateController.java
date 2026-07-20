package com.serviceos.configuration.web;

import com.serviceos.configuration.api.ConfigurationTaskTemplateItem;
import com.serviceos.configuration.api.ConfigurationTaskTemplateQuery;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CorrelationIds;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 任务模板产品读模型 HTTP 适配。 */
@RestController
@RequestMapping("/api/v1/configuration/task-templates")
final class ConfigurationTaskTemplateController {
    private final ConfigurationTaskTemplateQuery query;
    private final CurrentPrincipalProvider principals;

    ConfigurationTaskTemplateController(
            ConfigurationTaskTemplateQuery query,
            CurrentPrincipalProvider principals
    ) {
        this.query = query;
        this.principals = principals;
    }

    @GetMapping
    ResponseEntity<List<ConfigurationTaskTemplateItem>> list(
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(query.list(principals.current(), correlationId));
    }
}
