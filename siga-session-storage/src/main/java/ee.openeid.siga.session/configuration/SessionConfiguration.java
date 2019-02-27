package ee.openeid.siga.session.configuration;

import ee.openeid.siga.session.CacheName;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.CacheConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import java.util.concurrent.TimeUnit;

@SpringBootConfiguration
@EnableConfigurationProperties({
        SessionConfigurationProperties.class
})
@Profile("!test")
public class SessionConfiguration {

    private SessionConfigurationProperties sessionConfigurationProperties;

    @Bean(destroyMethod = "close")
    public Ignite ignite() throws IgniteException {
        Ignition.setClientMode(true);
        Ignite ignite = Ignition.start(sessionConfigurationProperties.getLocation());
        CacheConfiguration sessionConfiguration = new CacheConfiguration();
        sessionConfiguration.setName(CacheName.HASHCODE_CONTAINER_SESSION.name());
        Duration expiryDuration = new Duration(TimeUnit.SECONDS, sessionConfigurationProperties.getExpiryDuration());
        sessionConfiguration.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(expiryDuration));
        ignite.addCacheConfiguration(sessionConfiguration);
        return ignite;
    }

    @Autowired
    protected void setSessionConfigurationProperties(SessionConfigurationProperties sessionConfigurationProperties) {
        this.sessionConfigurationProperties = sessionConfigurationProperties;
    }
}
