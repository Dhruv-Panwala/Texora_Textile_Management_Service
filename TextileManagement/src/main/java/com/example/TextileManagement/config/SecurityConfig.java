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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import jakarta.servlet.http.HttpServletResponse;

import com.example.TextileManagement.security.AuditFilter;
import com.example.TextileManagement.security.RequestAuthorizationFilter;
import com.example.TextileManagement.security.JsonBodySizeFilter;
import com.example.TextileManagement.security.CompanyContextFilter;
import com.example.TextileManagement.security.PasswordSessionVersionFilter;
import com.example.TextileManagement.security.WorkspaceRoleAuthorizationFilter;

@Configuration
public class SecurityConfig {
    @Value("${app.cors.allowed-origin}")
    private String allowedOrigin;

    @Value("${server.servlet.session.cookie.secure:false}")
    private boolean sessionCookieSecure;

    @Value("${server.servlet.session.cookie.same-site:lax}")
    private String sessionCookieSameSite;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, RequestAuthorizationFilter authorizationFilter,
            AuditFilter auditFilter,
            SecurityContextRepository securityContextRepository, JsonBodySizeFilter jsonBodySizeFilter) throws Exception {
        return http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.migrateSession()))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/health", "/health/live", "/health/ready", "/hello", "/api/auth/csrf", "/api/auth/login", "/api/auth/signup",
                                "/api/auth/invitations/accept", "/api/auth/forgot-password",
                                "/api/auth/reset-password").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; img-src 'self' blob:"))
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(Customizer.withDefaults())
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)))
                .addFilterAfter(authorizationFilter, org.springframework.security.web.context.SecurityContextHolderFilter.class)
                .addFilterAfter(auditFilter, RequestAuthorizationFilter.class)
                .addFilterBefore(jsonBodySizeFilter, org.springframework.security.web.context.SecurityContextHolderFilter.class)
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(HttpServletResponse.SC_NO_CONTENT)))
                .build();
    }

    @Bean
    FilterRegistrationBean<AuditFilter> auditFilterRegistration(AuditFilter filter) {
        return disabledServletRegistration(filter);
    }

    @Bean
    FilterRegistrationBean<RequestAuthorizationFilter> requestAuthorizationFilterRegistration(
            RequestAuthorizationFilter filter) {
        return disabledServletRegistration(filter);
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
    FilterRegistrationBean<JsonBodySizeFilter> jsonBodySizeFilterRegistration(JsonBodySizeFilter filter) {
        return disabledServletRegistration(filter);
    }

    private <T extends jakarta.servlet.Filter> FilterRegistrationBean<T> disabledServletRegistration(T filter) {
        FilterRegistrationBean<T> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setHeaderName("X-CSRF-TOKEN");
        repository.setCookieName("XSRF-TOKEN");
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie
                .secure(sessionCookieSecure)
                .sameSite(sessionCookieSameSite));
        return repository;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-Company-Id", "X-CSRF-TOKEN", "X-Request-ID"));
        configuration.setExposedHeaders(List.of("Content-Disposition", "X-Request-ID", "Server-Timing"));
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
