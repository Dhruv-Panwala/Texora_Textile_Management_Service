package com.example.TextileManagement.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.TextileManagement.config.CurrentCompanyContext;
import com.example.TextileManagement.service.WorkspaceRoleAccessService;

class WorkspaceRoleAuthorizationFilterTest {
    private final CurrentCompanyContext companyContext = new CurrentCompanyContext();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        companyContext.clear();
    }

    @Test
    void viewerMutationIsRejected() throws Exception {
        WorkspaceRoleAccessService accessService = mock(WorkspaceRoleAccessService.class);
        when(accessService.canWriteCompany("viewer@example.com", 7L)).thenReturn(false);
        WorkspaceRoleAuthorizationFilter filter = new WorkspaceRoleAuthorizationFilter(companyContext, accessService);
        companyContext.setCompanyId(7L);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("viewer@example.com", "ignored", "ROLE_USER"));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/sales");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }
}
