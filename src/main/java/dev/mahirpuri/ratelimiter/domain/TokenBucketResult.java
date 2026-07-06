package dev.mahirpuri.ratelimiter.domain;

/**
 * Outcome of one atomic check-and-decrement against a bucket.
 *
 * @param allowed         whether the request was granted a token
 * @param remainingTokens tokens left in the bucket after this call (fractional,
 *                        because refill is continuous)
 * @param retryAfterMs    if denied, how long until enough tokens will have
 *                        accumulated; 0 when allowed
 */
public record TokenBucketResult(boolean allowed, double remainingTokens, long retryAfterMs) {
}
