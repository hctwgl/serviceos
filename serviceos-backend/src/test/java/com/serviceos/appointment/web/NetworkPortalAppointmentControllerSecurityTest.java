package com.serviceos.appointment.web;

import com.serviceos.appointment.api.AppointmentCommandReceipt;
import com.serviceos.appointment.api.NetworkPortalAppointmentService;
import com.serviceos.appointment.api.ProposeAppointmentCommand;
import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M197/M198：network-portal appointments 未认证 401；缺能力 403；改约/取消回执。 */
@WebMvcTest(NetworkPortalAppointmentController.class)
@Import(SecurityConfiguration.class)
class NetworkPortalAppointmentControllerSecurityTest {
    private static final UUID TASK_ID = UUID.fromString("019f83c0-9999-7f8c-9505-36fe5c0e880b");
    private static final UUID APPOINTMENT_ID = UUID.fromString("019f83c0-aaaa-7f8c-9505-36fe5c0e880d");
    private static final UUID NETWORK_ID = UUID.fromString("019f83c0-2222-7f8c-9505-36fe5c0e8803");
    private static final UUID PRINCIPAL_ID = UUID.fromString("019f83c0-1111-7f8c-9505-36fe5c0e8801");

    @Autowired MockMvc mvc;
    @MockitoBean NetworkPortalAppointmentService portalAppointments;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void unauthenticatedProposeIsRejected() throws Exception {
        mvc.perform(post("/api/v1/network-portal/tasks/{taskId}/appointments", TASK_ID)
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID)
                        .header("Idempotency-Key", "m197-1")
                        .contentType("application/json")
                        .content(proposeBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingCapabilityReturnsAccessDenied() throws Exception {
        CurrentPrincipal actor = actor();
        when(principals.current()).thenReturn(actor);
        when(portalAppointments.propose(
                eq(actor), any(), eq("NETWORK|NETWORK|" + NETWORK_ID), eq(TASK_ID), any()))
                .thenThrow(new BusinessProblem(ProblemCode.ACCESS_DENIED, "The action is not allowed"));

        mvc.perform(post("/api/v1/network-portal/tasks/{taskId}/appointments", TASK_ID)
                        .with(jwt().jwt(token -> token.subject("external-subject")
                                .claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-deny")
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID)
                        .header("Idempotency-Key", "m197-deny")
                        .contentType("application/json")
                        .content(proposeBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void authenticatedProposeReturnsReceipt() throws Exception {
        CurrentPrincipal actor = actor();
        AppointmentCommandReceipt receipt = new AppointmentCommandReceipt(
                APPOINTMENT_ID, UUID.randomUUID(), "PROPOSED", 1, 1,
                Instant.parse("2026-07-17T02:00:00Z"));
        when(principals.current()).thenReturn(actor);
        when(portalAppointments.propose(
                eq(actor), any(), eq("NETWORK|NETWORK|" + NETWORK_ID), eq(TASK_ID),
                any(ProposeAppointmentCommand.class)))
                .thenReturn(receipt);

        mvc.perform(post("/api/v1/network-portal/tasks/{taskId}/appointments", TASK_ID)
                        .with(jwt().jwt(token -> token.subject("external-subject")
                                .claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-ok")
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID)
                        .header("Idempotency-Key", "m197-ok")
                        .contentType("application/json")
                        .content(proposeBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointmentId").value(APPOINTMENT_ID.toString()))
                .andExpect(jsonPath("$.status").value("PROPOSED"));
    }

    @Test
    void authenticatedConfirmReturnsReceipt() throws Exception {
        CurrentPrincipal actor = actor();
        AppointmentCommandReceipt receipt = new AppointmentCommandReceipt(
                APPOINTMENT_ID, UUID.randomUUID(), "CONFIRMED", 2, 2,
                Instant.parse("2026-07-17T03:00:00Z"));
        when(principals.current()).thenReturn(actor);
        when(portalAppointments.confirm(
                eq(actor), any(), eq("NETWORK|NETWORK|" + NETWORK_ID), eq(APPOINTMENT_ID),
                eq(1L), eq("NETWORK_MEMBER"), eq(PRINCIPAL_ID.toString()), eq("PHONE")))
                .thenReturn(receipt);

        mvc.perform(post("/api/v1/network-portal/appointments/{appointmentId}:confirm", APPOINTMENT_ID)
                        .with(jwt().jwt(token -> token.subject("external-subject")
                                .claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-confirm")
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID)
                        .header("Idempotency-Key", "m197-confirm")
                        .header("If-Match", "\"1\"")
                        .contentType("application/json")
                        .content("""
                                {
                                  "confirmedPartyType": "NETWORK_MEMBER",
                                  "confirmedPartyRef": "%s",
                                  "confirmationChannel": "PHONE"
                                }
                                """.formatted(PRINCIPAL_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void authenticatedListReturnsEmpty() throws Exception {
        CurrentPrincipal actor = actor();
        when(principals.current()).thenReturn(actor);
        when(portalAppointments.listByTask(
                eq(actor), any(), eq("NETWORK|NETWORK|" + NETWORK_ID), eq(TASK_ID)))
                .thenReturn(List.of());

        mvc.perform(get("/api/v1/network-portal/tasks/{taskId}/appointments", TASK_ID)
                        .with(jwt().jwt(token -> token.subject("external-subject")
                                .claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-list")
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedRescheduleIsRejected() throws Exception {
        mvc.perform(post("/api/v1/network-portal/appointments/{appointmentId}:reschedule", APPOINTMENT_ID)
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID)
                        .header("Idempotency-Key", "m198-1")
                        .header("If-Match", "\"2\"")
                        .contentType("application/json")
                        .content(rescheduleBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedCancelIsRejected() throws Exception {
        mvc.perform(post("/api/v1/network-portal/appointments/{appointmentId}:cancel", APPOINTMENT_ID)
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID)
                        .header("Idempotency-Key", "m198-2")
                        .header("If-Match", "\"1\"")
                        .contentType("application/json")
                        .content("""
                                { "reasonCode": "CUSTOMER_CANCELLED", "note": "cancel" }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingCapabilityOnRescheduleReturnsAccessDenied() throws Exception {
        CurrentPrincipal actor = actor();
        when(principals.current()).thenReturn(actor);
        when(portalAppointments.reschedule(
                eq(actor), any(), eq("NETWORK|NETWORK|" + NETWORK_ID), eq(APPOINTMENT_ID),
                eq(2L), any(), eq("CUSTOMER_REQUESTED_LATER"), eq("改约")))
                .thenThrow(new BusinessProblem(ProblemCode.ACCESS_DENIED, "The action is not allowed"));

        mvc.perform(post("/api/v1/network-portal/appointments/{appointmentId}:reschedule", APPOINTMENT_ID)
                        .with(jwt().jwt(token -> token.subject("external-subject")
                                .claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-deny-reschedule")
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID)
                        .header("Idempotency-Key", "m198-deny-reschedule")
                        .header("If-Match", "\"2\"")
                        .contentType("application/json")
                        .content(rescheduleBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void authenticatedRescheduleReturnsReceipt() throws Exception {
        CurrentPrincipal actor = actor();
        AppointmentCommandReceipt receipt = new AppointmentCommandReceipt(
                APPOINTMENT_ID, UUID.randomUUID(), "PROPOSED", 3, 3,
                Instant.parse("2026-07-17T04:00:00Z"));
        when(principals.current()).thenReturn(actor);
        when(portalAppointments.reschedule(
                eq(actor), any(), eq("NETWORK|NETWORK|" + NETWORK_ID), eq(APPOINTMENT_ID),
                eq(2L), any(), eq("CUSTOMER_REQUESTED_LATER"), eq("改约")))
                .thenReturn(receipt);

        mvc.perform(post("/api/v1/network-portal/appointments/{appointmentId}:reschedule", APPOINTMENT_ID)
                        .with(jwt().jwt(token -> token.subject("external-subject")
                                .claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-reschedule")
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID)
                        .header("Idempotency-Key", "m198-reschedule")
                        .header("If-Match", "\"2\"")
                        .contentType("application/json")
                        .content(rescheduleBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROPOSED"));
    }

    @Test
    void authenticatedCancelReturnsReceipt() throws Exception {
        CurrentPrincipal actor = actor();
        AppointmentCommandReceipt receipt = new AppointmentCommandReceipt(
                APPOINTMENT_ID, UUID.randomUUID(), "CANCELLED", 2, 2,
                Instant.parse("2026-07-17T05:00:00Z"));
        when(principals.current()).thenReturn(actor);
        when(portalAppointments.cancel(
                eq(actor), any(), eq("NETWORK|NETWORK|" + NETWORK_ID), eq(APPOINTMENT_ID),
                eq(1L), eq("CUSTOMER_CANCELLED"), eq("cancel")))
                .thenReturn(receipt);

        mvc.perform(post("/api/v1/network-portal/appointments/{appointmentId}:cancel", APPOINTMENT_ID)
                        .with(jwt().jwt(token -> token.subject("external-subject")
                                .claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-cancel")
                        .header("X-Network-Context", "NETWORK|NETWORK|" + NETWORK_ID)
                        .header("Idempotency-Key", "m198-cancel")
                        .header("If-Match", "\"1\"")
                        .contentType("application/json")
                        .content("""
                                { "reasonCode": "CUSTOMER_CANCELLED", "note": "cancel" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    private static String proposeBody() {
        return """
                {
                  "type": "SURVEY",
                  "window": {
                    "start": "2026-08-10T01:00:00Z",
                    "end": "2026-08-10T04:00:00Z",
                    "timezone": "Asia/Shanghai",
                    "estimatedDurationMinutes": 120
                  },
                  "addressRef": "addr-ref",
                  "addressVersion": "addr-v1"
                }
                """;
    }

    private static String rescheduleBody() {
        return """
                {
                  "newWindow": {
                    "start": "2026-09-11T02:00:00Z",
                    "end": "2026-09-11T05:00:00Z",
                    "timezone": "Asia/Shanghai",
                    "estimatedDurationMinutes": 120
                  },
                  "reasonCode": "CUSTOMER_REQUESTED_LATER",
                  "note": "改约"
                }
                """;
    }

    private static CurrentPrincipal actor() {
        return new CurrentPrincipal(
                PRINCIPAL_ID.toString(),
                "tenant-a",
                CurrentPrincipal.PrincipalType.USER,
                "network-portal",
                Set.of());
    }
}
