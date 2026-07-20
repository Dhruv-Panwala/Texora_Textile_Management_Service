package com.example.TextileManagement.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.Customizer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import jakarta.servlet.http.HttpServletResponse;

import com.example.TextileManagement.security.CompanyContextFilter;
import com.example.TextileManagement.security.AuditFilter;
import com.example.TextileManagement.security.PasswordSessionVersionFilter;
import com.example.TextileManagement.security.WorkspaceRoleAuthorizationFilter;

@Configuration
public class SecurityConfig {
    @Value("${app.cors.allowed-origin}")
    private String allowedOrigin;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, CompanyContextFilter companyFilter,
            PasswordSessionVersionFilter passwordSessionVersionFilter,
            WorkspaceRoleAuthorizationFilter roleFilter, AuditFilter auditFilter,
            SecurityContextRepository securityContextRepository) throws Exception {
        return http
                .csrf(csrf -> csrf.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.migrateSession()))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/health", "/hello", "/api/auth/csrf", "/api/auth/login", "/api/auth/signup",
                                "/api/auth/invitations/accept", "/api/auth/forgot-password",
                                "/api/auth/reset-password").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; img-src 'self' blob:"))
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(Customizer.withDefaults())
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)))
                .addFilterAfter(passwordSessionVersionFilter, org.springframework.security.web.context.SecurityContextHolderFilter.class)
                .addFilterAfter(companyFilter, PasswordSessionVersionFilter.class)
                .addFilterAfter(roleFilter, CompanyContextFilter.class)
                .addFilterAfter(auditFilter, WorkspaceRoleAuthorizationFilter.class)
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(HttpServletResponse.SC_NO_CONTENT)))
                .build();
    }

    @Bean
    FilterRegistrationBean<PasswordSessionVersionFilter> passwordSessionVersionFilterRegistration(
            PasswordSessionVersionFilter filter) {
        return disabledServletRegistration(filter);
    }

    @Bean
    FilterRegistrationBean<CompanyContextFilter> companyContextFilterRegistration(CompanyContextFilter filter) {
        return disabledServletRegistration(filter);
    }

    @Bean
    FilterRegistrationBean<WorkspaceRoleAuthorizationFilter> workspaceRoleAuthorizationFilterRegistration(
            WorkspaceRoleAuthorizationFilter filter) {
        return disabledServletRegistration(filter);
    }

    @Bean
    FilterRegistrationBean<AuditFilter> auditFilterRegistration(AuditFilter filter) {
        return disabledServletRegistration(filter);
    }

    private <T extends jakarta.servlet.Filter> FilterRegistrationBean<T> disabledServletRegistration(T filter) {
        FilterRegistrationBean<T> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-Company-Id", "X-CSRF-TOKEN"));
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }
}
