package com.serviceos.project.application;

import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.project.api.CreateProjectCommand;
import com.serviceos.project.domain.Project;
import com.serviceos.reliability.api.IdempotencyDecision;
import com.serviceos.reliability.api.IdempotencyService;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.shared.CommandContext;
import com.serviceos.shared.CommandMetadata;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultProjectCommandServiceTest {
    @Test
    void derivesTenantAndActorFromPrincipalBeforeCallingReliabilityPorts() {
        FakeProjectRepository projects = new FakeProjectRepository();
        CapturingIdempotency idempotency = new CapturingIdempotency();
        List<AuditEntry> audits = new ArrayList<>();
        List<OutboxEvent> events = new ArrayList<>();
        Clock clock = Clock.fixed(Instant.parse("2026-07-13T03:30:00Z"), ZoneOffset.UTC);
        AuthorizationService authorization = allowAuthorization();
        DefaultProjectCommandService service = new DefaultProjectCommandService(
                projects, authorization, idempotency, audits::add, events::add, clock);
        CurrentPrincipal principal = new CurrentPrincipal(
                "trusted-user", "trusted-tenant", CurrentPrincipal.PrincipalType.USER,
                "admin-web", Set.of("project.create"));

        var result = service.create(
                principal,
                new CommandMetadata("corr-1", "idem-1"),
                new CreateProjectCommand(
                        "BYD-2026", "client-byd", "比亚迪项目",
                        LocalDate.of(2026, 1, 1), null, List.of("CN-3702"), List.of("network-a")));

        assertThat(result.tenantId()).isEqualTo("trusted-tenant");
        assertThat(idempotency.context.tenantId()).isEqualTo("trusted-tenant");
        assertThat(idempotency.context.actorId()).isEqualTo("trusted-user");
        assertThat(audits).singleElement().satisfies(audit -> {
            assertThat(audit.matchedGrantIds()).containsExactly("grant-1");
            assertThat(audit.authorizationPolicyVersion()).isEqualTo("policy-v1");
        });
        assertThat(events).singleElement().satisfies(event ->
                assertThat(event.outboxId()).isNotEqualTo(event.eventId()));
    }

    private static AuthorizationService allowAuthorization() {
        AuthorizationDecision allowed = new AuthorizationDecision(
                AuthorizationDecision.Effect.ALLOW,
                List.of(), List.of("grant-1"), List.of("TENANT:trusted-tenant"),
                List.of(), "policy-v1");
        return new AuthorizationService() {
            @Override
            public AuthorizationDecision authorize(
                    CurrentPrincipal principal,
                    AuthorizationRequest request,
                    String correlationId
            ) {
                return allowed;
            }

            @Override
            public AuthorizationDecision require(
                    CurrentPrincipal principal,
                    AuthorizationRequest request,
                    String correlationId
            ) {
                return allowed;
            }
        };
    }

    private static final class FakeProjectRepository implements ProjectRepository {
        private Project project;

        @Override
        public void insert(Project project) {
            this.project = project;
        }

        @Override
        public void insertRegionBindings(Project project, String createdBy) {
            // 单元测试内存仓库；绑定已包含在聚合中，无额外持久化动作。
        }

        @Override
        public void insertNetworkBindings(Project project, String createdBy) {
            // 单元测试内存仓库；绑定已包含在聚合中，无额外持久化动作。
        }

        @Override
        public Optional<Project> findById(String tenantId, UUID projectId) {
            return Optional.ofNullable(project)
                    .filter(candidate -> candidate.tenantId().equals(tenantId))
                    .filter(candidate -> candidate.id().equals(projectId));
        }

        @Override
        public Optional<Project> findByIdForUpdate(String tenantId, UUID projectId) {
            return findById(tenantId, projectId);
        }

        @Override
        public boolean advanceVersion(String tenantId, UUID projectId, long expectedVersion) {
            return project != null && project.tenantId().equals(tenantId)
                    && project.id().equals(projectId) && project.version() == expectedVersion;
        }

        @Override
        public void reviseRegionBindings(
                String tenantId, UUID projectId, List<String> removed, List<String> added,
                String actorId, Instant revisedAt
        ) {
            // 本测试只覆盖创建命令。
        }

        @Override
        public void reviseNetworkBindings(
                String tenantId, UUID projectId, List<String> removed, List<String> added,
                String actorId, Instant revisedAt
        ) {
            // 本测试只覆盖创建命令。
        }

        @Override
        public void insertScopeRevision(ProjectScopeRevision revision) {
            // 本测试只覆盖创建命令。
        }

        @Override
        public Optional<ProjectScopeRevision> findScopeRevision(String tenantId, UUID revisionId) {
            return Optional.empty();
        }
    }

    private static final class CapturingIdempotency implements IdempotencyService {
        private CommandContext context;

        @Override
        public IdempotencyDecision begin(
                CommandContext context,
                String operationType,
                String requestDigest
        ) {
            this.context = context;
            return IdempotencyDecision.newCommand();
        }

        @Override
        public void complete(
                CommandContext context,
                String operationType,
                String resourceId,
                String responseDigest
        ) {
            this.context = context;
        }
    }
}
