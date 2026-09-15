package com.example.TextileManagement.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AuthRateLimiter {
    private final JdbcTemplate jdbcTemplate;
    private final int maxFailures;
    private final Duration window;
    private final boolean postgres;

    public AuthRateLimiter(JdbcTemplate jdbcTemplate,
            @Value("${app.auth.rate-limit.max-failures:10}") int maxFailures,
            @Value("${app.auth.rate-limit.window-seconds:900}") long windowSeconds,
            @Value("${spring.datasource.url:}") String datasourceUrl) {
        this.jdbcTemplate = jdbcTemplate;
        this.maxFailures = Math.max(1, maxFailures);
        this.window = Duration.ofSeconds(Math.max(1, windowSeconds));
        this.postgres = !datasourceUrl.toLowerCase(java.util.Locale.ROOT).startsWith("jdbc:h2:");
    }

    public boolean isBlocked(String action, String account, String clientIp) {
        LocalDateTime cutoff = now().minus(window);
        return isBucketBlocked(bucketKey(action, "account", account), cutoff)
                || isBucketBlocked(bucketKey(action, "ip", clientIp), cutoff);
    }

    @Transactional
    public void recordFailure(String action, String account, String clientIp) {
        LocalDateTime current = now();
        LocalDateTime cutoff = current.minus(window);
        jdbcTemplate.update("DELETE FROM auth_throttle_buckets WHERE updated_at < ?", Timestamp.valueOf(cutoff));
        upsert(bucketKey(action, "account", account), current, cutoff);
        upsert(bucketKey(action, "ip", clientIp), current, cutoff);
    }

    @Transactional
    public void clearAccount(String action, String account) {
        jdbcTemplate.update("DELETE FROM auth_throttle_buckets WHERE bucket_key = ?",
                bucketKey(action, "account", account));
    }

    private boolean isBucketBlocked(String key, LocalDateTime cutoff) {
        return jdbcTemplate.query("SELECT failure_count, window_started_at FROM auth_throttle_buckets WHERE bucket_key = ?",
                result -> result.next()
                        && result.getInt("failure_count") >= maxFailures
                        && result.getTimestamp("window_started_at").toLocalDateTime().isAfter(cutoff),
                key);
    }

    private void upsert(String key, LocalDateTime current, LocalDateTime cutoff) {
        if (!postgres) {
            int updated = jdbcTemplate.update("""
                    UPDATE auth_throttle_buckets
                    SET failure_count = CASE WHEN window_started_at <= ? THEN 1 ELSE failure_count + 1 END,
                        window_started_at = CASE WHEN window_started_at <= ? THEN ? ELSE window_started_at END,
                        updated_at = ?
                    WHERE bucket_key = ?
                    """,
                    Timestamp.valueOf(cutoff), Timestamp.valueOf(cutoff), Timestamp.valueOf(current),
                    Timestamp.valueOf(current), key);
            if (updated == 0) {
                jdbcTemplate.update("MERGE INTO auth_throttle_buckets (bucket_key, window_started_at, failure_count, updated_at) KEY(bucket_key) VALUES (?, ?, 1, ?)",
                        key, Timestamp.valueOf(current), Timestamp.valueOf(current));
            }
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO auth_throttle_buckets (bucket_key, window_started_at, failure_count, updated_at)
                VALUES (?, ?, 1, ?)
                ON CONFLICT (bucket_key) DO UPDATE SET
                    failure_count = CASE
                        WHEN auth_throttle_buckets.window_started_at <= ? THEN 1
                        ELSE auth_throttle_buckets.failure_count + 1
                    END,
                    window_started_at = CASE
                        WHEN auth_throttle_buckets.window_started_at <= ? THEN EXCLUDED.window_started_at
                        ELSE auth_throttle_buckets.window_started_at
                    END,
                    updated_at = EXCLUDED.updated_at
                """,
                key, Timestamp.valueOf(current), Timestamp.valueOf(current),
                Timestamp.valueOf(cutoff), Timestamp.valueOf(cutoff));
    }

    private String bucketKey(String action, String dimension, String value) {
        return action + ":" + dimension + ":" + sha256(value == null ? "" : value);
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to create authentication throttle key", exception);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
