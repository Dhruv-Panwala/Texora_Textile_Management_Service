package com.example.TextileManagement.security;

import java.io.IOException;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.TextileManagement.config.CurrentCompanyContext;
import com.example.TextileManagement.service.WorkspaceRoleAccessService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class WorkspaceRoleAuthorizationFilter extends OncePerRequestFilter {
    private final CurrentCompanyContext currentCompanyContext;
    private final WorkspaceRoleAccessService roleAccessService;

    public WorkspaceRoleAuthorizationFilter(CurrentCompanyContext currentCompanyContext,
            WorkspaceRoleAccessService roleAccessService) {
        this.currentCompanyContext = currentCompanyContext;
        this.roleAccessService = roleAccessService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/auth/")
                || ("GET".equalsIgnoreCase(request.getMethod()) && !isMutatingGet(request))
                || "HEAD".equalsIgnoreCase(request.getMethod())
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    private boolean isMutatingGet(HttpServletRequest request) {
        return request.getRequestURI().matches("/api/sales/\\d+/bill\\.pdf");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long companyId = currentCompanyContext.getCompanyId();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            filterChain.doFilter(request, response);
            return;
        }
        if (companyId != null && roleAccessService.canWriteCompany(authentication.getName(), companyId)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Read-only workspace access\"}");
    }
}
