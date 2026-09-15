package com.example.TextileManagement.config;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.web.util.OnCommittedResponseWrapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTimingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestTimingFilter.class);
    private static final String REQUEST_ID = "X-Request-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = validRequestId(request.getHeader(REQUEST_ID)) ? request.getHeader(REQUEST_ID)
                : UUID.randomUUID().toString();
        long started = System.nanoTime();
        RequestTimingContext.start();
        response.setHeader(REQUEST_ID, requestId);
        TimingResponseWrapper timingResponse = new TimingResponseWrapper(response, started);
        try {
            filterChain.doFilter(request, timingResponse);
        } finally {
            long routeMillis = elapsedMillis(started);
            RequestTimingContext.Timing timing = RequestTimingContext.current();
            long databaseMillis = timing == null ? 0L : nanosToMillis(timing.databaseNanos());
            int databaseQueries = timing == null ? 0 : timing.databaseQueries();
            timingResponse.writeTimingHeader();
            log.info("event=request_timing request_id={} method={} route={} status={} route_ms={} db_ms={} db_queries={}",
                    requestId, request.getMethod(), routePattern(request), response.getStatus(), routeMillis,
                    databaseMillis, databaseQueries);
            RequestTimingContext.clear();
        }
    }

    private boolean validRequestId(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private long elapsedMillis(long started) {
        return nanosToMillis(System.nanoTime() - started);
    }

    private long nanosToMillis(long nanos) {
        return Math.max(0L, Math.round(nanos / 1_000_000.0));
    }

    private String routePattern(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern instanceof String value && !value.isBlank() ? value : "unmatched";
    }

    private final class TimingResponseWrapper extends OnCommittedResponseWrapper {
        private final long started;
        private boolean timingHeaderWritten;

        private TimingResponseWrapper(HttpServletResponse response, long started) {
            super(response);
            this.started = started;
        }

        @Override
        protected void onResponseCommitted() {
            writeTimingHeader();
        }

        private void writeTimingHeader() {
            if (timingHeaderWritten) {
                return;
            }
            RequestTimingContext.Timing timing = RequestTimingContext.current();
            long databaseMillis = timing == null ? 0L : nanosToMillis(timing.databaseNanos());
            setHeader("Server-Timing", "app;dur=" + elapsedMillis(started) + ", db;dur=" + databaseMillis);
            timingHeaderWritten = true;
        }
    }
}
