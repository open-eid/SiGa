package ee.openeid.siga.session.configuration;

import ee.openeid.siga.common.event.SigaEvent;
import ee.openeid.siga.common.event.SigaEventName;
import ee.openeid.siga.session.CacheName;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
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

    @Bean(destroyMethod = "close")
    public Ignite ignite() throws IgniteException {
        Ignition.setClientMode(true);
        Ignite ignite = Ignition.start(sessionConfigurationProperties.getConfigurationLocation());

        ignite.events(ignite.cluster().forCacheNodes(CacheName.CONTAINER.name())).remoteListen((UUID uuid, CacheEvent event) -> {
            BinaryObject sessionObject = (BinaryObject) event.oldValue();
            BinaryObjectBuilder bob = BinaryObjectBuilderImpl.wrap(sessionObject);
            SigaEvent sigaEvent = SigaEvent.builder()
                    .clientUuid(bob.getField("clientUuid"))
                    .serviceUuid(bob.getField("serviceUuid"))
                    .eventType(SigaEvent.EventType.FINISH)
                    .eventName(SigaEventName.HC_DELETE_CONTAINER)
                    .timestamp(now().toEpochMilli())
                    .resultType(SigaEvent.EventResultType.SUCCESS)
                    .build();
            sigaEvent.addEventParameter(CONTAINER_ID, bob.getField("sessionId"));
            log.info(getMarker("SIGA_EVENT"), sigaEvent.toString());
            return true;
        }, null, EventType.EVT_CACHE_OBJECT_EXPIRED);

        return ignite;
    }

    @Autowired
    protected void setSessionConfigurationProperties(SessionConfigurationProperties sessionConfigurationProperties) {
        this.sessionConfigurationProperties = sessionConfigurationProperties;
    }
}
