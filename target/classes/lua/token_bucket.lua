-- token_bucket.lua
--
-- Atomic read -> refill -> decrement for a token bucket stored as a Redis hash.
-- Redis executes scripts single-threaded, so the whole sequence below is one
-- atomic operation: no other client can observe or mutate the bucket between
-- the read and the write. This is what eliminates the check-then-act race that
-- a naive GET/SET implementation would have under concurrency.
--
-- The bucket refills continuously: on every call we compute how much time has
-- passed since the last refill and add elapsedSeconds * refillRatePerSecond
-- tokens (capped at capacity). Tokens are stored as a floating point string so
-- fractional refill is not lost between calls.
--
-- We use Redis' own clock (TIME) rather than the app server clock so that
-- every service instance sharing this Redis sees a single consistent
-- timeline. Redis 7 replicates script effects (not the script itself), so a
-- non-deterministic command like TIME is safe here.
--
-- KEYS[1]  bucket hash key, e.g. rate_limit:{clientId}:{endpoint}
-- ARGV[1]  capacity                 (max tokens)
-- ARGV[2]  refill rate              (tokens per second, may be fractional)
-- ARGV[3]  tokens requested         (normally 1)
-- ARGV[4]  bucket TTL               (milliseconds; idle buckets expire)
--
-- Returns: { allowed (0|1), remainingTokens (string), retryAfterMs (integer) }

local capacity  = tonumber(ARGV[1])
local rate      = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])
local ttlMs     = tonumber(ARGV[4])

local time  = redis.call('TIME')
local nowMs = (tonumber(time[1]) * 1000) + math.floor(tonumber(time[2]) / 1000)

local state  = redis.call('HMGET', KEYS[1], 'tokens', 'lastRefillTimestamp')
local tokens = tonumber(state[1])
local last   = tonumber(state[2])

-- First sighting of this bucket: initialize to a full bucket. Doing the
-- existence check inside the script keeps initialization atomic with the
-- first decrement (a separate SET NX round trip would reintroduce a race
-- window between init and consume).
if tokens == nil or last == nil then
  tokens = capacity
  last   = nowMs
end

-- Continuous refill based on elapsed time. Guard against a negative delta
-- (possible if the hash was written under a different clock source).
local elapsedMs = nowMs - last
if elapsedMs < 0 then
  elapsedMs = 0
end
tokens = math.min(capacity, tokens + (elapsedMs / 1000) * rate)

local allowed = 0
local retryAfterMs = 0

if tokens >= requested then
  tokens = tokens - requested
  allowed = 1
elseif rate > 0 then
  -- Time until enough tokens have accumulated to serve this request.
  retryAfterMs = math.ceil(((requested - tokens) / rate) * 1000)
end

redis.call('HSET', KEYS[1],
  'tokens', tostring(tokens),
  'lastRefillTimestamp', tostring(nowMs),
  'capacity', tostring(capacity),
  'refillRatePerSecond', tostring(rate))
redis.call('PEXPIRE', KEYS[1], ttlMs)

return { allowed, tostring(tokens), retryAfterMs }
