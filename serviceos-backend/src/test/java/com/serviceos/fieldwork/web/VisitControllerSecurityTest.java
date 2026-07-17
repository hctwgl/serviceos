package com.serviceos.fieldwork.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.fieldwork.api.VisitCommandReceipt;
import com.serviceos.fieldwork.api.VisitLocation;
import com.serviceos.fieldwork.api.VisitService;
import com.serviceos.fieldwork.api.VisitView;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M32 HTTP 边界拒绝匿名访问、租户伪造和非双引号 If-Match。 */
@WebMvcTest(VisitController.class)
@Import(SecurityConfiguration.class)
class VisitControllerSecurityTest {
    private static final UUID APPOINTMENT = UUID.fromString("30000000-0000-4000-8000-000000000032");
    private static final UUID VISIT = UUID.fromString("40000000-0000-4000-8000-000000000032");

    @Autowired MockMvc mvc;
    @MockitoBean VisitService visits;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void unauthenticatedVisitCommandsAreRejected() throws Exception {
        mvc.perform(post("/api/v1/appointments/{id}/visits:check-in", APPOINTMENT)
                        .header("Idempotency-Key", "device-command-1")
                        .contentType("application/json").content(checkInBody()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/visits/{id}:check-out", VISIT)
                        .header("Idempotency-Key", "checkout-1").header("If-Match", "\"1\"")
                        .contentType("application/json").content(checkOutBody()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/visits/{id}", VISIT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedGetVisitUsesTrustedPrincipalAndReturnsEtag() throws Exception {
        CurrentPrincipal principal = principal();
        when(principals.current()).thenReturn(principal);
        UUID projectId = UUID.fromString("10000000-0000-4000-8000-000000000032");
        UUID workOrderId = UUID.fromString("20000000-0000-4000-8000-000000000032");
        UUID taskId = UUID.fromString("50000000-0000-4000-8000-000000000032");
        when(visits.get(eq(principal), anyString(), eq(VISIT))).thenReturn(new VisitView(
                VISIT, projectId, workOrderId, taskId, APPOINTMENT, 1, "technician-032",
                "network-a", "COMPLETED",
                Instant.parse("2026-07-14T12:00:00Z"), Instant.parse("2026-07-14T12:00:01Z"),
                new VisitLocation(31.23, 121.47, 8), "WITHIN_GEOFENCE", 12.0, "policy-v1",
                "ACCEPTED", "device-032", "device-command-1", false,
                Instant.parse("2026-07-14T12:30:00Z"), Instant.parse("2026-07-14T12:30:01Z"),
                "SERVICE_COMPLETED", null, null, List.of("operation://1"), List.of(), 2, List.of()));

        mvc.perform(get("/api/v1/visits/{id}", VISIT)
                        .with(jwt().jwt(token -> token.subject("technician-032")
                                .claim("tenant_id", "tenant-trusted"))))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.visitId").value(VISIT.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        verify(visits).get(eq(principal), anyString(), eq(VISIT));
    }

    @Test
    void trustedPrincipalAndDeviceIdentityReachApplicationBoundary() throws Exception {
        CurrentPrincipal principal = principal();
        when(principals.current()).thenReturn(principal);
        when(visits.checkIn(eq(principal), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(new VisitCommandReceipt(
                VISIT, "IN_PROGRESS", 1, "WITHIN_GEOFENCE", "ACCEPTED",
                Instant.parse("2026-07-14T12:00:01Z")));

        mvc.perform(post("/api/v1/appointments/{id}/visits:check-in", APPOINTMENT)
                        .with(jwt().jwt(token -> token.subject("technician-032")
                                .claim("tenant_id", "tenant-trusted")))
                        .header("Idempotency-Key", "device-command-1")
                        .header("X-Tenant-Id", "spoofed-tenant")
                        .contentType("application/json").content(checkInBody()))
                .andExpect(status().isCreated()).andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.visitId").value(VISIT.toString()));

        verify(visits).checkIn(eq(principal),
                argThat((CommandMetadata value) -> value.idempotencyKey().equals("device-command-1")),
                argThat(command -> command.appointmentId().equals(APPOINTMENT)
                        && command.deviceCommandId().equals("device-command-1")
                        && command.location().accuracyMeters() == 8));
    }

    @Test
    void unquotedVersionIsRejectedBeforeCheckoutExecution() throws Exception {
        when(principals.current()).thenReturn(principal());
        mvc.perform(post("/api/v1/visits/{id}:check-out", VISIT)
                        .with(jwt().jwt(token -> token.subject("technician-032")
                                .claim("tenant_id", "tenant-trusted")))
                        .header("Idempotency-Key", "checkout-1").header("If-Match", "1")
                        .contentType("application/json").content(checkOutBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    private static CurrentPrincipal principal() {
        return new CurrentPrincipal("technician-032", "tenant-trusted",
                CurrentPrincipal.PrincipalType.USER, "mobile-web", Set.of());
    }

    private static String checkInBody() {
        return """
                {"capturedAt":"2026-07-14T12:00:00Z","deviceCommandId":"device-command-1",
                 "deviceId":"device-032","location":{"latitude":31.2304,"longitude":121.4737,
                 "accuracyMeters":8},"offline":false}
                """;
    }

    private static String checkOutBody() {
        return """
                {"capturedAt":"2026-07-14T13:00:00Z","resultCode":"SERVICE_COMPLETED",
                 "operationRefs":["operation://survey/1"]}
                """;
    }
}
