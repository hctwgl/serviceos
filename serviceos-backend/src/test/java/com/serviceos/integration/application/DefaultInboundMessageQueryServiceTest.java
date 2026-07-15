package com.serviceos.integration.application;

import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.integration.api.InboundEnvelopeView;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultInboundMessageQueryServiceTest {
    private final InboundMessageRepository repository = mock(InboundMessageRepository.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final DefaultInboundMessageQueryService service =
            new DefaultInboundMessageQueryService(repository, authorization);

    @Test
    void completedEnvelopeUsesServerTenantAndProjectScopeWithoutExposingObjectReference() {
        UUID envelopeId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        CurrentPrincipal principal = new CurrentPrincipal(
                "operator", "tenant-integration", CurrentPrincipal.PrincipalType.USER,
                "ops-web", Set.of());
        InboundEnvelopeView view = new InboundEnvelopeView(
                envelopeId, projectId, "byd-cpim-v7.3.1", "CREATE_WORK_ORDER", "nonce-1",
                "a".repeat(64), "b".repeat(64), "VALID", "COMPLETED", "map-v1",
                UUID.randomUUID(), "ACCEPTED", "WORK_ORDER", UUID.randomUUID().toString(),
                Instant.parse("2026-07-15T07:00:00Z"), Instant.parse("2026-07-15T07:00:01Z"),
                "corr-1");
        when(repository.findEnvelope(principal.tenantId(), envelopeId))
                .thenReturn(Optional.of(new InboundMessageRepository.InboundEnvelopeRecord(
                        view, "private/raw/object.json", "c".repeat(64))));
        when(authorization.require(org.mockito.ArgumentMatchers.eq(principal),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("corr-read")))
                .thenReturn(AuthorizationDecision.allow());

        assertThat(service.getEnvelope(principal, "corr-read", envelopeId)).isEqualTo(view);

        ArgumentCaptor<AuthorizationRequest> request = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(authorization).require(
                org.mockito.ArgumentMatchers.eq(principal), request.capture(),
                org.mockito.ArgumentMatchers.eq("corr-read"));
        assertThat(request.getValue().capability()).isEqualTo("integration.readInbound");
        assertThat(request.getValue().tenantId()).isEqualTo(principal.tenantId());
        assertThat(request.getValue().projectId()).isEqualTo(projectId.toString());
    }

    @Test
    void rejectedEnvelopeWithoutProjectRequiresTenantScope() {
        UUID envelopeId = UUID.randomUUID();
        CurrentPrincipal principal = new CurrentPrincipal(
                "operator", "tenant-integration", CurrentPrincipal.PrincipalType.USER,
                "ops-web", Set.of());
        InboundEnvelopeView view = new InboundEnvelopeView(
                envelopeId, null, "byd-cpim-v7.3.1", "CREATE_WORK_ORDER", "nonce-2",
                "a".repeat(64), null, "VALID", "REJECTED", null,
                null, "INVALID_ORDER", null, null,
                Instant.parse("2026-07-15T07:00:00Z"), Instant.parse("2026-07-15T07:00:01Z"),
                "corr-2");
        when(repository.findEnvelope(principal.tenantId(), envelopeId))
                .thenReturn(Optional.of(new InboundMessageRepository.InboundEnvelopeRecord(
                        view, "private/raw/object.json", "d".repeat(64))));
        when(authorization.require(org.mockito.ArgumentMatchers.eq(principal),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("corr-read")))
                .thenReturn(AuthorizationDecision.allow());

        service.getEnvelope(principal, "corr-read", envelopeId);

        ArgumentCaptor<AuthorizationRequest> request = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(authorization).require(
                org.mockito.ArgumentMatchers.eq(principal), request.capture(),
                org.mockito.ArgumentMatchers.eq("corr-read"));
        assertThat(request.getValue().projectId()).isNull();
        assertThat(request.getValue().capability()).isEqualTo("integration.readInbound");
    }
}
