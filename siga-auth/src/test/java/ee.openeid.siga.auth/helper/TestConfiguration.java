package ee.openeid.siga.auth.helper;

import ee.openeid.siga.auth.SecurityConfiguration;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.Ignition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

@Profile("test")
@Configuration
@Import(SecurityConfiguration.class)
@ComponentScan(basePackages = {"ee.openeid.siga.auth", "ee.openeid.siga.common"})
public class TestConfiguration {
    @Bean(destroyMethod = "close")
    public Ignite ignite() throws IgniteException {
        try {
            return Ignition.start("ignite-test-configuration.xml");
        } catch (Exception e) {
            try {
                return Ignition.start();
            } catch (Exception ex) {
                return Ignition.ignite();
            }
        }
    }
}
