package ee.openeid.siga.session.redis;

import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisClusterNode;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;

import java.util.function.Consumer;

/**
 * Scans every key matched by a {@link ScanOptions} pattern across both standalone and cluster
 * connections. A standalone {@code SCAN} only walks the connection's bound shard, so on Redis
 * Cluster the iteration must fan out across every master node — otherwise a cluster scan silently
 * reports one shard's worth of keys.
 */
final class RedisKeyScan {

    private RedisKeyScan() {
    }

    static void forEachKey(RedisConnection connection, ScanOptions options, Consumer<byte[]> consumer) {
        if (connection instanceof RedisClusterConnection cluster) {
            for (RedisClusterNode node : cluster.clusterGetNodes()) {
                if (!node.isMaster()) {
                    continue;
                }
                try (Cursor<byte[]> cursor = cluster.scan(node, options)) {
                    while (cursor.hasNext()) {
                        consumer.accept(cursor.next());
                    }
                }
            }
        } else {
            try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                while (cursor.hasNext()) {
                    consumer.accept(cursor.next());
                }
            }
        }
    }
}
