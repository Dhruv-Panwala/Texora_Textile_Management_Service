package com.example.TextileManagement.security;

import java.io.IOException;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.TextileManagement.entities.UserAccount;
import com.example.TextileManagement.repository.UserAccountRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class PasswordSessionVersionFilter extends OncePerRequestFilter {
    private final UserAccountRepository userRepository;

    public PasswordSessionVersionFilter(UserAccountRepository userRepository) {
        this.userRepository = userRepository;
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

        if (!(authentication.getDetails() instanceof AuthSessionDetails details)) {
            reject(request, response);
            return;
        }

        UserAccount user = userRepository.findById(details.userId()).orElse(null);
        long currentVersion = user == null || user.getAuthVersion() == null ? 0L : user.getAuthVersion();
        if (user == null || details.authVersion() == null || !"ACTIVE".equalsIgnoreCase(user.getStatus())
                || currentVersion != details.authVersion()) {
            reject(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
