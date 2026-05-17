package ee.openeid.siga.session.redis;

import ee.openeid.siga.session.configuration.RedisSessionProperties;
import ee.openeid.siga.session.configuration.SessionStatusReprocessingProperties;
import ee.openeid.siga.session.spi.SessionLockRegistry;
import ee.openeid.siga.session.spi.SessionStatusScanner;
import ee.openeid.siga.session.spi.SessionStorage;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.ErrorHandler;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves {@link RedisSessionConfiguration} is required: it materialises every Redis bean — the
 * SPI implementations, the reprocessing due queue, the keyspace-event notifier, and the
 * cluster-safe {@link RedisLockRegistry} — in a single conditional block. Without this
 * configuration class, the default-Redis variant of the
 * session-storage module wires nothing.
 *
 * <p>The lock-registry key must be {@code {lock}}-hashtagged so {@code PUB_SUB_LOCK} Lua scripts
 * routing across a Valkey 7.2+ cluster don't {@code CROSSSLOT}. We assert that via the
 * {@code LOCK_REGISTRY_KEY} constant exposed for the cluster test suite — the same constant the
 * production {@code @Bean} uses.
 */
@Tag("docker")
@Testcontainers
class RedisSessionConfigurationTest {

    @Container
    private static final GenericContainer<?> REDIS = RedisTestSupport.newRedisContainer();

    private ApplicationContextRunner contextRunner() {
        // Each run() creates and destroys its own RedisConnectionFactory — a shared factory would
        // be killed by the first context's shutdown and unusable in subsequent contextRunner()
        // invocations.
        return new ApplicationContextRunner()
                .withUserConfiguration(TestFactoryConfig.class, RedisSessionConfiguration.class)
                .withPropertyValues("siga.session-storage.application-cache-version=v1");
    }

    @Configuration
    static class TestFactoryConfig {
        @Bean(destroyMethod = "destroy")
        public RedisConnectionFactory redisConnectionFactory() {
            return RedisTestSupport.connectionFactory(REDIS);
        }

