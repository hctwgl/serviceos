package com.serviceos.appointment.web;

import com.serviceos.appointment.api.AppointmentCommandReceipt;
import com.serviceos.appointment.api.AppointmentService;
import com.serviceos.appointment.api.ContactAttemptView;
import com.serviceos.appointment.api.ContactResultCode;
import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CommandMetadata;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M30 HTTP 边界只接受 JWT 主体、标准幂等键和双引号 ETag。 */
@WebMvcTest(AppointmentController.class)
@Import(SecurityConfiguration.class)
class AppointmentControllerSecurityTest {
    private static final UUID APPOINTMENT_ID =
            UUID.fromString("30000000-0000-4000-8000-000000000030");

    @Autowired MockMvc mvc;
    @MockitoBean AppointmentService appointments;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void unauthenticatedConfirmIsRejected() throws Exception {
        mvc.perform(post("/api/v1/appointments/{id}:confirm", APPOINTMENT_ID)
                        .header("Idempotency-Key", "confirm-1").header("If-Match", "\"1\"")
                        .contentType("application/json").content(confirmBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void trustedPrincipalAndVersionReachApplicationBoundary() throws Exception {
        CurrentPrincipal principal = new CurrentPrincipal(
                "scheduler", "tenant-trusted", CurrentPrincipal.PrincipalType.USER,
                "network-web", Set.of());
        when(principals.current()).thenReturn(principal);
        when(appointments.confirm(eq(principal),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AppointmentCommandReceipt(
                        APPOINTMENT_ID, UUID.randomUUID(), "CONFIRMED", 2, 2,
                        Instant.parse("2026-07-14T12:00:00Z")));

        mvc.perform(post("/api/v1/appointments/{id}:confirm", APPOINTMENT_ID)
                        .with(jwt().jwt(token -> token.subject("scheduler")
                                .claim("tenant_id", "tenant-trusted")))
                        .header("Idempotency-Key", "confirm-1").header("If-Match", "\"1\"")
                        .header("X-Tenant-Id", "spoofed-tenant")
                        .contentType("application/json").content(confirmBody()))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        verify(appointments).confirm(eq(principal),
                argThat((CommandMetadata metadata) -> metadata.idempotencyKey().equals("confirm-1")),
                argThat(command -> command.appointmentId().equals(APPOINTMENT_ID)
                        && command.expectedVersion() == 1
                        && command.confirmedPartyType().equals("CUSTOMER")));
    }

    @Test
    void unquotedIfMatchIsRejectedBeforeCommandExecution() throws Exception {
        when(principals.current()).thenReturn(new CurrentPrincipal(
                "scheduler", "tenant-trusted", CurrentPrincipal.PrincipalType.USER,
                "network-web", Set.of()));
        mvc.perform(post("/api/v1/appointments/{id}:confirm", APPOINTMENT_ID)
                        .with(jwt().jwt(token -> token.subject("scheduler")
                                .claim("tenant_id", "tenant-trusted")))
                        .header("Idempotency-Key", "confirm-1").header("If-Match", "1")
                        .contentType("application/json").content(confirmBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void unauthenticatedM31CommandsAreRejected() throws Exception {
        mvc.perform(post("/api/v1/tasks/{id}/contact-attempts", APPOINTMENT_ID)
                        .header("Idempotency-Key", "contact-1")
                        .contentType("application/json").content(contactBody()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/appointments/{id}:cancel", APPOINTMENT_ID)
                        .header("Idempotency-Key", "cancel-1").header("If-Match", "\"1\"")
                        .contentType("application/json").content("{\"reasonCode\":\"CUSTOMER_CANCELLED\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/appointments/{id}:mark-no-show", APPOINTMENT_ID)
                        .header("Idempotency-Key", "no-show-1").header("If-Match", "\"2\"")
                        .contentType("application/json").content("""
                                {"noShowPartyType":"CUSTOMER","noShowPartyRef":"customer-ref",
                                 "reasonCode":"CUSTOMER_ABSENT","evidenceRefs":[]}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void contactRequestReachesTrustedApplicationBoundary() throws Exception {
        CurrentPrincipal principal = new CurrentPrincipal(
                "scheduler", "tenant-trusted", CurrentPrincipal.PrincipalType.USER,
                "network-web", Set.of());
        when(principals.current()).thenReturn(principal);
        UUID attemptId = UUID.randomUUID();
        when(appointments.recordContactAttempt(eq(principal),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ContactAttemptView(
                        attemptId, UUID.randomUUID(), UUID.randomUUID(), APPOINTMENT_ID, "PHONE",
                        "customer-ref", Instant.parse("2026-07-14T11:00:00Z"),
                        Instant.parse("2026-07-14T11:01:00Z"), ContactResultCode.CONNECTED,
                        null, null, null, "scheduler", Instant.parse("2026-07-14T11:01:00Z")));

        mvc.perform(post("/api/v1/tasks/{id}/contact-attempts", APPOINTMENT_ID)
                        .with(jwt().jwt(token -> token.subject("scheduler").claim("tenant_id", "tenant-trusted")))
                        .header("Idempotency-Key", "contact-1")
                        .contentType("application/json").content(contactBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultCode").value("CONNECTED"));

        verify(appointments).recordContactAttempt(eq(principal),
                argThat((CommandMetadata metadata) -> metadata.idempotencyKey().equals("contact-1")),
                argThat(command -> command.taskId().equals(APPOINTMENT_ID)
                        && command.resultCode() == ContactResultCode.CONNECTED));
    }

    private static String confirmBody() {
        return """
                {
                  "confirmedPartyType": "CUSTOMER",
                  "confirmedPartyRef": "customer-ref",
                  "confirmationChannel": "PHONE"
                }
                """;
    }

    private static String contactBody() {
        return """
                {"channel":"PHONE","contactedPartyRef":"customer-ref",
                 "startedAt":"2026-07-14T11:00:00Z","endedAt":"2026-07-14T11:01:00Z",
                 "resultCode":"CONNECTED"}
                """;
    }
}
