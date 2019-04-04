package ee.openeid.siga.auth.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "siga.security")
public class SecurityConfigurationProperties {

    private HmacConf hmac;
    private JasyptConf jasypt;

    @Getter
    @Setter
    public static class HmacConf {
        private long expiration;
        private long clockSkew;
    }

    @Getter
    @Setter
    public static class JasyptConf {
        private String encryptionAlgo;
        private String encryptionKey;
    }
}
