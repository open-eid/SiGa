package ee.openeid.siga.session.configuration;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.Ignition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

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
        return ignite;
    }

    @Autowired
    protected void setSessionConfigurationProperties(SessionConfigurationProperties sessionConfigurationProperties) {
        this.sessionConfigurationProperties = sessionConfigurationProperties;
    }
}
