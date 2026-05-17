package ee.openeid.siga.helper;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.Ignition;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@SpringBootConfiguration
@ConditionalOnProperty(prefix = "siga.session-storage", name = "type", havingValue = "ignite")
public class IgniteConfiguration {

    private static final String INSTANCE_NAME = "siga-ignite";

    @Bean
    public Ignite ignite() throws IgniteException {
        System.setProperty("IGNITE_OVERRIDE_CONSISTENT_ID", "node00");
        return Ignition.allGrids().stream()
                .filter(grid -> INSTANCE_NAME.equals(grid.name()))
                .findFirst()
                .orElseGet(() -> Ignition.start("ignite-test-configuration.xml"));
    }
}
