package com.example.TextileManagement.security;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.TextileManagement.config.CurrentCompanyContext;
import com.example.TextileManagement.entities.CompanyProfile;
import com.example.TextileManagement.service.CompanyAccessService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CompanyContextFilter extends OncePerRequestFilter {
    public static final String HEADER_NAME = "X-Company-Id";

    private final CurrentCompanyContext currentCompanyContext;
    private final CompanyAccessService companyAccessService;

    public CompanyContextFilter(CurrentCompanyContext currentCompanyContext, CompanyAccessService companyAccessService) {
        this.currentCompanyContext = currentCompanyContext;
        this.companyAccessService = companyAccessService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null
                    || !authentication.isAuthenticated()
                    || authentication instanceof AnonymousAuthenticationToken) {
                filterChain.doFilter(request, response);
                return;
            }

            Long companyId = resolveCompanyId(request.getHeader(HEADER_NAME), authentication.getName(), response);
            if (companyId == null) {
                return;
            }
            currentCompanyContext.setCompanyId(companyId);
            filterChain.doFilter(request, response);
        } finally {
            currentCompanyContext.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/auth/invitations/");
    }

    private Long resolveCompanyId(String headerValue, String username, HttpServletResponse response) throws IOException {
        if (headerValue != null && !headerValue.isBlank()) {
            try {
                Long companyId = Long.valueOf(headerValue.trim());
                if (companyAccessService.canAccess(username, companyId)) {
                    return companyId;
                }
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden");
                return null;
            } catch (NumberFormatException ignored) {
                writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid company context");
                return null;
            }
        }

        for (CompanyProfile company : companyAccessService.findAccessibleCompanies(username)) {
            return company.getId();
        }
        writeError(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden");
        return null;
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
