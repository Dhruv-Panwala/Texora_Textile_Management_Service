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
    private final WorkspaceCollaborationService collaborationService;
    private final PasswordResetService passwordResetService;
    private final SecurityContextRepository securityContextRepository;
    private final boolean signupEnabled;

    public AuthController(UserAccountRepository userRepository, PasswordEncoder passwordEncoder,
            WorkspaceRepository workspaceRepository, CompanyProfileRepository companyProfileRepository,
            WorkspaceMemberRepository workspaceMemberRepository, AuthRateLimiter authRateLimiter,
            WorkspaceCollaborationService collaborationService, PasswordResetService passwordResetService,
            SecurityContextRepository securityContextRepository,
            @Value("${app.auth.signup-enabled:true}") boolean signupEnabled) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.workspaceRepository = workspaceRepository;
        this.companyProfileRepository = companyProfileRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.authRateLimiter = authRateLimiter;
        this.collaborationService = collaborationService;
        this.passwordResetService = passwordResetService;
        this.securityContextRepository = securityContextRepository;
        this.signupEnabled = signupEnabled;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest,
            HttpServletResponse response) {
        String rateLimitKey = rateLimitKey("login", httpRequest);
        if (!authRateLimiter.allow(rateLimitKey)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        if (request == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String username = normalizeUsername(request.username());
        String password = request.password();
        if (username == null || password == null || username.isBlank() || password.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return userRepository.findByUsernameIgnoreCase(username)
                .filter(user -> "ACTIVE".equalsIgnoreCase(user.getStatus()))
                .filter(user -> passwordEncoder.matches(password, user.getPasswordHash()))
                .map(user -> {
                    authRateLimiter.clear(rateLimitKey);
                    authenticate(user, httpRequest, response);
                    return ResponseEntity.ok(new AuthResponse(user.getUsername()));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @PostMapping("/signup")
    @Transactional
    public ResponseEntity<AuthResponse> signup(@RequestBody SignupRequest request, HttpServletRequest httpRequest,
            HttpServletResponse response) {
        if (!signupEnabled) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.GONE,
                    "Public account creation is temporarily closed; use an invitation link");
        }
        String rateLimitKey = rateLimitKey("signup", httpRequest);
        if (!authRateLimiter.allow(rateLimitKey)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        if (request == null) {
            return ResponseEntity.badRequest().build();
        }

        String email = normalizeEmail(request.email());
        String password = request.password();
        String displayName = trim(request.displayName());
        String workspaceName = trim(request.workspaceName());
        String businessName = trim(request.businessName());
        if (!validEmail(email) || password == null || password.length() < 12
                || displayName.isBlank() || workspaceName.isBlank() || businessName.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (userRepository.existsByUsernameIgnoreCase(email)) {
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

        authRateLimiter.clear(rateLimitKey);
        authenticate(user, httpRequest, response);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(user.getUsername()));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<PasswordResetResponse> forgotPassword(@RequestBody PasswordResetRequest request,
            HttpServletRequest httpRequest) {
        String ipKey = rateLimitKey("password-reset", httpRequest);
        if (!authRateLimiter.allow(ipKey)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        String email = request == null ? null : normalizeEmail(request.email());
        if (validEmail(email) && !authRateLimiter.allow("password-reset-email:" + email)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        passwordResetService.requestReset(email);
        return ResponseEntity.accepted().body(new PasswordResetResponse(
                "If an account exists for that email, a reset link has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@RequestBody ResetPasswordRequest request, HttpServletRequest httpRequest) {
        String rateLimitKey = rateLimitKey("password-reset-submit", httpRequest);
        if (!authRateLimiter.allow(rateLimitKey)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        passwordResetService.resetPassword(request == null ? null : request.token(),
                request == null ? null : request.newPassword());
        authRateLimiter.clear(rateLimitKey);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return userRepository.findByUsernameIgnoreCase(authentication.getName())
                .filter(user -> "ACTIVE".equalsIgnoreCase(user.getStatus()))
                .map(user -> ResponseEntity.ok(new UserResponse(user.getUsername(), user.getDisplayName())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getToken());
    }

    @PostMapping("/invitations/accept")
    @Transactional
    public ResponseEntity<AuthResponse> acceptInvitation(@RequestBody AcceptInvitationRequest request,
            HttpServletRequest httpRequest, HttpServletResponse response) {
        String rateLimitKey = rateLimitKey("invitation", httpRequest);
        if (!authRateLimiter.allow(rateLimitKey)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        if (request == null) {
            return ResponseEntity.badRequest().build();
        }
        UserAccount user = collaborationService.acceptAsNewUser(request.token(), request.displayName(), request.password());
        authRateLimiter.clear(rateLimitKey);
        authenticate(user, httpRequest, response);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(user.getUsername()));
    }

    @PostMapping("/invitations/accept-existing")
    public ResponseEntity<Void> acceptInvitationAsExistingUser(@RequestBody AcceptExistingInvitationRequest request,
            Authentication authentication) {
        collaborationService.acceptAsExistingUser(request == null ? null : request.token(), authentication.getName());
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

    private String rateLimitKey(String action, HttpServletRequest request) {
        return action + ":" + request.getRemoteAddr();
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

    public record AcceptInvitationRequest(String token, String displayName, String password) {
    }

    public record AcceptExistingInvitationRequest(String token) {
    }
}
