package com.serviceos.bootstrap;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
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
    SecurityFilterChain apiSecurity(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // 本地对象存储数据面使用 HMAC 短期能力 token；公网业务 API 仍要求 OIDC JWT。
                        .requestMatchers("/api/v1/file-transfers/**").permitAll()
                        .anyRequest().authenticated())
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
                            body.put("correlationId", request.getHeader("X-Correlation-Id"));
                            objectMapper.writeValue(response.getOutputStream(), body);
                        }))
                .build();
    }
}
