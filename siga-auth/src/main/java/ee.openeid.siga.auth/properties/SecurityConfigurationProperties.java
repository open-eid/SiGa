package ee.openeid.siga.auth.properties;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static lombok.AccessLevel.PRIVATE;

@Getter
@Setter
@FieldDefaults(level = PRIVATE)
@ConfigurationProperties(prefix = "siga.security")
public class SecurityConfigurationProperties {

    HmacConf hmac;
    JasyptConf jasypt;

    @Getter
    @Setter
    @FieldDefaults(level = PRIVATE)
    public static class HmacConf {
        long expiration;
        long clockSkew;
    }

    @Getter
    @Setter
    @FieldDefaults(level = PRIVATE)
    public static class JasyptConf {
        String encryptionAlgo;
        String encryptionKey;
    }
}
