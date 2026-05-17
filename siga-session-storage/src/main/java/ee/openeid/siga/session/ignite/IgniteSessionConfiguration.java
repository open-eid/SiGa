package ee.openeid.siga.session.ignite;

import ee.openeid.siga.session.configuration.IgniteSessionProperties;
import ee.openeid.siga.session.configuration.SessionStorageProperties;
import ee.openeid.siga.session.spi.SessionLockRegistry;
import ee.openeid.siga.session.spi.SessionStatusScanner;
import ee.openeid.siga.session.spi.SessionStorage;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;

import static org.apache.ignite.IgniteSystemProperties.IGNITE_NO_SHUTDOWN_HOOK;

/**
 * Wires every Ignite-backed session-storage bean in a single place, gated at the class level so
 * the property check and classpath guard live in one location instead of being duplicated on each
 * component. Activates only when {@code siga.session-storage.type=ignite} is set explicitly —
 * Redis is the implicit default.
 */
@Configuration
@ConditionalOnProperty(prefix = "siga.session-storage", name = "type", havingValue = "ignite")
@EnableConfigurationProperties({SessionStorageProperties.class, IgniteSessionProperties.class})
public class IgniteSessionConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public Ignite ignite(IgniteSessionProperties properties) {
        // Graceful shutdown is controlled by SessionStatusService.
        System.setProperty(IGNITE_NO_SHUTDOWN_HOOK, "true");
        Ignition.setClientMode(true);
        try (InputStream in = properties.configurationLocation().getInputStream()) {
            return Ignition.start(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load Ignite configuration", e);
        }
    }

    @Bean
    public SessionStorage sessionStorage(Ignite ignite) {
        return new IgniteSessionStorage(ignite);
    }

    @Bean
    public SessionLockRegistry sessionLockRegistry(Ignite ignite) {
        return new IgniteSessionLockRegistry(ignite);
    }

    @Bean
    public SessionStatusScanner sessionStatusScanner(Ignite ignite) {
        return new IgniteSessionStatusScanner(ignite);
    }

    @Bean
    public IgniteSessionExpiryNotifier igniteSessionExpiryNotifier(Ignite ignite,
                                                                   ApplicationEventPublisher eventPublisher) {
        return new IgniteSessionExpiryNotifier(ignite, eventPublisher);
    }
}
