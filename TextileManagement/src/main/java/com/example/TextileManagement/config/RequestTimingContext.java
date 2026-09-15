package com.example.TextileManagement.config;

/** Request-local counters used by the timing filter and repository aspect. */
public final class RequestTimingContext {
    private static final ThreadLocal<Timing> CURRENT = new ThreadLocal<>();

    private RequestTimingContext() {
    }

    public static void start() {
        CURRENT.set(new Timing());
    }

    public static void recordDatabaseQuery(long elapsedNanos) {
        Timing timing = CURRENT.get();
        if (timing != null) {
            timing.databaseNanos += elapsedNanos;
            timing.databaseQueries++;
        }
    }

    public static Timing current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static final class Timing {
        private long databaseNanos;
        private int databaseQueries;

        public long databaseNanos() {
            return databaseNanos;
        }

        public int databaseQueries() {
            return databaseQueries;
        }
    }
}
