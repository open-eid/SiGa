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
            System.setProperty("IGNITE_OVERRIDE_CONSISTENT_ID", "node00");
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