        @Bean
        public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
            return RedisTestSupport.stringTemplate((LettuceConnectionFactory) factory);
        }
    }

    @Test
    void shouldWireAllRedisBeans_WhenSessionStorageTypeIsRedis() {
        contextRunner()
                .withPropertyValues("siga.session-storage.type=redis")
                .run(context -> {
                    assertThat(context).hasSingleBean(SessionStorage.class);
                    assertThat(context.getBean(SessionStorage.class))
                            .isInstanceOf(RedisSessionStorage.class);
                    assertThat(context).hasSingleBean(SessionLockRegistry.class);
                    assertThat(context).hasSingleBean(SessionStatusScanner.class);
                    assertThat(context).hasSingleBean(SessionStatusReprocessingProperties.class);
                    assertThat(context.getBean(SessionStatusScanner.class))
                            .isInstanceOf(RedisSessionStatusScanner.class);
                    assertThat(context).hasSingleBean(RedisSessionEventListener.class);
                    assertThat(context).hasSingleBean(RedisSessionExpiryNotifier.class);
                    assertThat(context).hasSingleBean(RedisLockRegistry.class);
                });
    }

    @Test
    void shouldWireAllRedisBeans_WhenSessionStorageTypeIsUnset() {
        // matchIfMissing=true is the Redis-as-default contract. Pin it.
        contextRunner()
                .withSystemProperties("siga.session-storage.type=")
                .run(context -> {
                    assertThat(context).hasSingleBean(SessionStorage.class);
                    assertThat(context.getBean(SessionStorage.class))
                            .isInstanceOf(RedisSessionStorage.class);
                });
    }

    @Test
    void shouldNotWireAnyRedisBeans_WhenSessionStorageTypeIsIgnite() {
        contextRunner()
                .withPropertyValues("siga.session-storage.type=ignite")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RedisSessionStorage.class);
                    assertThat(context).doesNotHaveBean(RedisSessionStatusScanner.class);
                    assertThat(context).doesNotHaveBean(RedisSessionEventListener.class);
                    assertThat(context).doesNotHaveBean(RedisSessionExpiryNotifier.class);
                    assertThat(context).doesNotHaveBean(RedisLockRegistry.class);
                });
    }

    @Test
    void shouldUseClusterSafeRegistryKey_WithLockHashtag() {
        // The {lock} hashtag in LOCK_REGISTRY_KEY forces all lock keys to a single Valkey slot so
        // PUB_SUB_LOCK Lua scripts don't CROSSSLOT. Pin both the constant and that the wired
        // RedisLockRegistry actually uses it.
        assertTrue(RedisSessionConfiguration.LOCK_REGISTRY_KEY.contains("{lock}"),
                "Lock registry key must contain a hashtag — see RedisSessionConfiguration.LOCK_REGISTRY_KEY");

        contextRunner()
                .withPropertyValues("siga.session-storage.type=redis")
                .run(context -> {
                    RedisLockRegistry registry = context.getBean(RedisLockRegistry.class);
                    String registryKey = readRegistryKeyField(registry);
                    assertNotNull(registryKey);
                    assertTrue(registryKey.contains("{lock}"),
                            "RedisLockRegistry must be wired with the {lock}-hashtagged prefix; "
                                    + "actual prefix: " + registryKey);
                });
    }

    @Test
    void shouldExposeRedisLockTypeAsPubSubLock() {
        // PUB_SUB_LOCK is the Lua-script variant whose unlock script triggered the CROSSSLOT issue.
        // If a future refactor changes the lock type, the {lock} hashtag pin becomes moot — so this
        // test fences the two together.
        contextRunner()
                .withPropertyValues("siga.session-storage.type=redis")
                .run(context -> {
                    RedisLockRegistry registry = context.getBean(RedisLockRegistry.class);
                    String lockType = readField(registry, "redisLockType").toString();
                    assertTrue(lockType.endsWith("PUB_SUB_LOCK"),
                            "redisLockType must be PUB_SUB_LOCK; actual: " + lockType);
                });
    }

    @Test
    void shouldInstallErrorHandler_OnRedisLockRenewalTaskScheduler() {
        // RedisSessionConfiguration.redisLockRenewalTaskScheduler() wires a setErrorHandler(...)
        // explicitly so a transient exception in the renewal Lua call doesn't propagate to
        // ScheduledExecutorService and silently cancel future runs of that task. The existing
        // shouldKeepLockExclusive_WhenHeldLongerThanInitialTtl proves the happy path but never
        // forces a renewal failure mid-run, so dropping setErrorHandler(...) would still pass that
        // test. Pin the ErrorHandler presence directly.
        TaskScheduler scheduler = new RedisSessionConfiguration().redisLockRenewalTaskScheduler(RedisSessionProperties.withDefaults());
        assertThat(scheduler).isInstanceOf(ThreadPoolTaskScheduler.class);
        ErrorHandler errorHandler = (ErrorHandler) readField(scheduler, "errorHandler");
        assertNotNull(errorHandler,
                "redisLockRenewalTaskScheduler must have an ErrorHandler — without it a renewal blip "
                        + "cancels future runs and the lock holder loses its key while still mid-work");
    }

    @Test
    void shouldKeepSchedulingTasks_AfterErrorHandlerSwallowsAnException() throws Exception {
        // Behavioral counterpart to shouldInstallErrorHandler_OnRedisLockRenewalTaskScheduler: an
        // ErrorHandler is wired but only its presence is observable via reflection. This test
        // schedules a recurring Runnable that throws on the first invocation; if the production
        // ErrorHandler does its job, subsequent invocations still fire and the counter reaches at
        // least 3 within the await window. Without the ErrorHandler, ScheduledExecutorService
        // cancels the task after the first throw and the counter never gets past 1.
        ThreadPoolTaskScheduler scheduler =
                (ThreadPoolTaskScheduler) new RedisSessionConfiguration().redisLockRenewalTaskScheduler(RedisSessionProperties.withDefaults());
        scheduler.initialize();
        try {
            AtomicInteger invocations = new AtomicInteger();
            scheduler.scheduleAtFixedRate(() -> {
                int n = invocations.incrementAndGet();
                if (n == 1) {
                    throw new RuntimeException("simulated transient renewal failure");
                }
            }, Instant.now(), Duration.ofMillis(50));

            Awaitility.await()
                    .atMost(5, TimeUnit.SECONDS)
                    .pollInterval(50, TimeUnit.MILLISECONDS)
                    .until(() -> invocations.get() >= 3);
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void shouldWireStringKeyAndJdkValueSerializers_OnSigaSessionRedisTemplate() {
        // RedisSessionConfiguration.sigaSessionRedisTemplate() explicitly uses RedisSerializer.string()
        // for keys and RedisSerializer.java() (JDK) for stored session values. The serializer choice
        // is the session-payload schema contract — switching to a different codec would invalidate
        // every session in Redis after a deploy. Pin the serializer types directly so a refactor
        // that swaps to JSON / protobuf surfaces here.
        contextRunner()
                .withPropertyValues("siga.session-storage.type=redis")
                .run(context -> {
                    @SuppressWarnings("unchecked")
                    RedisTemplate<String, Object> template = context.getBean(
                            "sigaSessionRedisTemplate", RedisTemplate.class);
                    assertThat(template.getKeySerializer())
                            .as("Key serializer must be a StringRedisSerializer")
                            .isInstanceOf(StringRedisSerializer.class);
                    assertThat(template.getHashKeySerializer())
                            .as("Hash key serializer must be a StringRedisSerializer")
                            .isInstanceOf(StringRedisSerializer.class);
                    assertThat(template.getValueSerializer())
                            .as("Value serializer must be a JdkSerializationRedisSerializer — Session "
                                    + "payloads are complex POJOs and switching codecs invalidates "
                                    + "every persisted session")
                            .isInstanceOf(JdkSerializationRedisSerializer.class);
                    assertThat(template.getHashValueSerializer())
                            .as("Hash value serializer must be a JdkSerializationRedisSerializer")
                            .isInstanceOf(JdkSerializationRedisSerializer.class);
                });
    }

    @Test
    void shouldDelegateObtainToUnderlyingRedisLockRegistry_OnSessionLockRegistryBean() {
        // RedisSessionConfiguration#sessionLockRegistry is a thin lambda alias —
        // `redisLockRegistry::obtain` — so RedisLockRegistry's DisposableBean lifecycle still fires
        // on shutdown without a wrapper class. RedisLockRegistry.obtain(key) caches Locks in a
        // LinkedHashMap via computeIfAbsent, so two calls with the same key on the same registry
        // return the same Lock instance. If a refactor returns a wrapper that re-obtains under the
        // hood (or wires sessionLockRegistry over a *second* RedisLockRegistry), the two references
        // diverge.
        contextRunner()
                .withPropertyValues("siga.session-storage.type=redis")
                .run(context -> {
                    SessionLockRegistry sessionLockRegistry = context.getBean(SessionLockRegistry.class);
                    RedisLockRegistry redisLockRegistry = context.getBean(RedisLockRegistry.class);

                    Lock viaSession = sessionLockRegistry.obtain("v1_svc_delegation");
                    Lock viaRedis = redisLockRegistry.obtain("v1_svc_delegation");

                    assertSame(viaRedis, viaSession,
                            "SessionLockRegistry bean must delegate obtain() to the same "
                                    + "RedisLockRegistry — different instances would mean a wrapper "
                                    + "is in the way, breaking the DisposableBean shutdown path");
                });
    }

    @Test
    void shouldPropagateLockTtl_FromPropertiesToRedisLockRegistry() {
        // RedisSessionConfiguration#redisLockRegistry constructs the RedisLockRegistry with
        // properties.lockTtl().toMillis() — the crash-recovery lease for distributed locks. A
        // refactor accidentally passing properties.sessionTtl() (default 300s) instead of
        // lockTtl() (default 120s) would not be caught by any existing test: locks would still
        // work, just with the wrong TTL. Pin the value reaches the wired bean by booting with an
        // explicit lockTtl and reading RedisLockRegistry's `expireAfter` Duration field.
        contextRunner()
                .withPropertyValues(
                        "siga.session-storage.type=redis",
                        "siga.session-storage.redis.lockTtl=PT7S")
                .run(context -> {
                    RedisLockRegistry registry = context.getBean(RedisLockRegistry.class);
                    Duration expireAfter = (Duration) readField(registry, "expireAfter");
                    assertEquals(Duration.ofSeconds(7), expireAfter,
                            "RedisLockRegistry.expireAfter must equal the configured lockTtl");
                });
    }

    private static String readRegistryKeyField(RedisLockRegistry registry) {
        // RedisLockRegistry stores the prefix under a non-public field — name has been stable across
        // Spring Integration versions ("registryKey").
        return (String) readField(registry, "registryKey");
    }

    private static Object readField(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to read field " + name + " on " + target.getClass(), e);
        }
    }
}
