package ee.openeid.siga.auth.properties;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "siga.security")
@Validated
@Getter
@Setter
public class SecurityConfigurationProperties {
    private List<@NonNull String> prohibitedPoliciesForRemoteSigning = new ArrayList<>(Arrays.asList("1.3.6.1.4.1.10015.1.3", "1.3.6.1.4.1.10015.18.1", "1.3.6.1.4.1.10015.17.2", "1.3.6.1.4.1.10015.17.1"));

    private int maxFileSize = 4194304; //4mb
    @Valid
    private HmacConf hmac = new HmacConf();

    @Valid
    private JasyptConf jasypt = new JasyptConf();

    @Validated
    @Getter
    @Setter
    public static class HmacConf {
        @Min(-1)
        @NotNull(message = "siga.security.hmac.expiration propery must be set.")
        private Long expiration;
        @NotNull(message = "siga.security.hmac.clock-skew propery must be set")
        @PositiveOrZero
        private Long clockSkew;
    }

    @Validated
    @Getter
    @Setter
    public static class JasyptConf {
        @NotBlank(message = "siga.security.jasypt.encryption-algo propery must be set")
        private String encryptionAlgo;
        @NotBlank(message = "siga.security.jasypt.encryption-key propery must be set")
        private String encryptionKey;
    }
}
