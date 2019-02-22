package ee.openeid.siga;

import ee.openeid.siga.session.CacheName;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.CacheConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@SpringBootConfiguration
@Profile("test")
public class IgniteConfiguration {

    @Bean(destroyMethod = "close")
    public Ignite ignite() throws IgniteException {
        Ignite ignite = Ignition.start();
        CacheConfiguration sessionConfiguration = new CacheConfiguration();
        sessionConfiguration.setName(CacheName.HASHCODE_CONTAINER_SESSION.name());
        ignite.addCacheConfiguration(sessionConfiguration);
        return ignite;
    }
}
