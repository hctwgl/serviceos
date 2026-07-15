package com.serviceos.integration.byd.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.integration.byd.api.BydCpimReviewCallbackResponse;
import com.serviceos.integration.byd.application.BydCpimReviewCallbackService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BydCpimReviewCallbackController.class)
@Import(SecurityConfiguration.class)
class BydCpimReviewCallbackControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean BydCpimReviewCallbackService callbacks;

    @Test
    void callbackDoesNotRequireOidcBecauseCpimSignatureIsItsIdentityBoundary() throws Exception {
        when(callbacks.receive(any(), any(byte[].class), anyString()))
                .thenReturn(new BydCpimReviewCallbackResponse("success", java.util.List.of()));

        mvc.perform(post("/api/v1/integrations/byd/cpim/v7.3.1/review-results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("APP_KEY", "app")
                        .header("Nonce", "nonce")
                        .header("Cur_Time", "2026-07-15")
                        .header("Sign", "a".repeat(64))
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("success"));
    }
}
