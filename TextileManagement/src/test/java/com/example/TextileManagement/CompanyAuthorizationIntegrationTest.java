package com.example.TextileManagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import jakarta.servlet.http.Cookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.TextileManagement.entities.CompanyProfile;
import com.example.TextileManagement.entities.UserAccount;
import com.example.TextileManagement.entities.Workspace;
import com.example.TextileManagement.entities.WorkspaceMember;
import com.example.TextileManagement.repository.AuditEventRepository;
import com.example.TextileManagement.repository.CompanyProfileRepository;
import com.example.TextileManagement.repository.UserAccountRepository;
import com.example.TextileManagement.repository.WorkspaceMemberRepository;
import com.example.TextileManagement.repository.WorkspaceRepository;
import com.example.TextileManagement.security.AuthRateLimiter;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class CompanyAuthorizationIntegrationTest {
    private static final String PASSWORD = "integration-test-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceMemberRepository memberRepository;

    @Autowired
    private CompanyProfileRepository companyRepository;

    @Autowired
    private AuditEventRepository auditRepository;

    @Autowired
    private AuthRateLimiter authRateLimiter;

    @Test
    void writableCompanyUserCannotMutateViewerCompany() throws Exception {
        Fixture fixture = fixture("STAFF", "VIEWER");
        LoginSession login = login(fixture.username());

        mockMvc.perform(get("/api/dashboard?period=monthly")
                .cookie(login.sessionCookie(), login.csrfCookie())
                .header("X-Company-Id", Long.MAX_VALUE))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/auth/me").cookie(login.sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(fixture.username()));

        mockMvc.perform(put("/api/company/" + fixture.companyB().getId())
                .cookie(login.sessionCookie(), login.csrfCookie())
                .header("X-CSRF-TOKEN", login.csrf())
                .header("X-Company-Id", fixture.companyA().getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(companyBody("should-not-change", fixture.companyB().getVersion())))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(put("/api/company")
                .cookie(login.sessionCookie(), login.csrfCookie())
                .header("X-CSRF-TOKEN", login.csrf())
                .header("X-Company-Id", fixture.companyB().getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(companyBody("should-not-change", fixture.companyB().getVersion())))
                .andExpect(status().isForbidden());

        assertEquals(fixture.companyB().getTradeName(), companyRepository.findById(fixture.companyB().getId()).orElseThrow().getTradeName());
    }

    @Test
    void staffCannotChangeCompanySettings() throws Exception {
        assertSettingsRoleRejected("STAFF");
    }

    @Test
    void accountantCannotChangeCompanySettings() throws Exception {
        assertSettingsRoleRejected("ACCOUNTANT");
    }

    @Test
    void adminCanChangeSettingsAndAuditUsesTheMutatedCompany() throws Exception {
        Fixture fixture = fixture("ADMIN", "VIEWER");
        LoginSession login = login(fixture.username());

        mockMvc.perform(put("/api/company")
                .cookie(login.sessionCookie(), login.csrfCookie())
                .header("X-CSRF-TOKEN", login.csrf())
                .header("X-Company-Id", fixture.companyA().getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(companyBody("updated-company-a", fixture.companyA().getVersion())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeName").value("updated-company-a"));

        assertTrue(auditRepository.findTop100ByCompanyIdOrderByCreatedAtDesc(fixture.companyA().getId()).stream()
                .anyMatch(event -> "PUT".equals(event.getMethod())
                        && "/api/company".equals(event.getPath())
                        && event.getCompanyId().equals(fixture.companyA().getId())
                        && event.getStatusCode() == 200));
        assertTrue(auditRepository.findTop100ByCompanyIdOrderByCreatedAtDesc(fixture.companyB().getId()).stream()
                .noneMatch(event -> "/api/company".equals(event.getPath())));
    }

    @Test
    void successfulLoginDoesNotClearAnUnrelatedIpBucket() {
        String account = "rate-limit-account-" + System.nanoTime();
        String ip = "198.51.100.42";
        for (int attempt = 0; attempt < 10; attempt++) {
            authRateLimiter.recordFailure("regression-login", account, ip);
        }

        assertTrue(authRateLimiter.isBlocked("regression-login", account, ip));
        authRateLimiter.clearAccount("regression-login", account);
        assertTrue(authRateLimiter.isBlocked("regression-login", "another-account", ip));
    }

    @Test
    void csrfBootstrapUsesACookieWithoutAllocatingAnAnonymousSession() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andReturn();

        assertNull(result.getRequest().getSession(false));
    }

    @Test
    void oversizedJsonIsRejectedBeforeControllerProcessing() throws Exception {
        String oversizedBody = "{\"username\":\"" + "x".repeat(300_000) + "\",\"password\":\"x\"}";

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(oversizedBody))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void savedTakaBatchIsTenantScopedAndIdempotent() throws Exception {
        Fixture fixture = fixture("ADMIN", "VIEWER");
        LoginSession login = login(fixture.username());
        String key = "integration-batch-" + System.nanoTime();
        String body = "{\"entries\":[{\"takaNo\":123,\"meters\":450.5},{\"takaNo\":124,\"meters\":451}]}";

        mockMvc.perform(post("/api/taka-entries/batch")
                .cookie(login.sessionCookie(), login.csrfCookie())
                .header("X-CSRF-TOKEN", login.csrf())
                .header("X-Company-Id", fixture.companyA().getId())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].takaNo").value(123))
                .andExpect(jsonPath("$[1].meters").value(451));

        mockMvc.perform(post("/api/taka-entries/batch")
                .cookie(login.sessionCookie(), login.csrfCookie())
                .header("X-CSRF-TOKEN", login.csrf())
                .header("X-Company-Id", fixture.companyA().getId())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].takaNo").value(123));
    }

    private void assertSettingsRoleRejected(String role) throws Exception {
        Fixture fixture = fixture(role, "VIEWER");
        LoginSession login = login(fixture.username());

        mockMvc.perform(put("/api/company")
                .cookie(login.sessionCookie(), login.csrfCookie())
                .header("X-CSRF-TOKEN", login.csrf())
                .header("X-Company-Id", fixture.companyA().getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(companyBody("should-not-change", fixture.companyA().getVersion())))
                .andExpect(status().isForbidden());

        assertEquals(fixture.companyA().getTradeName(), companyRepository.findById(fixture.companyA().getId()).orElseThrow().getTradeName());
    }

    private LoginSession login(String username) throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        String csrfToken = csrfCookie.getValue();
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .cookie(csrfCookie)
                .header("X-CSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie sessionCookie = loginResult.getResponse().getCookie("JSESSIONID");
        return new LoginSession(java.util.Objects.requireNonNull(sessionCookie), csrfToken, csrfCookie);
    }

    private Fixture fixture(String roleA, String roleB) {
        String suffix = Long.toString(System.nanoTime());
        UserAccount user = new UserAccount();
        user.setUsername("company-security-" + suffix + "@example.com");
        user.setEmail(user.getUsername());
        user.setDisplayName("Security Test User");
        user.setStatus("ACTIVE");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user = userRepository.saveAndFlush(user);

        Workspace workspaceA = workspace("workspace-a-" + suffix);
        Workspace workspaceB = workspace("workspace-b-" + suffix);
        CompanyProfile companyA = company("Company A " + suffix, workspaceA);
        CompanyProfile companyB = company("Company B " + suffix, workspaceB);
        member(workspaceA, user, roleA);
        member(workspaceB, user, roleB);
        return new Fixture(user.getUsername(), companyA, companyB);
    }

    private Workspace workspace(String name) {
        Workspace workspace = new Workspace();
        workspace.setName(name);
        workspace.setSlug(name);
        workspace.setStatus("ACTIVE");
        return workspaceRepository.saveAndFlush(workspace);
    }

    private CompanyProfile company(String name, Workspace workspace) {
        CompanyProfile company = new CompanyProfile();
        company.setTradeName(name);
        company.setWorkspace(workspace);
        return companyRepository.saveAndFlush(company);
    }

    private void member(Workspace workspace, UserAccount user, String role) {
        WorkspaceMember member = new WorkspaceMember();
        member.setWorkspace(workspace);
        member.setUser(user);
        member.setRole(role);
        memberRepository.saveAndFlush(member);
    }

    private String companyBody(String tradeName, Long version) {
        return "{\"tradeName\":\"" + tradeName + "\",\"version\":" + version + "}";
    }

    private record Fixture(String username, CompanyProfile companyA, CompanyProfile companyB) {
    }

    private record LoginSession(Cookie sessionCookie, String csrf, Cookie csrfCookie) {
    }
}
