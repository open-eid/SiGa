package ee.openeid.siga;

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
        return Ignition.start("ignite-test-configuration.xml");
    }
}
