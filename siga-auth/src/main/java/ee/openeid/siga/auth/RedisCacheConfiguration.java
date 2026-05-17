package ee.openeid.siga.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;

/**
 * AUTH_SERVICES cache backed by Redis when {@code siga.session-storage.type=redis}. Values are
 * serialized via Spring's {@code JdkSerializationRedisSerializer} — {@code SigaUserDetails}
 * implements {@link java.io.Serializable} (through Spring Security's {@code UserDetails}) and
 * carries an explicit serialVersionUID so the wire format is stable across deployments.
 *
 * <p>Note: {@code cacheDefaults} applies this serializer to every cache the bean manages; today
 * only {@code AUTH_SERVICES} exists. Adding a cache with a different value type requires switching
 * to per-cache configuration via {@code withInitialCacheConfigurations}.
 */
@Configuration
@EnableCaching
@ConditionalOnProperty(prefix = "siga.session-storage", name = "type", havingValue = "redis", matchIfMissing = true)
public class RedisCacheConfiguration {

    @Value("${siga.auth.cache.services-ttl:5m}")
    private Duration authServicesTtl;

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        org.springframework.data.redis.cache.RedisCacheConfiguration config = org.springframework.data.redis.cache.RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(authServicesTtl)
                .disableCachingNullValues()
                .serializeKeysWith(SerializationPair.fromSerializer(RedisSerializer.string()))
                .serializeValuesWith(SerializationPair.fromSerializer(RedisSerializer.java()));
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
}
