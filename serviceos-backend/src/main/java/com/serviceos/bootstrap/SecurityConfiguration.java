package com.serviceos.bootstrap;

import com.serviceos.shared.CorrelationIds;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ServiceOS 作为 OIDC Resource Server，只验证外部身份提供方签发的 JWT，不实现密码登录。
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain apiSecurity(
            HttpSecurity http,
            ObjectMapper objectMapper,
            CorrelationContextFilter correlationContextFilter,
            @Value("${serviceos.observability.allow-anonymous-metrics:false}") boolean allowAnonymousMetrics
    ) throws Exception {
        return http
                .addFilterBefore(correlationContextFilter, SecurityContextHolderFilter.class)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                    authorize.requestMatchers("/livez", "/readyz").permitAll();
                    if (allowAnonymousMetrics) {
                        authorize.requestMatchers("/actuator/prometheus").permitAll();
                    }
                    // 本地对象存储数据面使用 HMAC 短期能力 token；公网业务 API 仍要求 OIDC JWT。
                    authorize.requestMatchers("/api/v1/file-transfers/**").permitAll();
                    // 车企回调使用各自协议签名认证，不使用 OIDC JWT；业务验签必须在适配器服务内完成。
                    authorize.requestMatchers("/api/v1/integrations/byd/cpim/v7.3.1/install-orders").permitAll();
                    authorize.requestMatchers("/api/v1/integrations/byd/cpim/v7.3.1/review-results").permitAll();
                    authorize.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                            Map<String, Object> body = new LinkedHashMap<>();
                            body.put("type", "https://serviceos.example/problems/unauthenticated");
                            body.put("title", "UNAUTHENTICATED");
                            body.put("status", HttpServletResponse.SC_UNAUTHORIZED);
                            body.put("detail", "A valid bearer token is required");
                            body.put("errorCode", "UNAUTHENTICATED");
                            body.put("correlationId", CorrelationIds.fromRequestAttribute(
                                    request.getAttribute(CorrelationIds.REQUEST_ATTRIBUTE)));
                            objectMapper.writeValue(response.getOutputStream(), body);
                        }))
                .build();
    }

    @Bean
    CorrelationContextFilter correlationContextFilter() {
        return new CorrelationContextFilter();
    }
}
