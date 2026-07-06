package dev.mahirpuri.ratelimiter.domain;

/**
 * Pure-Java reference implementation of the token bucket algorithm.
 *
 * At runtime the math runs inside Redis (see resources/lua/token_bucket.lua)
 * so that read-refill-decrement is atomic across service instances. This class
 * is the line-for-line Java equivalent of that script. It exists for two
 * reasons:
 *
 * 1. The refill and boundary logic can be unit tested exhaustively without a
 * Redis instance (see TokenBucketCalculatorTest).
 * 2. Integration tests use it as an oracle: results coming back from the real
 * Lua script are checked against what this implementation predicts.
 *
 * If the algorithm changes, change it here and in the Lua script together.
 */
public final class TokenBucketCalculator {

    private TokenBucketCalculator() {
    }

    /**
     * Refill a bucket for the elapsed time, then attempt to consume tokens.
     *
     * @param currentTokens       tokens in the bucket at the last refill
     * @param lastRefillTimestamp epoch millis of the last refill
     * @param nowMillis           epoch millis of this call
     * @param capacity            maximum tokens the bucket can hold
     * @param refillRatePerSecond tokens added per second (may be fractional)
     * @param requested           tokens this request wants (normally 1)
     */
    public static TokenBucketResult tryConsume(double currentTokens,
            long lastRefillTimestamp,
            long nowMillis,
            double capacity,
            double refillRatePerSecond,
            double requested) {
        double tokens = refill(currentTokens, lastRefillTimestamp, nowMillis, capacity, refillRatePerSecond);

        if (tokens >= requested) {
            return new TokenBucketResult(true, tokens - requested, 0);
        }

        long retryAfterMs = 0;
        if (refillRatePerSecond > 0) {
            retryAfterMs = (long) Math.ceil(((requested - tokens) / refillRatePerSecond) * 1000.0);
        }
        return new TokenBucketResult(false, tokens, retryAfterMs);
    }

    /**
     * Continuous refill: tokens accumulate proportionally to elapsed time,
     * capped at capacity. A negative time delta (clock skew between writers)
     * is treated as zero elapsed time rather than draining the bucket.
     */
    public static double refill(double currentTokens,
            long lastRefillTimestamp,
            long nowMillis,
            double capacity,
            double refillRatePerSecond) {
        long elapsedMs = Math.max(0, nowMillis - lastRefillTimestamp);
        double refilled = currentTokens + (elapsedMs / 1000.0) * refillRatePerSecond;
        return Math.min(capacity, refilled);
    }
}
