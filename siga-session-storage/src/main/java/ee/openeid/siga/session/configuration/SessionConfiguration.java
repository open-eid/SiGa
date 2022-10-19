package ee.openeid.siga.session.configuration;

import ee.openeid.siga.auth.repository.ConnectionRepository;
import ee.openeid.siga.session.CacheName;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.events.CacheEvent;
import org.apache.ignite.events.EventType;
import org.apache.ignite.internal.binary.builder.BinaryObjectBuilderImpl;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import java.util.UUID;

import static org.apache.ignite.IgniteSystemProperties.IGNITE_NO_SHUTDOWN_HOOK;

@Slf4j
@Profile("!test")
@SpringBootConfiguration
@EnableConfigurationProperties({SessionConfigurationProperties.class})
@RequiredArgsConstructor
public class SessionConfiguration {
    private final SessionConfigurationProperties sessionConfigurationProperties;
    private final ConnectionRepository connectionRepository;

    @Bean(destroyMethod = "close")
    public Ignite ignite() {
        System.setProperty(IGNITE_NO_SHUTDOWN_HOOK, "true"); // Graceful shutdown is controlled by SessionStatusService
        Ignition.setClientMode(true);
        Ignite ignite = Ignition.start(sessionConfigurationProperties.getConfigurationLocation());

        ignite.events(ignite.cluster().forCacheNodes(CacheName.CONTAINER_SESSION.name())).remoteListen((UUID uuid, CacheEvent event) -> {
            log.info(String.format("CACHE_OBJECT_EXPIRED event received: cacheName=%s, key=%s", event.cacheName(), event.key().toString()));
            if (CacheName.CONTAINER_SESSION.name().equals(event.cacheName())) {
                removeContainerConnectionData((BinaryObject) event.oldValue());
            }
            return true;
        }, null, EventType.EVT_CACHE_OBJECT_EXPIRED);

        return ignite;
    }

    private void removeContainerConnectionData(BinaryObject sessionObject) {
        String containerId = BinaryObjectBuilderImpl.wrap(sessionObject).getField("sessionId");
        if (containerId != null) {
            int count = connectionRepository.deleteByContainerId(containerId);
            log.debug("Deleted " + count + " connection(s) by container id " + containerId);
        }
    }
}
