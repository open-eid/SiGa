package ee.openeid.siga.session.configuration;

import ee.openeid.siga.common.event.SigaEvent;
import ee.openeid.siga.common.event.SigaEventName;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.session.CacheName;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.Ignition;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.binary.BinaryObjectBuilder;
import org.apache.ignite.events.CacheEvent;
import org.apache.ignite.events.EventType;
import org.apache.ignite.internal.binary.BinaryObjectImpl;
import org.apache.ignite.internal.binary.builder.BinaryObjectBuilderImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import java.util.UUID;

import static java.time.Instant.now;
import static lombok.AccessLevel.PRIVATE;

@SpringBootConfiguration
@EnableConfigurationProperties({
        SessionConfigurationProperties.class
})
@Profile("!test")
@Slf4j
@FieldDefaults(level = PRIVATE)
public class SessionConfiguration {

    SessionConfigurationProperties sessionConfigurationProperties;

    @Bean(destroyMethod = "close")
    public Ignite ignite() throws IgniteException {
        Ignition.setClientMode(true);
        Ignite ignite = Ignition.start(sessionConfigurationProperties.getLocation());

        ignite.events(ignite.cluster().forCacheNodes(CacheName.CONTAINER.name())).remoteListen((UUID uuid, CacheEvent event) -> {
            BinaryObject sessionObject = (BinaryObject) event.oldValue();
            BinaryObjectBuilder bob = BinaryObjectBuilderImpl.wrap(sessionObject);

            SigaEvent sigaEvent = SigaEvent.builder()
                    .clientName(bob.getField("clientName"))
                    .serviceName(bob.getField("serviceName"))
                    .serviceUuid(bob.getField("serviceUuid"))
                    .eventType(SigaEvent.EventType.FINISH)
                    .eventName(SigaEventName.HC_DELETE_CONTAINER)
                    .timestamp(now().toEpochMilli())
                    .resultType(SigaEvent.EventResultType.SUCCESS)
                    .build();
            sigaEvent.addEventParameter("container_id", bob.getField("sessionId"));
            log.info(sigaEvent.toString());
            return true;
        }, null, EventType.EVT_CACHE_OBJECT_EXPIRED);

        return ignite;
    }

    @Autowired
    protected void setSessionConfigurationProperties(SessionConfigurationProperties sessionConfigurationProperties) {
        this.sessionConfigurationProperties = sessionConfigurationProperties;
    }
}
