package ee.openeid.siga.session.configuration;

import ee.openeid.siga.auth.model.SigaConnection;
import ee.openeid.siga.auth.repository.ConnectionRepository;
import ee.openeid.siga.session.CacheName;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.events.CacheEvent;
import org.apache.ignite.events.EventType;
import org.apache.ignite.internal.binary.builder.BinaryObjectBuilderImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import java.util.Optional;
import java.util.UUID;

@SpringBootConfiguration
@EnableConfigurationProperties({SessionConfigurationProperties.class})
@Profile("!test")
@Slf4j
public class SessionConfiguration {

    private SessionConfigurationProperties sessionConfigurationProperties;
    private ConnectionRepository connectionRepository;

    @Bean(destroyMethod = "close")
    public Ignite ignite() {
        Ignition.setClientMode(true);
        Ignite ignite = Ignition.start(sessionConfigurationProperties.getConfigurationLocation());

        ignite.events(ignite.cluster().forCacheNodes(CacheName.CONTAINER.name())).remoteListen((UUID uuid, CacheEvent event) -> {
            log.info(String.format("CACHE_OBJECT_EXPIRED event received: cacheName=%s, key=%s", event.cacheName(), event.key().toString()));
            if (CacheName.CONTAINER.name().equals(event.cacheName())) {
                removeContainerConnectionData((BinaryObject) event.oldValue());
            }
            return true;
        }, null, EventType.EVT_CACHE_OBJECT_EXPIRED);

        return ignite;
    }

    private void removeContainerConnectionData(BinaryObject sessionObject) {
        String containerId = BinaryObjectBuilderImpl.wrap(sessionObject).getField("sessionId");
        if (containerId != null) {
            Optional<SigaConnection> connection = connectionRepository.findAllByContainerId(containerId);
            connection.ifPresent(sigaConnection -> connectionRepository.delete(sigaConnection));
        }
    }

    @Autowired
    protected void setSessionConfigurationProperties(SessionConfigurationProperties sessionConfigurationProperties) {
        this.sessionConfigurationProperties = sessionConfigurationProperties;
    }

    @Autowired
    public void setConnectionRepository(ConnectionRepository connectionRepository) {
        this.connectionRepository = connectionRepository;
    }
}
