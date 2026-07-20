package com.example.TextileManagement.security;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class AuthRateLimiter {
    private static final int MAX_ATTEMPTS = 10;
    private static final long WINDOW_MILLIS = Duration.ofMinutes(15).toMillis();

    private final ConcurrentHashMap<String, AttemptWindow> windows = new ConcurrentHashMap<>();

    public boolean allow(String key) {
        long now = System.currentTimeMillis();
        AttemptWindow window = windows.compute(key, (ignored, current) -> {
            if (current == null || now - current.startedAt() >= WINDOW_MILLIS) {
                return new AttemptWindow(now, 1);
            }
            return new AttemptWindow(current.startedAt(), current.attempts() + 1);
        });
        return window.attempts() <= MAX_ATTEMPTS;
    }

    public void clear(String key) {
        windows.remove(key);
    }

    // ponytail: process-local limiter; replace with Redis when API instances are scaled horizontally.
    private record AttemptWindow(long startedAt, int attempts) {
    }
}
