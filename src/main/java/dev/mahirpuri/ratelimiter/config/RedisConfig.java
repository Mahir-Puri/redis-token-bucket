package dev.mahirpuri.ratelimiter.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Redis wiring. Spring Boot auto-configures the Lettuce connection factory
 * from spring.data.redis.* properties; this class only adds the template and
 * the pre-loaded Lua script bean.
 *
 * The script is registered once at startup; Spring Data sends EVALSHA on each
 * call and falls back to EVAL if the script cache was flushed, so the script
 * body is not re-shipped on every request.
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public DefaultRedisScript<List> tokenBucketScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/token_bucket.lua"));
        script.setResultType(List.class);
        return script;
    }
}
