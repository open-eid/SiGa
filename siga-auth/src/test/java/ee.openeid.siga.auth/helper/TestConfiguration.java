package ee.openeid.siga.auth.helper;

import ee.openeid.siga.auth.SecurityConfiguration;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.Ignition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(SecurityConfiguration.class)
@ComponentScan(basePackages = {"ee.openeid.siga.auth", "ee.openeid.siga.common"})
public class TestConfiguration {

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
}
