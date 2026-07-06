package dev.mahirpuri.ratelimiter.domain;

/**
 * Effective rate limit configuration for a client: either loaded from Redis
 * (custom) or built from application defaults.
 */
public record BucketConfig(long capacity, double refillRatePerSecond) {
}
