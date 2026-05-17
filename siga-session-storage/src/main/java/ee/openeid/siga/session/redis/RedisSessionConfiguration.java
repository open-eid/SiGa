package ee.openeid.siga.session.redis;

import ee.openeid.siga.session.configuration.RedisClusterTopologyRefreshValidation;
import ee.openeid.siga.session.configuration.RedisSessionProperties;
import ee.openeid.siga.session.configuration.SessionStatusReprocessingProperties;
import ee.openeid.siga.session.configuration.SessionStorageProperties;
import ee.openeid.siga.session.spi.SessionLockRegistry;
import ee.openeid.siga.session.spi.SessionStatusScanner;
import ee.openeid.siga.session.spi.SessionStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Wires every Redis-backed session-storage bean in a single place, gated at the class level so the
 * property check and classpath guard live in one location instead of being duplicated on each
 * component.
 *
 * <p>The {@link SessionLockRegistry} is exposed as a thin lambda alias over Spring Integration's
 * final {@link RedisLockRegistry} bean — that lets {@code Lifecycle/DisposableBean} on the
 * underlying registry still fire on shutdown without a wrapper class.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "siga.session-storage", name = "type", havingValue = "redis", matchIfMissing = true)
@EnableConfigurationProperties({
        SessionStorageProperties.class,
        RedisSessionProperties.class,
        SessionStatusReprocessingProperties.class,
        RedisClusterTopologyRefreshValidation.class
})
@EnableScheduling
public class RedisSessionConfiguration {

    // The {lock} hashtag forces every lock key under this prefix to hash to a single Redis Cluster
    // slot. RedisLockRegistry's PUB_SUB_LOCK Lua scripts touch both the lock key and a derived
    // pub/sub channel name; without same-slot routing the unlock script fails with CROSSSLOT on
    // Valkey 7.2+ cluster (ElastiCache, MemoryDB). Single-slot concentration is acceptable here
    // because lock keys are tiny (one SET-with-EXPIRE per active session) and short-lived
    // (lockTtl defaults to 120s).
    // https://docs.spring.io/spring-integration/reference/redis.html#elasticache-valkey-cluster
    static final String LOCK_REGISTRY_KEY = "siga:lock:{lock}";

    @Bean
    @ConditionalOnMissingBean(name = "sigaSessionRedisTemplate")
    RedisTemplate<String, Object> sigaSessionRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        RedisSerializer<Object> jdkSerializer = RedisSerializer.java();
        template.setKeySerializer(RedisSerializer.string());
        template.setValueSerializer(jdkSerializer);
        template.setHashKeySerializer(RedisSerializer.string());
        template.setHashValueSerializer(jdkSerializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    SessionStorage sessionStorage(RedisTemplate<String, Object> sigaSessionRedisTemplate,
                                  RedisSessionProperties properties) {
        return new RedisSessionStorage(sigaSessionRedisTemplate, properties.sessionTtl());
    }

    @Bean
    TaskScheduler redisLockRenewalTaskScheduler(RedisSessionProperties properties) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.lockRenewalThreadPoolSize());
        scheduler.setAwaitTerminationSeconds(15);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setThreadNamePrefix("siga-redis-lock-renewal-");
        // Without an ErrorHandler, an exception during the renewal Lua call (e.g. a Redis blip)
        // propagates to the underlying ScheduledExecutorService, which silently suppresses
        // further runs of that task — renewal stops for that lock and the holder eventually
        // loses the key while still mid-work. Routing exceptions through this handler logs the
        // failure and keeps the schedule alive so renewal recovers on the next tick.
        scheduler.setErrorHandler(t ->
                log.warn("Redis lock renewal task failed; retrying on next tick: {}", t.getMessage(), t));
        return scheduler;
    }

    @Bean(destroyMethod = "destroy")
    RedisLockRegistry redisLockRegistry(RedisConnectionFactory connectionFactory,
                                        RedisSessionProperties properties,
                                        @Qualifier("redisLockRenewalTaskScheduler") TaskScheduler renewalTaskScheduler) {
        RedisLockRegistry registry = new RedisLockRegistry(connectionFactory, LOCK_REGISTRY_KEY,
                properties.lockTtl().toMillis());
        registry.setRedisLockType(RedisLockRegistry.RedisLockType.PUB_SUB_LOCK);
        // Renewal makes lockTtl the crash-recovery lease rather than the live-hold ceiling:
        // RedisLockRegistry extends the Redis key every lockTtl/3 while the owning JVM is alive
        // (atomic owner-checked PEXPIRE), so a long MID/SID poll cannot race itself out of the
        // lock. When the JVM dies the scheduler dies with it and the key expires naturally.
        //
        // This brings the Redis backend in line with IgniteSessionLockRegistry's failoverSafe
        // semaphore semantics — Ignite detects node death via cluster topology and never expires
        // locks held by live holders; Redis has no membership signal, so renewal is the closest
        // approximation that gives the same "live holder doesn't lose its lock" guarantee under
        // the cross-backend SessionLockRegistry contract.
        registry.setRenewalTaskScheduler(renewalTaskScheduler);
        return registry;
    }

    @Bean
    SessionLockRegistry sessionLockRegistry(RedisLockRegistry redisLockRegistry) {
        return redisLockRegistry::obtain;
    }

    @Bean
    SessionStatusScanner sessionStatusScanner(StringRedisTemplate stringRedisTemplate,
                                              SessionStorage sessionStorage,
                                              SessionStatusReprocessingProperties reprocessingProperties,
                                              RedisSessionProperties properties) {
        return new RedisSessionStatusScanner(stringRedisTemplate, sessionStorage, reprocessingProperties,
                properties.statusScanBatchSize());
    }

    @Bean
    RedisSessionEventListener redisSessionEventListener(StringRedisTemplate stringRedisTemplate,
                                                        SessionStatusReprocessingProperties reprocessingProperties) {
        return new RedisSessionEventListener(stringRedisTemplate, reprocessingProperties);
    }

    @Bean
    RedisSessionExpiryNotifier redisSessionExpiryNotifier(RedisConnectionFactory connectionFactory,
                                                          ApplicationEventPublisher eventPublisher,
                                                          RedisSessionProperties properties) {
        return new RedisSessionExpiryNotifier(connectionFactory, eventPublisher,
                properties.skipKeyspaceEventsVerification());
    }
}
