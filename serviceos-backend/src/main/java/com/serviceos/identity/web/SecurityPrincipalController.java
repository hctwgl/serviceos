package com.serviceos.identity.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.identity.api.IdentityLinkView;
import com.serviceos.identity.api.PrincipalChangeTimelinePage;
import com.serviceos.identity.api.PrincipalLoginEventPage;
import com.serviceos.identity.api.PrincipalPersonaView;
import com.serviceos.identity.api.SecurityPrincipalCommandService;
import com.serviceos.identity.api.SecurityPrincipalDetail;
import com.serviceos.identity.api.SecurityPrincipalPage;
import com.serviceos.identity.api.SecurityPrincipalQueryService;
import com.serviceos.identity.api.SecurityPrincipalView;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.CorrelationIds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 统一主体目录协议适配器。tenant 和 actor 只来自受信 CurrentPrincipal；issuer/subject 仅允许在
 * 专用高风险绑定命令中提交，普通目录响应绝不返回这些敏感标识。
 */
@RestController
@RequestMapping("/api/v1/security-principals")
final class SecurityPrincipalController {
    private final SecurityPrincipalQueryService queries;
    private final SecurityPrincipalCommandService commands;
    private final CurrentPrincipalProvider principals;

    SecurityPrincipalController(
            SecurityPrincipalQueryService queries,
            SecurityPrincipalCommandService commands,
            CurrentPrincipalProvider principals
    ) {
        this.queries = queries;
        this.commands = commands;
        this.principals = principals;
    }

    @GetMapping
    ResponseEntity<SecurityPrincipalPage> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(queries.list(principals.current(), correlationId, query, status, cursor, limit), correlationId);
    }

    /**
     * 登记主体：不接受密码；登录依赖后续 IdentityLink / OIDC。
     */
    @PostMapping
    ResponseEntity<SecurityPrincipalView> register(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody RegisterPrincipalRequest request
    ) {
        SecurityPrincipalView created = commands.register(
                principals.current(),
                metadata(correlationId, idempotencyKey),
                request.displayName(),
                request.employeeNumber(),
                request.personaType());
        return ResponseEntity.created(URI.create("/api/v1/security-principals/" + created.id()))
                .eTag(Long.toString(created.version()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(created);
    }

    @GetMapping("/{principalId}")
    ResponseEntity<SecurityPrincipalDetail> get(
            @PathVariable UUID principalId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        SecurityPrincipalDetail detail = queries.get(principals.current(), correlationId, principalId);
        return ResponseEntity.ok().eTag(Long.toString(detail.principal().version()))
                .header(CorrelationIds.HEADER_NAME, correlationId).body(detail);
    }

    @GetMapping("/{principalId}/identities")
    ResponseEntity<List<IdentityLinkView>> identities(
            @PathVariable UUID principalId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(queries.identities(principals.current(), correlationId, principalId), correlationId);
    }

    @GetMapping("/{principalId}/recent-logins")
    ResponseEntity<PrincipalLoginEventPage> recentLogins(
            @PathVariable UUID principalId,
            @RequestParam(required = false) Integer limit,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(
                queries.recentLogins(principals.current(), correlationId, principalId, limit),
                correlationId);
    }

    @GetMapping("/{principalId}/change-timeline")
    ResponseEntity<PrincipalChangeTimelinePage> changeTimeline(
            @PathVariable UUID principalId,
            @RequestParam(required = false) Integer limit,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(
                queries.changeTimeline(principals.current(), correlationId, principalId, limit),
                correlationId);
    }

    @PostMapping("/{principalId}/identity-links")
    ResponseEntity<SecurityPrincipalView> linkIdentity(
            @PathVariable UUID principalId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody LinkIdentityRequest request
    ) {
        return command(commands.linkIdentity(principals.current(), metadata(correlationId, idempotencyKey),
                principalId, version(ifMatch), request.issuer(), request.subject(), request.clientId()), correlationId);
    }

    @PostMapping("/{principalId}:disable")
    ResponseEntity<SecurityPrincipalView> disable(
            @PathVariable UUID principalId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody ReasonRequest request
    ) {
        return command(commands.disable(principals.current(), metadata(correlationId, idempotencyKey),
                principalId, version(ifMatch), request.reason()), correlationId);
    }

    @PostMapping("/{principalId}:enable")
    ResponseEntity<SecurityPrincipalView> enable(
            @PathVariable UUID principalId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody ReasonRequest request
    ) {
        return command(commands.enable(principals.current(), metadata(correlationId, idempotencyKey),
                principalId, version(ifMatch), request.reason()), correlationId);
    }

    @PostMapping("/{principalId}:update-profile")
    ResponseEntity<SecurityPrincipalView> updateProfile(
            @PathVariable UUID principalId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return command(commands.updateProfile(principals.current(), metadata(correlationId, idempotencyKey),
                principalId, version(ifMatch), request.displayName(), request.employeeNumber()), correlationId);
    }

    @PostMapping("/{principalId}/personas")
    ResponseEntity<PrincipalPersonaView> addPersona(
            @PathVariable UUID principalId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody AddPersonaRequest request
    ) {
        PrincipalPersonaView result = commands.addPersona(principals.current(),
                metadata(correlationId, idempotencyKey), principalId, version(ifMatch),
                request.personaType(), request.validFrom(), request.validTo());
        return ResponseEntity.ok().header(CorrelationIds.HEADER_NAME, correlationId).body(result);
    }

    private static CommandMetadata metadata(String correlationId, String idempotencyKey) {
        return new CommandMetadata(correlationId, idempotencyKey);
    }

    private static long version(String ifMatch) {
        if (ifMatch == null || !ifMatch.matches("\\\"[1-9][0-9]*\\\"")) {
            throw new IllegalArgumentException("If-Match must contain one quoted positive aggregate version");
        }
        return Long.parseLong(ifMatch.substring(1, ifMatch.length() - 1));
    }

    private static <T> ResponseEntity<T> response(T body, String correlationId) {
        return ResponseEntity.ok().header(CorrelationIds.HEADER_NAME, correlationId).body(body);
    }

    private static ResponseEntity<SecurityPrincipalView> command(SecurityPrincipalView body, String correlationId) {
        return ResponseEntity.ok().eTag(Long.toString(body.version()))
                .header(CorrelationIds.HEADER_NAME, correlationId).body(body);
    }
}

record RegisterPrincipalRequest(
        @NotBlank @Size(max = 200) String displayName,
        @Size(max = 128) String employeeNumber,
        @Size(max = 40) String personaType
) {}

record LinkIdentityRequest(
        @NotBlank @Size(max = 512) String issuer,
        @NotBlank @Size(max = 255) String subject,
        @Size(max = 128) String clientId
) {}

record ReasonRequest(@NotBlank @Size(max = 500) String reason) {}

record UpdateProfileRequest(
        @NotBlank @Size(max = 200) String displayName,
        @Size(max = 128) String employeeNumber
) {}

record AddPersonaRequest(
        @NotBlank @Size(max = 40) String personaType,
        Instant validFrom,
        Instant validTo
) {}
