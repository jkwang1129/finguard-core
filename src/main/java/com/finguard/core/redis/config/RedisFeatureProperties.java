package com.finguard.core.redis.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "finguard.redis")
public class RedisFeatureProperties {

    @Valid
    private final Statistics statistics = new Statistics();

    @Valid
    private final RateLimit rateLimit = new RateLimit();

    public Statistics getStatistics() {
        return statistics;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public static class Statistics {

        @NotNull
        private Duration cacheTtl = Duration.ofSeconds(60);

        public Duration getCacheTtl() {
            return cacheTtl;
        }

        public void setCacheTtl(Duration cacheTtl) {
            this.cacheTtl = cacheTtl;
        }

        @AssertTrue(message = "cache TTL must be between 1 second and 1 day")
        public boolean isCacheTtlValid() {
            return validWindow(cacheTtl);
        }
    }

    public static class RateLimit {

        @Valid
        private final Limit login = new Limit(5, Duration.ofMinutes(5));

        @Valid
        private final Limit upload = new Limit(10, Duration.ofMinutes(1));

        public Limit getLogin() {
            return login;
        }

        public Limit getUpload() {
            return upload;
        }
    }

    public static class Limit {

        @Min(1)
        @Max(10_000)
        private int limit;

        @NotNull
        private Duration window;

        public Limit() {
        }

        private Limit(int limit, Duration window) {
            this.limit = limit;
            this.window = window;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }

        @AssertTrue(message = "window must be between 1 second and 1 day")
        public boolean isWindowValid() {
            return validWindow(window);
        }
    }

    private static boolean validWindow(Duration duration) {
        if (duration == null
                || duration.isNegative()
                || duration.isZero()
                || duration.getNano() != 0) {
            return false;
        }
        long seconds = duration.getSeconds();
        return seconds >= 1 && seconds <= Duration.ofDays(1).getSeconds();
    }
}
