package ee.openeid.siga.auth;

import ee.openeid.siga.auth.properties.SecurityConfigurationProperties;
import ee.openeid.siga.auth.properties.VaultProperties;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.vault.config.EnvironmentVaultConfiguration;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponseSupport;

import static java.util.Objects.requireNonNull;
import static lombok.AccessLevel.PRIVATE;

@Configuration
@PropertySource(value = {"vault-config.properties"})
@Import(value = EnvironmentVaultConfiguration.class)
@FieldDefaults(level = PRIVATE)
public class VaultConfiguration {

    @Autowired
    VaultTemplate vaultTemplate;

    @Autowired
    SecurityConfigurationProperties securityConfigurationProperties;

    @Bean
    public VaultProperties sigaVaultProperties() {
        requireNonNull(securityConfigurationProperties.getVault(), "siga.security.vault properties not set!");
        VaultResponseSupport<VaultProperties> response = vaultTemplate.read(securityConfigurationProperties.getVault().getPath(), VaultProperties.class);
        return response.getData();
    }
}
