package com.example.TextileManagement.security;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class ClientIpResolver {
    private final boolean trustCloudflare;

    public ClientIpResolver(@Value("${app.trusted-proxy:none}") String trustedProxy) {
        this.trustCloudflare = "cloudflare".equalsIgnoreCase(trustedProxy);
    }

    public String resolve(HttpServletRequest request) {
        if (trustCloudflare) {
            String cloudflareIp = request.getHeader("CF-Connecting-IP");
            if (isIpAddress(cloudflareIp)) {
                return cloudflareIp.trim();
            }
        }
        String remoteAddress = request.getRemoteAddr();
        return isIpAddress(remoteAddress) ? remoteAddress : "unknown";
    }

    private boolean isIpAddress(String value) {
        if (value == null || value.isBlank() || value.length() > 45) {
            return false;
        }
        try {
            InetAddress.getByName(value.trim());
            return true;
        } catch (UnknownHostException ignored) {
            return false;
        }
    }
}
