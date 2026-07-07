package dev.mahirpuri.ratelimiter.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.mahirpuri.ratelimiter.domain.BucketConfig;
import dev.mahirpuri.ratelimiter.domain.TokenBucketResult;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

/**
 * Data access layer. Two kinds of keys live in Redis:
 *
 * rate_limit:{clientId}:{endpoint} hash: tokens, lastRefillTimestamp,
 * capacity, refillRatePerSecond
 * rate_limit:config:{clientId} hash: capacity, refillRatePerSecond
 *
 * Bucket state is only ever touched through the Lua script so that
 * read-refill-decrement is a single atomic operation, no matter how many
 * service instances share this Redis.
 */
@Repository
public class RedisTokenBucketRepository {

    private static final String BUCKET_KEY_FORMAT = "rate_limit:%s:%s";
    private static final String CONFIG_KEY_FORMAT = "rate_limit:config:%s";

    private static final String FIELD_CAPACITY = "capacity";
    private static final String FIELD_REFILL_RATE = "refillRatePerSecond";

    private final StringRedisTemplate redisTemplate;
    @SuppressWarnings("rawtypes")
    private final DefaultRedisScript<List> tokenBucketScript;

    @SuppressWarnings("rawtypes")
    public RedisTokenBucketRepository(StringRedisTemplate redisTemplate,
            DefaultRedisScript<List> tokenBucketScript) {
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = tokenBucketScript;
    }

    /**
     * Atomically refill the bucket for elapsed time and try to take one token.
     */
    public TokenBucketResult consume(String clientId, String endpoint, BucketConfig config, long ttlMillis) {
        String key = bucketKey(clientId, endpoint);
        List<?> result = redisTemplate.execute(
                tokenBucketScript,
                List.of(key),
                String.valueOf(config.capacity()),
                String.valueOf(config.refillRatePerSecond()),
                "1",
                String.valueOf(ttlMillis));

        if (result == null || result.size() < 3) {
            throw new IllegalStateException("Unexpected response from token bucket script: " + result);
        }

        boolean allowed = ((Number) result.get(0)).longValue() == 1L;
        double remainingTokens = Double.parseDouble(String.valueOf(result.get(1)));
        long retryAfterMs = ((Number) result.get(2)).longValue();

        return new TokenBucketResult(allowed, remainingTokens, retryAfterMs);
    }

    public Optional<BucketConfig> findConfig(String clientId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(configKey(clientId));
        if (entries == null || entries.isEmpty()) {
            return Optional.empty();
        }
        long capacity = Long.parseLong(String.valueOf(entries.get(FIELD_CAPACITY)));
        double refillRate = Double.parseDouble(String.valueOf(entries.get(FIELD_REFILL_RATE)));
        return Optional.of(new BucketConfig(capacity, refillRate));
    }

    public void saveConfig(String clientId, BucketConfig config) {
        redisTemplate.opsForHash().putAll(configKey(clientId), Map.of(
                FIELD_CAPACITY, String.valueOf(config.capacity()),
                FIELD_REFILL_RATE, String.valueOf(config.refillRatePerSecond())));
    }

    static String bucketKey(String clientId, String endpoint) {
        return BUCKET_KEY_FORMAT.formatted(clientId, endpoint);
    }

    static String configKey(String clientId) {
        return CONFIG_KEY_FORMAT.formatted(clientId);
    }
}
