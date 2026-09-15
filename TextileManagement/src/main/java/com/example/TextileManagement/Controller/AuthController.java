package com.example.TextileManagement.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.TextileManagement.entities.CompanyProfile;
import com.example.TextileManagement.entities.UserAccount;
import com.example.TextileManagement.entities.Workspace;
import com.example.TextileManagement.entities.WorkspaceMember;
import com.example.TextileManagement.repository.CompanyProfileRepository;
import com.example.TextileManagement.repository.UserAccountRepository;
import com.example.TextileManagement.repository.WorkspaceMemberRepository;
import com.example.TextileManagement.repository.WorkspaceRepository;
import com.example.TextileManagement.security.AuthRateLimiter;
import com.example.TextileManagement.security.AuthSessionDetails;
import com.example.TextileManagement.security.ClientIpResolver;
import com.example.TextileManagement.security.RequestAuthorizationFilter;
import com.example.TextileManagement.repository.CompanyProfileSummary;
import com.example.TextileManagement.repository.BootstrapAuthorizationProjection;
import com.example.TextileManagement.repository.RequestAuthorizationProjection;
import com.example.TextileManagement.service.PasswordResetService;
import com.example.TextileManagement.service.WorkspaceCollaborationService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final WorkspaceRepository workspaceRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final AuthRateLimiter authRateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final WorkspaceCollaborationService collaborationService;
    private final PasswordResetService passwordResetService;
    private final SecurityContextRepository securityContextRepository;
    private final boolean signupEnabled;

    public AuthController(UserAccountRepository userRepository, PasswordEncoder passwordEncoder,
            WorkspaceRepository workspaceRepository, CompanyProfileRepository companyProfileRepository,
            WorkspaceMemberRepository workspaceMemberRepository, AuthRateLimiter authRateLimiter,
            ClientIpResolver clientIpResolver,
            WorkspaceCollaborationService collaborationService, PasswordResetService passwordResetService,
            SecurityContextRepository securityContextRepository,
            @Value("${app.auth.signup-enabled:true}") boolean signupEnabled) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.workspaceRepository = workspaceRepository;
        this.companyProfileRepository = companyProfileRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.authRateLimiter = authRateLimiter;
        this.clientIpResolver = clientIpResolver;
        this.collaborationService = collaborationService;
        this.passwordResetService = passwordResetService;
        this.securityContextRepository = securityContextRepository;
        this.signupEnabled = signupEnabled;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest,
            HttpServletResponse response) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        String username = request == null ? null : normalizeUsername(request.username());
        String account = accountDimension(username);
        if (authRateLimiter.isBlocked("login", account, clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        String password = request == null ? null : request.password();
        if (username == null || password == null || username.isBlank() || password.isBlank()) {
            return loginFailure(account, clientIp);
        }
        return userRepository.findByUsernameIgnoreCase(username)
                .filter(user -> "ACTIVE".equalsIgnoreCase(user.getStatus()))
                .filter(user -> passwordEncoder.matches(password, user.getPasswordHash()))
                .map(user -> {
                    authRateLimiter.clearAccount("login", account);
                    authenticate(user, httpRequest, response);
                    return ResponseEntity.ok(new AuthResponse(user.getUsername()));
                })
                .orElseGet(() -> loginFailure(account, clientIp));
    }

    @PostMapping("/signup")
    @Transactional
    public ResponseEntity<AuthResponse> signup(@RequestBody SignupRequest request, HttpServletRequest httpRequest,
            HttpServletResponse response) {
        if (!signupEnabled) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.GONE,
                    "Public account creation is temporarily closed; use an invitation link");
        }
        String clientIp = clientIpResolver.resolve(httpRequest);
        String email = request == null ? null : normalizeEmail(request.email());
        String account = accountDimension(email);
        if (authRateLimiter.isBlocked("signup", account, clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        if (request == null) {
            authRateLimiter.recordFailure("signup", account, clientIp);
            return ResponseEntity.badRequest().build();
        }

        String password = request.password();
        String displayName = trim(request.displayName());
        String workspaceName = trim(request.workspaceName());
        String businessName = trim(request.businessName());
        if (!validEmail(email) || password == null || password.length() < 12
                || displayName.isBlank() || workspaceName.isBlank() || businessName.isBlank()) {
            authRateLimiter.recordFailure("signup", account, clientIp);
            return ResponseEntity.badRequest().build();
        }
        if (userRepository.existsByUsernameIgnoreCase(email)) {
            authRateLimiter.recordFailure("signup", account, clientIp);
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        UserAccount user = new UserAccount();
        user.setUsername(email);
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setStatus("ACTIVE");
        user.setPasswordHash(passwordEncoder.encode(password));
        user = userRepository.save(user);

        Workspace workspace = new Workspace();
        workspace.setName(workspaceName);
        workspace.setSlug(slug(workspaceName) + "-" + user.getId());
        workspace.setStatus("ACTIVE");
        workspace = workspaceRepository.save(workspace);

        CompanyProfile company = new CompanyProfile();
        company.setTradeName(businessName);
        company.setWorkspace(workspace);
        company = companyProfileRepository.save(company);

        WorkspaceMember membership = new WorkspaceMember();
        membership.setWorkspace(workspace);
        membership.setUser(user);
        membership.setRole("OWNER");
        workspaceMemberRepository.save(membership);

        authRateLimiter.clearAccount("signup", account);
        authenticate(user, httpRequest, response);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(user.getUsername()));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<PasswordResetResponse> forgotPassword(@RequestBody PasswordResetRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        String email = request == null ? null : normalizeEmail(request.email());
        String account = accountDimension(email);
        if (authRateLimiter.isBlocked("password-reset", account, clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        passwordResetService.requestReset(email);
        authRateLimiter.recordFailure("password-reset", account, clientIp);
        return ResponseEntity.accepted().body(new PasswordResetResponse(
                "If an account exists for that email, a reset link has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@RequestBody ResetPasswordRequest request, HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        String account = "reset-token";
        if (authRateLimiter.isBlocked("password-reset-submit", account, clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        try {
            passwordResetService.resetPassword(request == null ? null : request.token(),
                    request == null ? null : request.newPassword());
        } catch (RuntimeException exception) {
            authRateLimiter.recordFailure("password-reset-submit", account, clientIp);
            throw exception;
        }
        authRateLimiter.clearAccount("password-reset-submit", account);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication, HttpServletRequest request) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        RequestAuthorizationProjection access = authorizationProjection(request);
        if (access != null) {
            return ResponseEntity.ok(new UserResponse(access.getUsername(), access.getDisplayName()));
        }
        return userRepository.findByUsernameIgnoreCase(authentication.getName())
                .filter(user -> "ACTIVE".equalsIgnoreCase(user.getStatus()))
                .map(user -> ResponseEntity.ok(new UserResponse(user.getUsername(), user.getDisplayName())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @GetMapping("/bootstrap")
    public ResponseEntity<BootstrapResponse> bootstrap(Authentication authentication, HttpServletRequest request) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        RequestAuthorizationProjection access = authorizationProjection(request);
        if (access == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<CompanyProfileSummary> companies = bootstrapCompanies(request);
        if (companies == null) {
            companies = companyProfileRepository.findAccessibleSummariesByUsername(authentication.getName());
        }
        return ResponseEntity.ok(new BootstrapResponse(
                new UserResponse(access.getUsername(), access.getDisplayName()), companies));
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getToken());
    }

    @PostMapping("/invitations/accept")
    @Transactional
    public ResponseEntity<AuthResponse> acceptInvitation(@RequestBody AcceptInvitationRequest request,
            HttpServletRequest httpRequest, HttpServletResponse response) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        String account = "invitation-token";
        if (authRateLimiter.isBlocked("invitation", account, clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        if (request == null) {
            return ResponseEntity.badRequest().build();
        }
        UserAccount user;
        try {
            user = collaborationService.acceptAsNewUser(request.token(), request.displayName(), request.password());
        } catch (RuntimeException exception) {
            authRateLimiter.recordFailure("invitation", account, clientIp);
            throw exception;
        }
        authRateLimiter.clearAccount("invitation", account);
        authenticate(user, httpRequest, response);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(user.getUsername()));
    }

    @PostMapping("/invitations/accept-existing")
    public ResponseEntity<Void> acceptInvitationAsExistingUser(@RequestBody AcceptExistingInvitationRequest request,
            Authentication authentication, HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        String account = "invitation-token";
        if (authRateLimiter.isBlocked("invitation", account, clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        try {
            collaborationService.acceptAsExistingUser(request == null ? null : request.token(), authentication.getName());
        } catch (RuntimeException exception) {
            authRateLimiter.recordFailure("invitation", account, clientIp);
            throw exception;
        }
        authRateLimiter.clearAccount("invitation", account);
        return ResponseEntity.noContent().build();
    }

    private void authenticate(UserAccount user, HttpServletRequest request, HttpServletResponse response) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(user.getUsername(), null, List.of());
        authentication.setDetails(new AuthSessionDetails(user.getId(), user.getAuthVersion() == null ? 0L : user.getAuthVersion()));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private String normalizeUsername(String username) {
        return username == null ? null : username.trim();
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean validEmail(String email) {
        return email != null && email.length() <= 254 && email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }

    private String slug(String value) {
        String slug = value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "workspace" : slug;
    }

    private ResponseEntity<AuthResponse> loginFailure(String account, String clientIp) {
        authRateLimiter.recordFailure("login", account, clientIp);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    private RequestAuthorizationProjection authorizationProjection(HttpServletRequest request) {
        Object value = request.getAttribute(RequestAuthorizationFilter.AUTHORIZATION_ATTRIBUTE);
        return value instanceof RequestAuthorizationProjection projection ? projection : null;
    }

    @SuppressWarnings("unchecked")
    private List<CompanyProfileSummary> bootstrapCompanies(HttpServletRequest request) {
        Object value = request.getAttribute(RequestAuthorizationFilter.BOOTSTRAP_ATTRIBUTE);
        if (!(value instanceof List<?> rows)) {
            return null;
        }
        return rows.stream()
                .filter(BootstrapAuthorizationProjection.class::isInstance)
                .map(BootstrapAuthorizationProjection.class::cast)
                .filter(row -> row.getId() != null)
                .map(row -> (CompanyProfileSummary) row)
                .toList();
    }

    private String accountDimension(String normalizedAccount) {
        if (normalizedAccount == null || normalizedAccount.isBlank() || normalizedAccount.length() > 254) {
            return "invalid-account";
        }
        return normalizedAccount.toLowerCase(java.util.Locale.ROOT);
    }

    public record LoginRequest(String username, String password) {
    }

    public record AuthResponse(String username) {
    }

    public record CsrfResponse(String token) {
    }

    public record SignupRequest(String email, String password, String displayName, String workspaceName, String businessName) {
    }

    public record PasswordResetRequest(String email) {
    }

    public record ResetPasswordRequest(String token, String newPassword) {
    }

    public record PasswordResetResponse(String message) {
    }

    public record UserResponse(String username, String displayName) {
    }

    public record BootstrapResponse(UserResponse user, List<CompanyProfileSummary> companies) {
    }

    public record AcceptInvitationRequest(String token, String displayName, String password) {
    }

    public record AcceptExistingInvitationRequest(String token) {
    }
}
