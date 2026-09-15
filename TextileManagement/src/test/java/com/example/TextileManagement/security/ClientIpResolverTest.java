package com.example.TextileManagement.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {
    @Test
    void ignoresForwardingHeadersWhenProxyIsNotTrusted() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        request.addHeader("X-Forwarded-For", "198.51.100.20");
        request.addHeader("CF-Connecting-IP", "198.51.100.21");

        assertEquals("192.0.2.10", new ClientIpResolver("none").resolve(request));
    }

    @Test
    void acceptsOnlyAValidCloudflareClientIpWhenConfigured() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("CF-Connecting-IP", "198.51.100.21");

        assertEquals("198.51.100.21", new ClientIpResolver("cloudflare").resolve(request));
    }
}
