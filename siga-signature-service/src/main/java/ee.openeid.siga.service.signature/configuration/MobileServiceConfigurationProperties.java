package ee.openeid.siga.service.signature.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "siga.dds")
public class MobileServiceConfigurationProperties {

    private String url;
    private String trustStore;
    private String trustStorePassword;
}
