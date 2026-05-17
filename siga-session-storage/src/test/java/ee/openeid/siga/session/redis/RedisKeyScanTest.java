package ee.openeid.siga.session.redis;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisClusterNode;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link RedisKeyScan}'s connection-specific scan paths.
 *
 * <p>The container-backed tests verify standalone {@code SCAN} behavior, while the Mockito-based
 * cluster test verifies fan-out over master nodes only. {@code RedisClusterSessionStorageTest}
 * keeps the end-to-end cluster behavior covered through {@code RedisSessionStorage}.
 */
@Tag("docker")
@Testcontainers
class RedisKeyScanTest {

    @Container
    private static final GenericContainer<?> REDIS = RedisTestSupport.newRedisContainer();

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate template;

    @BeforeAll
    static void initClients() {
        factory = RedisTestSupport.connectionFactory(REDIS);
        template = RedisTestSupport.stringTemplate(factory);
    }

    @AfterAll
    static void closeClients() {
        if (factory != null) factory.destroy();
    }

    @BeforeEach
    void cleanState() {
        RedisTestSupport.flushAll(factory);
    }

    @Test
    void shouldVisitOnlyMatchingKeys_OnStandaloneConnection() {
        // Seed five matching keys and two non-matching ones.
        for (int i = 0; i < 5; i++) {
            template.opsForValue().set(RedisSessionKeys.session("v1_svc_scan_" + i), "x");
        }
        template.opsForValue().set("siga:lock:other", "x");
        template.opsForValue().set("unrelated:key", "x");

        Set<String> visited = scanWithPattern(RedisSessionKeys.SESSION_SCAN_PATTERN);

        assertEquals(5, visited.size(), "Standalone scan must visit only matching keys, exactly once each");
        for (int i = 0; i < 5; i++) {
            assertTrue(visited.contains(RedisSessionKeys.session("v1_svc_scan_" + i)));
        }
        assertTrue(visited.stream().noneMatch(k -> k.equals("siga:lock:other")
                || k.equals("unrelated:key")));
    }

    @Test
    void shouldEmitNothing_WhenNoKeysMatch() {
        template.opsForValue().set("unrelated:1", "x");
        template.opsForValue().set("unrelated:2", "x");

        Set<String> visited = scanWithPattern(RedisSessionKeys.SESSION_SCAN_PATTERN);

        assertEquals(0, visited.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSkipReplicas_WhenFanningOutOnCluster() {
        // RedisKeyScan.forEachKey skips replica nodes via `if (!node.isMaster()) continue;`.
        // Without the filter, replicas would be scanned too and return the same keys as their
        // master. RedisClusterSessionStorageTest.shouldSumAcrossMasters_WhenSizeCalledOnCluster
        // observes the end-to-end count; this test pins the node-selection invariant directly.
        RedisClusterConnection clusterConnection = Mockito.mock(RedisClusterConnection.class);

        RedisClusterNode master1 = Mockito.mock(RedisClusterNode.class);
        RedisClusterNode replica1 = Mockito.mock(RedisClusterNode.class);
        RedisClusterNode master2 = Mockito.mock(RedisClusterNode.class);
        RedisClusterNode replica2 = Mockito.mock(RedisClusterNode.class);
        Mockito.when(master1.isMaster()).thenReturn(true);
        Mockito.when(master2.isMaster()).thenReturn(true);
        Mockito.when(replica1.isMaster()).thenReturn(false);
        Mockito.when(replica2.isMaster()).thenReturn(false);
        Mockito.when(clusterConnection.clusterGetNodes())
                .thenReturn(List.of(master1, replica1, master2, replica2));

        Cursor<byte[]> emptyCursor = Mockito.mock(Cursor.class);
        Mockito.when(emptyCursor.hasNext()).thenReturn(false);
        Mockito.when(clusterConnection.scan(Mockito.any(RedisClusterNode.class), Mockito.any(ScanOptions.class)))
                .thenReturn(emptyCursor);

        ScanOptions options = ScanOptions.scanOptions().match(RedisSessionKeys.SESSION_SCAN_PATTERN).build();
        RedisKeyScan.forEachKey(clusterConnection, options, b -> {
        });

        Mockito.verify(clusterConnection).scan(master1, options);
        Mockito.verify(clusterConnection).scan(master2, options);
        Mockito.verify(clusterConnection, Mockito.never()).scan(replica1, options);
        Mockito.verify(clusterConnection, Mockito.never()).scan(replica2, options);
        // Cursor cleanup via try-with-resources: two scans, two cursor closes.
        Mockito.verify(emptyCursor, Mockito.times(2)).close();
    }

    private static Set<String> scanWithPattern(String pattern) {
        Set<String> visited = new HashSet<>();
        template.execute((RedisCallback<Void>) connection -> {
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100L).build();
            RedisKeyScan.forEachKey(connection, options,
                    bytes -> visited.add(new String(bytes, StandardCharsets.UTF_8)));
            return null;
        });
        return visited;
    }
}
