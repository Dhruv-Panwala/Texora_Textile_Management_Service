package com.example.TextileManagement.security;

import java.io.IOException;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.TextileManagement.config.CurrentCompanyContext;
import com.example.TextileManagement.repository.CompanyProfileRepository;
import com.example.TextileManagement.repository.BootstrapAuthorizationProjection;
import com.example.TextileManagement.repository.RequestAuthorizationProjection;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** One request-scoped user/company/role lookup replaces three serial lookups. */
@Component
public class RequestAuthorizationFilter extends OncePerRequestFilter {
    public static final String HEADER_NAME = "X-Company-Id";
    public static final String AUTHORIZATION_ATTRIBUTE = RequestAuthorizationFilter.class.getName() + ".projection";
    public static final String BOOTSTRAP_ATTRIBUTE = RequestAuthorizationFilter.class.getName() + ".bootstrap";
    private final CurrentCompanyContext currentCompanyContext;
    private final CompanyProfileRepository companyProfileRepository;

    public RequestAuthorizationFilter(CurrentCompanyContext currentCompanyContext,
            CompanyProfileRepository companyProfileRepository) {
        this.currentCompanyContext = currentCompanyContext;
        this.companyProfileRepository = companyProfileRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            filterChain.doFilter(request, response);
            return;
        }

        if ("/api/auth/bootstrap".equals(request.getRequestURI())) {
            handleBootstrap(request, response, filterChain, authentication);
            return;
        }

        boolean invitationRequest = request.getRequestURI().startsWith("/api/auth/invitations/");
        RequestAuthorizationProjection access;
        if (invitationRequest) {
            // Preserve the old invitation flow: validate session invalidation, but do not require company/role context.
            access = companyProfileRepository.findFirstRequestAuthorization(authentication.getName()).orElse(null);
            if (access == null || !validSession(authentication, access)) {
                rejectUnauthorized(request, response);
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        String requestedCompany = request.getHeader(HEADER_NAME);
        if (requestedCompany == null || requestedCompany.isBlank()) {
            requestedCompany = request.getParameter("companyId");
        }
        Long companyId = parseCompanyId(requestedCompany, response);
        if (response.isCommitted()) {
            return;
        }

        access = companyId == null
                ? companyProfileRepository.findFirstRequestAuthorization(authentication.getName()).orElse(null)
                : companyProfileRepository.findRequestAuthorization(authentication.getName(), companyId).orElse(null);
        if (access == null && companyId != null) {
            // A valid session with an inaccessible company is forbidden, not expired.
            RequestAuthorizationProjection identity = companyProfileRepository
                    .findFirstRequestAuthorization(authentication.getName()).orElse(null);
            if (identity != null && validSession(authentication, identity)) {
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden");
                return;
            }
            access = identity;
        }
        if (access == null || !validSession(authentication, access)) {
            rejectUnauthorized(request, response);
            return;
        }
        if (access.getCompanyId() == null) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden");
            return;
        }

        currentCompanyContext.setCompanyId(access.getCompanyId());
        request.setAttribute(AUTHORIZATION_ATTRIBUTE, access);
        try {
            if (isWriteRequest(request) && !canWrite(access.getRole())) {
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "Read-only workspace access");
                return;
            }
            filterChain.doFilter(request, response);
        } finally {
            currentCompanyContext.clear();
        }
    }

    private void handleBootstrap(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain,
            Authentication authentication) throws IOException, ServletException {
        String requestedCompany = request.getHeader(HEADER_NAME);
        if (requestedCompany == null || requestedCompany.isBlank()) {
            requestedCompany = request.getParameter("companyId");
        }
        Long requestedCompanyId = parseCompanyId(requestedCompany, response);
        if (response.isCommitted()) {
            return;
        }

        java.util.List<BootstrapAuthorizationProjection> rows = companyProfileRepository
                .findBootstrapAuthorization(authentication.getName());
        RequestAuthorizationProjection identity = rows.isEmpty() ? null : rows.get(0);
        if (identity == null || !validSession(authentication, identity)) {
            rejectUnauthorized(request, response);
            return;
        }
        if (identity.getCompanyId() == null) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden");
            return;
        }
        if (requestedCompanyId != null && rows.stream().noneMatch(row -> requestedCompanyId.equals(row.getCompanyId()))) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden");
            return;
        }
        request.setAttribute(AUTHORIZATION_ATTRIBUTE, identity);
        request.setAttribute(BOOTSTRAP_ATTRIBUTE, rows);
        filterChain.doFilter(request, response);
    }

    private Long parseCompanyId(String value, HttpServletResponse response) throws IOException {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long companyId = Long.parseLong(value.trim());
            if (companyId <= 0) {
                throw new NumberFormatException();
            }
            return companyId;
        } catch (NumberFormatException ignored) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid company context");
            return null;
        }
    }

    private boolean validSession(Authentication authentication, RequestAuthorizationProjection access) {
        if (!(authentication.getDetails() instanceof AuthSessionDetails details)) {
            return false;
        }
        return java.util.Objects.equals(details.userId(), access.getUserId())
                && details.authVersion() != null
                && details.authVersion().equals(access.getAuthVersion())
                && "ACTIVE".equalsIgnoreCase(access.getUserStatus());
    }

    private boolean isWriteRequest(HttpServletRequest request) {
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method)) {
            return "GET".equalsIgnoreCase(method)
                    && request.getRequestURI().matches("/api/sales/\\d+/bill\\.pdf");
        }
        return true;
    }

    private boolean canWrite(String role) {
        return role != null && !"VIEWER".equalsIgnoreCase(role.trim());
    }

    private void rejectUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
