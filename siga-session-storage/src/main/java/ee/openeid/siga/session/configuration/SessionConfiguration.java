package ee.openeid.siga.session.configuration;

import ee.openeid.siga.auth.model.SigaConnection;
import ee.openeid.siga.auth.repository.ConnectionRepository;
import ee.openeid.siga.common.event.SigaEvent;
import ee.openeid.siga.common.event.SigaEventName;
import ee.openeid.siga.session.CacheName;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.binary.BinaryObjectBuilder;
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

import static ee.openeid.siga.common.event.SigaEventName.EventParam.CONTAINER_ID;
import static java.time.Instant.now;
import static org.slf4j.MarkerFactory.getMarker;

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
            BinaryObject sessionObject = (BinaryObject) event.oldValue();
            BinaryObjectBuilder bob = BinaryObjectBuilderImpl.wrap(sessionObject);
            SigaEvent sigaEvent = SigaEvent.builder()
                    .clientUuid(bob.getField("clientUuid"))
                    .serviceUuid(bob.getField("serviceUuid"))
                    .eventType(SigaEvent.EventType.FINISH)
                    .eventName(chooseEventName(bob))
                    .timestamp(now().toEpochMilli())
                    .resultType(SigaEvent.EventResultType.SUCCESS)
                    .build();
            String containerId = bob.getField("sessionId");
            sigaEvent.addEventParameter(CONTAINER_ID, containerId);
            removeConnectionData(containerId);
            log.info(getMarker("SIGA_EVENT"), sigaEvent.toString());
            return true;
        }, null, EventType.EVT_CACHE_OBJECT_EXPIRED);

        return ignite;
    }

    private void removeConnectionData(String containerId) {
        Optional<SigaConnection> connection = connectionRepository.findAllByContainerId(containerId);
        connection.ifPresent(sigaConnection -> connectionRepository.delete(sigaConnection));
    }

    private SigaEventName chooseEventName(BinaryObjectBuilder bob) {
        if (bob.getField("containerName") == null) {
            return SigaEventName.HC_DELETE_CONTAINER;
        } else {
            return SigaEventName.DELETE_CONTAINER;
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
