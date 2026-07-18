package com.serviceos.fieldwork.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.fieldwork.api.TechnicianVisitCommandService;
import com.serviceos.fieldwork.api.VisitCommandReceipt;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M262：Technician Visit HTTP 只接受可信 JWT/context、严格版本与服务端在线语义。 */
@WebMvcTest(TechnicianVisitController.class)
@Import(SecurityConfiguration.class)
class TechnicianVisitControllerSecurityTest {
    private static final UUID PRINCIPAL = UUID.fromString("10000000-0000-4000-8000-000000000262");
    private static final UUID NETWORK = UUID.fromString("20000000-0000-4000-8000-000000000262");
    private static final String CONTEXT = "TECHNICIAN|NETWORK|" + NETWORK;
    private static final UUID APPOINTMENT = UUID.fromString("30000000-0000-4000-8000-000000000262");
    private static final UUID VISIT = UUID.fromString("40000000-0000-4000-8000-000000000262");

    @Autowired MockMvc mvc;
    @MockitoBean TechnicianVisitCommandService visits;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void anonymousAndMissingContextAreRejected() throws Exception {
        mvc.perform(post("/api/v1/technician/me/appointments/{id}/visits:check-in", APPOINTMENT)
                        .header("Idempotency-Key", "command-262")
                        .contentType("application/json").content(checkInBody()))
                .andExpect(status().isUnauthorized());

        when(principals.current()).thenReturn(principal());
        when(visits.checkIn(eq(principal()), org.mockito.ArgumentMatchers.any(), isNull(),
                org.mockito.ArgumentMatchers.any())).thenThrow(
                new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID, "缺少 X-Technician-Context"));
        mvc.perform(post("/api/v1/technician/me/appointments/{id}/visits:check-in", APPOINTMENT)
                        .with(jwt().jwt(token -> token.subject(PRINCIPAL.toString())
                                .claim("tenant_id", "tenant-262")))
                        .header("Idempotency-Key", "command-262")
                        .contentType("application/json").content(checkInBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("PORTAL_CONTEXT_INVALID"));
    }

    @Test
    void checkInForcesOnlineAndPassesTrustedContext() throws Exception {
        CurrentPrincipal principal = principal();
        when(principals.current()).thenReturn(principal);
        when(visits.checkIn(eq(principal), org.mockito.ArgumentMatchers.any(), eq(CONTEXT),
                org.mockito.ArgumentMatchers.any())).thenReturn(receipt("IN_PROGRESS", 1));

        mvc.perform(post("/api/v1/technician/me/appointments/{id}/visits:check-in", APPOINTMENT)
                        .with(jwt().jwt(token -> token.subject(PRINCIPAL.toString())
                                .claim("tenant_id", "tenant-262")))
                        .header("X-Technician-Context", CONTEXT)
                        .header("Idempotency-Key", "command-262")
                        .header("X-Tenant-Id", "spoofed")
                        .contentType("application/json").content(checkInBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.visitId").value(VISIT.toString()));

        verify(visits).checkIn(eq(principal),
                argThat((CommandMetadata metadata) -> metadata.idempotencyKey().equals("command-262")),
                eq(CONTEXT), argThat(command -> command.appointmentId().equals(APPOINTMENT)
                        && !command.offline()
                        && command.deviceCommandId().equals("command-262")));
    }

    @Test
    void interruptRequiresQuotedVersionAndKeepsEvidenceExplicit() throws Exception {
        CurrentPrincipal principal = principal();
        when(principals.current()).thenReturn(principal);
        when(visits.interrupt(eq(principal), org.mockito.ArgumentMatchers.any(), eq(CONTEXT),
                org.mockito.ArgumentMatchers.any())).thenReturn(receipt("INTERRUPTED", 2));

        mvc.perform(post("/api/v1/technician/me/visits/{id}:interrupt", VISIT)
                        .with(jwt().jwt(token -> token.subject(PRINCIPAL.toString())
                                .claim("tenant_id", "tenant-262")))
                        .header("X-Technician-Context", CONTEXT)
                        .header("Idempotency-Key", "interrupt-262").header("If-Match", "1")
                        .contentType("application/json").content(interruptBody()))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/technician/me/visits/{id}:interrupt", VISIT)
                        .with(jwt().jwt(token -> token.subject(PRINCIPAL.toString())
                                .claim("tenant_id", "tenant-262")))
                        .header("X-Technician-Context", CONTEXT)
                        .header("Idempotency-Key", "interrupt-262").header("If-Match", "\"1\"")
                        .contentType("application/json").content(interruptBody()))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"2\""));

        verify(visits).interrupt(eq(principal), org.mockito.ArgumentMatchers.any(), eq(CONTEXT),
                argThat(command -> command.visitId().equals(VISIT)
                        && command.expectedVersion() == 1
                        && command.evidenceRefs().isEmpty()));
    }

    private static CurrentPrincipal principal() {
        return new CurrentPrincipal(PRINCIPAL.toString(), "tenant-262",
                CurrentPrincipal.PrincipalType.USER, "technician-web", Set.of());
    }

    private static VisitCommandReceipt receipt(String status, long version) {
        return new VisitCommandReceipt(VISIT, status, version, "WITHIN_GEOFENCE", "ACCEPTED",
                Instant.parse("2026-07-18T10:00:01Z"));
    }

    private static String checkInBody() {
        return """
                {"capturedAt":"2026-07-18T10:00:00Z","deviceCommandId":"command-262",
                 "deviceId":"technician-web-session-262","location":{"latitude":31.2304,
                 "longitude":121.4737,"accuracyMeters":8}}
                """;
    }

    private static String interruptBody() {
        return """
                {"capturedAt":"2026-07-18T10:30:00Z","exceptionCode":"SITE_UNSAFE",
                 "note":"现场存在安全风险","evidenceRefs":[]}
                """;
    }
}
