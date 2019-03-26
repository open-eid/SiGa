package ee.openeid.siga.service.signature.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "siga.dds")
public class MobileServiceConfigurationProperties {

    private String urlV1;
    private String urlV2;
    private String trustStore;
    private String trustStorePassword;
}
