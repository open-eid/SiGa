package ee.openeid.siga.session.redis;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Test fixture builders for Redis-backed session-storage tests. Centralizes the Testcontainers
 * configuration and the connection-factory / template wiring so individual test classes don't
 * each reimplement the same boilerplate.
 */
public final class RedisTestSupport {

    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("valkey/valkey:7.2.6-alpine");
    private static final int REDIS_PORT = 6379;

    private RedisTestSupport() {
    }

    public static GenericContainer<?> newRedisContainer() {
        return new GenericContainer<>(REDIS_IMAGE)
                .withExposedPorts(REDIS_PORT)
                .withCommand("redis-server", "--notify-keyspace-events", "Ex");
    }

    public static LettuceConnectionFactory connectionFactory(GenericContainer<?> container) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                container.getHost(), container.getMappedPort(REDIS_PORT));
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        return factory;
    }

    public static RedisTemplate<String, Object> sessionTemplate(LettuceConnectionFactory factory) {
        RedisSerializer<Object> jdkSerializer = RedisSerializer.java();
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(RedisSerializer.string());
        template.setValueSerializer(jdkSerializer);
        template.setHashKeySerializer(RedisSerializer.string());
        template.setHashValueSerializer(jdkSerializer);
        template.afterPropertiesSet();
        return template;
    }

    public static StringRedisTemplate stringTemplate(LettuceConnectionFactory factory) {
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        return template;
    }

    public static void flushAll(LettuceConnectionFactory factory) {
        factory.getConnection().serverCommands().flushAll();
    }
}
