package ee.openeid.siga.auth.properties;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static lombok.AccessLevel.PRIVATE;

@Getter
@Setter
@ConfigurationProperties(prefix = "siga.security")
public class SecurityConfigurationProperties {

    private HmacConf hmac;
    private VaultConf vault;
    private EhCacheConf ehcache;

    @Getter
    @Setter
    @FieldDefaults(level = PRIVATE)
    public static class EhCacheConf {
        int timeToLive;
        int timeToIdle;
    }

    @Getter
    @Setter
    @FieldDefaults(level = PRIVATE)
    public static class HmacConf {
        int expiration;
        int clockSkew;
        String macAlgorithm;
    }

    @Getter
    @Setter
    @FieldDefaults(level = PRIVATE)
    public static class VaultConf {
        String path;
    }
}
