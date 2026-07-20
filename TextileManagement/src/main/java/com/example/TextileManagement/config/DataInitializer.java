package com.example.TextileManagement.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.example.TextileManagement.repository.CompanyProfileRepository;
import com.example.TextileManagement.repository.UserAccountRepository;
import com.example.TextileManagement.repository.WorkspaceMemberRepository;
import com.example.TextileManagement.repository.WorkspaceRepository;
import com.example.TextileManagement.entities.CompanyProfile;
import com.example.TextileManagement.entities.UserAccount;
import com.example.TextileManagement.entities.Workspace;
import com.example.TextileManagement.entities.WorkspaceMember;

@Component
public class DataInitializer implements CommandLineRunner {
    private final UserAccountRepository userRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final String familyUsername;
    private final String familyPassword;
    private final String familyEmail;

    public DataInitializer(
            UserAccountRepository userRepository,
            CompanyProfileRepository companyProfileRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.auth.family-username}") String familyUsername,
            @Value("${app.auth.family-password}") String familyPassword,
            @Value("${app.auth.family-email:}") String familyEmail) {
        this.userRepository = userRepository;
        this.companyProfileRepository = companyProfileRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.passwordEncoder = passwordEncoder;
        this.familyUsername = familyUsername;
        this.familyPassword = familyPassword;
        this.familyEmail = familyEmail == null ? "" : familyEmail.trim().toLowerCase();
    }

    @Override
    public void run(String... args) {
        UserAccount owner = userRepository.findByUsernameIgnoreCase(familyUsername).map(user -> {
            if (!passwordEncoder.matches(familyPassword, user.getPasswordHash())) {
                user.setPasswordHash(passwordEncoder.encode(familyPassword));
                user.setAuthVersion((user.getAuthVersion() == null ? 0L : user.getAuthVersion()) + 1L);
            }
            if (user.getStatus() == null) {
                user.setStatus("ACTIVE");
            }
            if (user.getAuthVersion() == null) {
                user.setAuthVersion(0L);
            }
            if (user.getEmail() == null && !familyEmail.isBlank()) {
                user.setEmail(familyEmail);
            }
            return userRepository.save(user);
        }).orElseGet(() -> {
            UserAccount user = new UserAccount();
            user.setUsername(familyUsername);
            user.setEmail(familyEmail.isBlank() ? null : familyEmail);
            user.setPasswordHash(passwordEncoder.encode(familyPassword));
            user.setStatus("ACTIVE");
            return userRepository.save(user);
        });

        Workspace workspace = workspaceRepository.findBySlug("devashish-business-group").orElseGet(() -> {
            Workspace created = new Workspace();
            created.setName("Devashish Business Group");
            created.setSlug("devashish-business-group");
            created.setStatus("ACTIVE");
            return workspaceRepository.save(created);
        });

        ensureCompany("Devashish Textile", "+91 95121 51000", workspace);
        ensureCompany("Ritika Creation", "+91 95121 51001", workspace);
        ensureOwnerMembership(workspace, owner);
    }

    private void ensureCompany(String tradeName, String phone, Workspace workspace) {
        CompanyProfile profile = companyProfileRepository
                .findByWorkspace_IdAndTradeNameIgnoreCase(workspace.getId(), tradeName)
                .orElseGet(() -> {
            CompanyProfile created = new CompanyProfile();
            created.setTradeName(tradeName);
            created.setGstNo("");
            created.setPhone(phone);
            created.setAddress("");
            created.setDefaultQuality("ARTSILK CLOTH");
            created.setWorkspace(workspace);
            return companyProfileRepository.save(created);
        });
        if (profile.getWorkspace() == null) {
            profile.setWorkspace(workspace);
            companyProfileRepository.save(profile);
        }
    }

    private void ensureOwnerMembership(Workspace workspace, UserAccount owner) {
        workspaceMemberRepository.findByWorkspace_IdAndUser_Id(workspace.getId(), owner.getId()).orElseGet(() -> {
            WorkspaceMember membership = new WorkspaceMember();
            membership.setWorkspace(workspace);
            membership.setUser(owner);
            membership.setRole("OWNER");
            return workspaceMemberRepository.save(membership);
        });
    }
}
