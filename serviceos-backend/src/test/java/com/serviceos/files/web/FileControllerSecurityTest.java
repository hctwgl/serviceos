package com.serviceos.files.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.files.api.FileCommandService;
import com.serviceos.files.api.UploadSessionView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CommandMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileController.class)
@Import(SecurityConfiguration.class)
class FileControllerSecurityTest {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    FileCommandService files;

    @MockitoBean
    CurrentPrincipalProvider principals;

    @Test
    void controlPlaneRejectsUnauthenticatedCaller() throws Exception {
        mvc.perform(post("/api/v1/files/upload-sessions")
                        .header("Idempotency-Key", "idem-file-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    void tenantAndActorComeFromCurrentPrincipalRatherThanSpoofedHeaders() throws Exception {
        CurrentPrincipal principal = new CurrentPrincipal(
                "user-trusted", "tenant-trusted", CurrentPrincipal.PrincipalType.USER,
                "technician-app", Set.of("file.upload"));
        UploadSessionView result = new UploadSessionView(
                UUID.fromString("41a51bf2-b180-4708-9b28-6925c9d72110"),
                UUID.fromString("d413c44f-42f2-4d64-8748-26d34271d15e"),
                "CREATED", "PUT", "http://localhost/transfer/token", Map.of(),
                Instant.parse("2026-07-13T06:15:00Z"), Instant.parse("2026-07-13T07:00:00Z"));
        when(principals.current()).thenReturn(principal);
        when(files.beginUpload(eq(principal), any(), any())).thenReturn(result);

        mvc.perform(post("/api/v1/files/upload-sessions")
                        .with(jwt().jwt(token -> token.subject("user-trusted")
                                .claim("tenant_id", "tenant-trusted")))
                        .header("Idempotency-Key", "idem-file-2")
                        .header("X-Correlation-Id", "corr-file-2")
                        .header("X-Tenant-Id", "tenant-spoofed")
                        .header("X-Actor-Id", "actor-spoofed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Correlation-Id", "corr-file-2"))
                .andExpect(jsonPath("$.uploadSessionId")
                        .value("41a51bf2-b180-4708-9b28-6925c9d72110"));

        verify(files).beginUpload(
                eq(principal),
                argThat((CommandMetadata metadata) ->
                        metadata.correlationId().equals("corr-file-2")
                                && metadata.idempotencyKey().equals("idem-file-2")),
                argThat(command -> command.businessContextId().equals("task-123")));
    }

    private static String validRequest() {
        return """
                {
                  "businessContextType": "Task",
                  "businessContextId": "task-123",
                  "originalFileName": "charger-nameplate.jpg",
                  "declaredMimeType": "image/jpeg",
                  "expectedSize": 1024,
                  "expectedSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                }
                """;
    }
}
