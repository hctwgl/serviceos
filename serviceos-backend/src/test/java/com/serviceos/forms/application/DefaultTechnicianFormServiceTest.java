package com.serviceos.forms.application;

import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.configuration.api.ClientCapabilityRuntimeGate;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.dispatch.api.TechnicianActiveAssignmentQuery;
import com.serviceos.forms.api.FormSubmissionService;
import com.serviceos.forms.api.TaskFormDefinition;
import com.serviceos.forms.api.TaskFormQueryService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.network.api.NetworkTechnicianMembershipView;
import com.serviceos.network.api.PrincipalNetworkAffiliationQuery;
import com.serviceos.network.api.TechnicianProfileView;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultTechnicianFormServiceTest {
    private static final UUID PRINCIPAL = UUID.fromString("10000000-0000-4000-8000-000000000263");
    private static final UUID PROFILE = UUID.fromString("20000000-0000-4000-8000-000000000263");
    private static final UUID NETWORK = UUID.fromString("30000000-0000-4000-8000-000000000263");
    private static final UUID TASK = UUID.fromString("40000000-0000-4000-8000-000000000263");
    private static final UUID PROJECT = UUID.fromString("50000000-0000-4000-8000-000000000263");

    private final PrincipalNetworkAffiliationQuery affiliations = mock(PrincipalNetworkAffiliationQuery.class);
    private final TechnicianActiveAssignmentQuery assignments = mock(TechnicianActiveAssignmentQuery.class);
    private final TaskFulfillmentContextService tasks = mock(TaskFulfillmentContextService.class);
    private final TaskFormQueryService forms = mock(TaskFormQueryService.class);
    private final FormSubmissionService submissions = mock(FormSubmissionService.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final ClientCapabilityRuntimeGate clientCapabilityRuntimeGate =
            mock(ClientCapabilityRuntimeGate.class);
    private DefaultTechnicianFormService service;

    @BeforeEach
    void setUp() {
        service = new DefaultTechnicianFormService(
                affiliations, assignments, tasks, forms, submissions, authorization,
                clientCapabilityRuntimeGate,
                Clock.fixed(Instant.parse("2026-07-18T12:00:00Z"), ZoneOffset.UTC));
        when(affiliations.findActiveTechnicianProfile("tenant-263", PRINCIPAL))
                .thenReturn(Optional.of(new TechnicianProfileView(
                        PROFILE, PRINCIPAL, "师傅", "ACTIVE", null, 1,
                        Instant.EPOCH, Instant.EPOCH, null, null, null)));
        when(affiliations.listActiveTechnicianMemberships(eq("tenant-263"), eq(PROFILE), any()))
                .thenReturn(List.of(new NetworkTechnicianMembershipView(
                        UUID.randomUUID(), NETWORK, PROFILE, "ACTIVE",
                        Instant.EPOCH, null, "fixture", Instant.EPOCH,
                        null, null, null, 1)));
        when(tasks.find("tenant-263", TASK)).thenReturn(Optional.of(task(PROFILE.toString())));
        when(assignments.filterTaskIdsForNetwork("tenant-263", NETWORK.toString(), List.of(TASK)))
                .thenReturn(List.of(TASK));
    }

    @Test
    void activeContextAndCurrentResponsibilityDelegateToFrozenFormQuery() {
        TaskFormDefinition definition = new TaskFormDefinition(
                TASK, UUID.randomUUID(), "survey", "1.0.0", "FORM_V1", "{}", "digest");
        when(forms.listForTask(principal(), "corr-263", TASK)).thenReturn(List.of(definition));

        assertThat(service.listForTask(
                principal(), "corr-263", "TECHNICIAN|NETWORK|" + NETWORK, "TECHNICIAN_WEB", TASK))
                .containsExactly(definition);
        verify(forms).listForTask(principal(), "corr-263", TASK);
    }

    @Test
    void iosClientRejectsConditionalFormAtRuntime() {
        TaskFormDefinition definition = new TaskFormDefinition(
                TASK, UUID.randomUUID(), "survey", "1.0.0", "FORM_V1", "{\"formKey\":\"survey\"}", "digest");
        when(forms.listForTask(principal(), "corr-ios", TASK)).thenReturn(List.of(definition));
        doThrow(new BusinessProblem(ProblemCode.CLIENT_CAPABILITY_UNSUPPORTED,
                "当前客户端（师傅 iOS）不支持本任务所需配置能力：表单字段条件显隐 visibleWhen（form.condition.visibleWhen）"))
                .when(clientCapabilityRuntimeGate)
                .requireCompatible(eq("TECHNICIAN_IOS"), eq(ConfigurationAssetType.FORM), any(), any());
        assertThatThrownBy(() -> service.listForTask(
                principal(), "corr-ios", "TECHNICIAN|NETWORK|" + NETWORK, "TECHNICIAN_IOS", TASK))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code())
                                .isEqualTo(ProblemCode.CLIENT_CAPABILITY_UNSUPPORTED));
    }

    @Test
    void forgedContextAndChangedResponsibilityFailClosed() {
        UUID forged = UUID.fromString("60000000-0000-4000-8000-000000000263");
        assertThatThrownBy(() -> service.listForTask(
                principal(), "corr-forged", "TECHNICIAN|NETWORK|" + forged, "TECHNICIAN_WEB", TASK))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.PORTAL_CONTEXT_INVALID));

        when(tasks.find("tenant-263", TASK)).thenReturn(Optional.of(task("another-technician")));
        assertThatThrownBy(() -> service.listForTask(
                principal(), "corr-changed", "TECHNICIAN|NETWORK|" + NETWORK, "TECHNICIAN_WEB", TASK))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.RESOURCE_NOT_FOUND));
    }

    private static TaskFulfillmentContext task(String responsible) {
        return new TaskFulfillmentContext(
                TASK, PROJECT, UUID.randomUUID(), UUID.randomUUID(), "bundle-digest",
                "SURVEY", "SURVEY", "HUMAN", "survey", null, null, null, null,
                "RUNNING", responsible, false, 1);
    }

    private static CurrentPrincipal principal() {
        return new CurrentPrincipal(PRINCIPAL.toString(), "tenant-263",
                CurrentPrincipal.PrincipalType.USER, "technician-form-test", Set.of());
    }
}
