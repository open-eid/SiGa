package ee.openeid.siga.service.signature.test;

import ee.openeid.siga.service.signature.client.SivaClient;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.Ignition;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

@Configuration
@ComponentScan(basePackages = {"ee.openeid.siga.auth", "ee.openeid.siga.common", "ee.openeid.siga.session", "ee.openeid.siga.service.signature"},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASPECTJ, pattern = "*..Siva*"))
public class TestConfiguration {

    @MockBean
    private SivaClient sivaClient;

    @Bean
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
