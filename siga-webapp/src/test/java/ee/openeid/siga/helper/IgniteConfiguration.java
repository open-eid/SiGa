package ee.openeid.siga.helper;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.Ignition;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@SpringBootConfiguration
@Profile("test")
public class IgniteConfiguration {

    @Bean(destroyMethod = "close")
    public Ignite ignite() throws IgniteException {
        try {
            return Ignition.start("ignite-test-configuration.xml");
        } catch (Exception e) {
            return Ignition.start();
        }
    }
}
