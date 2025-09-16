package ee.openeid.siga.common;

import ee.openeid.siga.common.configuration.SivaClientConfiguration;
import ee.openeid.siga.common.configuration.SivaClientConfigurationProperties;
import ee.openeid.siga.common.exception.TechnicalException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.time.Duration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;


public class SivaClientConfigurationTest {

    private static final String DEFAULT_BASE_URL = "http://host:1234/path";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(1L);

    @Test
    void sivaHttpClient_WhenConfiguredTruststoreIsEmpty_TechnicalExceptionIsThrown() {
        SivaClientConfiguration clientConfiguration = new SivaClientConfiguration();
        SivaClientConfigurationProperties configurationProperties = createDefaultConfigurationPropertiesWithTruststore(
                new ClassPathResource("empty-truststore.p12"),
                "changeit"
        );

        TechnicalException caughtException = assertThrows(
                TechnicalException.class,
                () -> clientConfiguration.sivaHttpClient(configurationProperties));

        assertThat(caughtException.getMessage(), equalTo("Keystore is empty - no certificate found"));
    }

    @Test
    void sivaHttpClient_WhenTrustStoreResourceIsUnreadable_TechnicalExceptionIsThrown() {
        SivaClientConfiguration clientConfiguration = new SivaClientConfiguration();
        SivaClientConfigurationProperties configurationProperties = createDefaultConfigurationPropertiesWithTruststore(
                new ClassPathResource("path/to/your/truststore.p12"),
                "changeit"
        );

        TechnicalException caughtException = assertThrows(
                TechnicalException.class,
                () -> clientConfiguration.sivaHttpClient(configurationProperties));

        assertThat(caughtException.getMessage(), equalTo("Failed to load KeyStore from resource"));
    }

    private static SivaClientConfigurationProperties createDefaultConfigurationPropertiesWithTruststore(
            Resource truststoreResource,
            String truststorePassword
    ) {
        SivaClientConfigurationProperties configurationProperties = new SivaClientConfigurationProperties();

        configurationProperties.setUrl(DEFAULT_BASE_URL);
        configurationProperties.setTrustStore(truststoreResource);
        configurationProperties.setTrustStorePassword(truststorePassword);
        configurationProperties.setConnectionTimeout(DEFAULT_TIMEOUT);
        configurationProperties.setWriteTimeout(DEFAULT_TIMEOUT);
        configurationProperties.setReadTimeout(DEFAULT_TIMEOUT);

        return configurationProperties;
    }
}
