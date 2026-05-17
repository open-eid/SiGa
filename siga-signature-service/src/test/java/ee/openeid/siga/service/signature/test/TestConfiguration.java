package ee.openeid.siga.service.signature.test;

import ee.openeid.siga.service.signature.client.SivaClient;
import ee.openeid.siga.session.configuration.SessionStorageProperties;
import ee.openeid.siga.session.ignite.IgniteSessionConfiguration;
import ee.openeid.siga.session.ignite.IgniteSessionExpiryNotifier;
import ee.openeid.siga.session.ignite.IgniteSessionLockRegistry;
import ee.openeid.siga.session.ignite.IgniteSessionStatusScanner;
import ee.openeid.siga.session.ignite.IgniteSessionStorage;
import ee.openeid.siga.session.spi.SessionLockRegistry;
import ee.openeid.siga.session.spi.SessionStatusScanner;
import ee.openeid.siga.session.spi.SessionStorage;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.Ignition;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

@Configuration
@EnableAutoConfiguration
@EnableConfigurationProperties(SessionStorageProperties.class)
@ComponentScan(basePackages = {"ee.openeid.siga.common", "ee.openeid.siga.session", "ee.openeid.siga.service.signature"},
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASPECTJ, pattern = "*..Siva*"),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = IgniteSessionConfiguration.class)
        })
public class TestConfiguration {

    @Bean
    SivaClient sivaClient() {
        return Mockito.mock(SivaClient.class);
    }

    private static final String INSTANCE_NAME = "siga-ignite";

    @Bean
    @ConditionalOnProperty(prefix = "siga.session-storage", name = "type", havingValue = "ignite")
    public Ignite ignite() throws IgniteException {
        System.setProperty("IGNITE_OVERRIDE_CONSISTENT_ID", "node00");
        return Ignition.allGrids().stream()
                .filter(grid -> INSTANCE_NAME.equals(grid.name()))
                .findFirst()
                .orElseGet(() -> Ignition.start("ignite-test-configuration.xml"));
    }

    @Bean
    @ConditionalOnProperty(prefix = "siga.session-storage", name = "type", havingValue = "ignite")
    public SessionStorage sessionStorage(Ignite ignite) {
        return new IgniteSessionStorage(ignite);
    }

    @Bean
    @ConditionalOnProperty(prefix = "siga.session-storage", name = "type", havingValue = "ignite")
    public SessionLockRegistry sessionLockRegistry(Ignite ignite) {
        return new IgniteSessionLockRegistry(ignite);
    }

    @Bean
    @ConditionalOnProperty(prefix = "siga.session-storage", name = "type", havingValue = "ignite")
    public SessionStatusScanner sessionStatusScanner(Ignite ignite) {
        return new IgniteSessionStatusScanner(ignite);
    }

    @Bean
    @ConditionalOnProperty(prefix = "siga.session-storage", name = "type", havingValue = "ignite")
    public IgniteSessionExpiryNotifier igniteSessionExpiryNotifier(Ignite ignite,
                                                                   ApplicationEventPublisher eventPublisher) {
        return new IgniteSessionExpiryNotifier(ignite, eventPublisher);
    }

    @Bean
    public org.digidoc4j.Configuration configuration() {
        org.digidoc4j.Configuration configuration = new org.digidoc4j.Configuration(org.digidoc4j.Configuration.Mode.TEST);
        configuration.loadConfiguration("digidoc4j.yaml");
        configuration.setTspSource("http://localhost:9091");
        configuration.setOcspSource("http://localhost:9092");
        configuration.setPreferAiaOcsp(false);
        return configuration;
    }
}
