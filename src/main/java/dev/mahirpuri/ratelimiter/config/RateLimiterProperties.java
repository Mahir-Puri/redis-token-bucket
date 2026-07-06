package dev.mahirpuri.ratelimiter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Defaults applied to any client that has no explicit config stored in Redis.
 * Bound from the {@code rate-limiter} section of application.yml.
 *
 * @param defaultCapacity            bucket size for unconfigured clients
 * @param defaultRefillRatePerSecond refill rate for unconfigured clients
 * @param bucketTtlSeconds           idle time after which a bucket hash expires
 *                                   in Redis
 */
@ConfigurationProperties(prefix = "rate-limiter")
public record RateLimiterProperties(
        long defaultCapacity,
        double defaultRefillRatePerSecond,
        long bucketTtlSeconds) {
}
