package com.example.TextileManagement.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.TextileManagement.service.AuditService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AuditFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(AuditFilter.class);
    private final AuditService auditService;
    private final ClientIpResolver clientIpResolver;

    public AuditFilter(AuditService auditService, ClientIpResolver clientIpResolver) {
        this.auditService = auditService;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            try {
                auditService.record(request.getMethod(), request.getRequestURI(), response.getStatus(),
                        clientIpResolver.resolve(request));
            } catch (RuntimeException exception) {
                // ponytail: audit failure must not turn a successful business request into a 500 response.
                log.warn("Could not persist audit event");
            }
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        return !path.startsWith("/api/")
                || "GET".equalsIgnoreCase(method)
                || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method);
    }
}
