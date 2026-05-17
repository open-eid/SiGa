package ee.openeid.siga.session.redis;

import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.common.session.HashcodeContainerSession;
import ee.openeid.siga.common.session.ProcessingStatus;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.common.session.SessionStatus;
import ee.openeid.siga.common.session.SignatureSession;
import ee.openeid.siga.common.util.UUIDGenerator;
import ee.openeid.siga.session.SessionService;
import ee.openeid.siga.session.SessionServiceTest;
import ee.openeid.siga.session.configuration.SessionStatusReprocessingProperties;
import ee.openeid.siga.session.configuration.SessionStorageProperties;
import ee.openeid.siga.session.spi.SessionStorage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import static ee.openeid.siga.common.model.SigningType.SMART_ID;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@Tag("docker")
@Testcontainers
class RedisSessionServiceTest extends SessionServiceTest {

    @Container
    static final GenericContainer<?> REDIS = RedisTestSupport.newRedisContainer();

    private static LettuceConnectionFactory factory;
    private static RedisTemplate<String, Object> template;
    private static StringRedisTemplate stringTemplate;
    private static SessionStorage storage;

    @BeforeAll
    static void initClients() {
        factory = RedisTestSupport.connectionFactory(REDIS);
        template = RedisTestSupport.sessionTemplate(factory);
        stringTemplate = RedisTestSupport.stringTemplate(factory);
        storage = new RedisSessionStorage(template, Duration.ofMinutes(5));
    }

    @AfterAll
    static void closeClients() {
        if (factory != null) factory.destroy();
    }

    @Override
    protected SessionStorage storage() {
        return storage;
    }

    @Override
    protected void resetStorage() {
        RedisTestSupport.flushAll(factory);
    }

    /**
     * End-to-end wiring test: SessionService.update(...) → ApplicationEventPublisher →
     * RedisSessionEventListener.onSessionUpdated → siga:{reprocess}:* ZSET. The contract tests
     * inherited from SessionServiceTest only assert that publishEvent was called against a Mockito
     * mock; this test wires a real Spring event-publishing context with the actual listener so a
     * refactor that drops the @EventListener annotation or breaks the publish→listen wiring is
     * caught.
     */
    @Test
    void shouldRebuildStatusZset_WhenSessionServiceDrivesUpdateAndRemoveEnd2End() {
        RedisTestSupport.flushAll(factory);

        // Real Spring context with full @EventListener processing — registers the listener as a
        // bean definition before refresh() so the EventListenerMethodProcessor picks up its
        // annotations.
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(RedisSessionEventListener.class, () -> new RedisSessionEventListener(
                stringTemplate, SessionStatusReprocessingProperties.withDefaults()));
        context.refresh();

        try {
            SessionStorageProperties properties = new SessionStorageProperties("v1");
            SessionService service = new SessionService(storage, context, properties);

            Authentication authentication = Mockito.mock(Authentication.class);
            SecurityContext securityContext = Mockito.mock(SecurityContext.class);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            SecurityContextHolder.setContext(securityContext);
            when(authentication.getPrincipal()).thenReturn(SigaUserDetails.builder()
                    .clientName("c").serviceName("s").serviceUuid("svc").build());
            when(authentication.getName()).thenReturn("user");

            String containerId = UUIDGenerator.generateUUID();
            String sessionId = service.getSessionId(containerId);

            Instant aged = Instant.now().minus(Duration.ofMinutes(7));
            Session session = HashcodeContainerSession.builder()
                    .sessionId(sessionId)
                    .clientName("c").serviceName("s").serviceUuid("svc")
                    .dataFiles(Collections.emptyList())
                    .signatures(Collections.emptyList())
                    .build();
            session.setSignatureSessions(Map.of("sig-e2e", SignatureSession.builder()
                    .signingType(SMART_ID)
                    .sessionStatus(SessionStatus.builder()
                            .processingStatus(ProcessingStatus.PROCESSING)
                            .processingStatusTimestamp(aged)
                            .build())
                    .build()));

            service.update(session);

            String zsetKey = RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE;
            assertNotNull(stringTemplate.opsForZSet().score(zsetKey, sessionId),
                    "End-to-end: update() must publish SessionUpdatedEvent which the queue listener "
                            + "translates into a ZADD on the signature due queue");

            service.removeByContainerId(containerId);

            assertNull(stringTemplate.opsForZSet().score(zsetKey, sessionId),
                    "End-to-end: remove*() must publish SessionRemovedEvent which the queue listener "
                            + "translates into a ZREM from every due queue");
        } finally {
            context.close();
        }
    }
}
