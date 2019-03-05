package ee.openeid.siga.auth;

import ee.openeid.siga.auth.properties.SecurityConfigurationProperties;
import ee.openeid.siga.auth.properties.SigaVaultProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.vault.config.EnvironmentVaultConfiguration;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponseSupport;

import static java.util.Objects.requireNonNull;

@Configuration
@PropertySource(value = {"vault-config.properties"})
@Import(value = EnvironmentVaultConfiguration.class)
public class VaultConfiguration {

    @Autowired
    VaultTemplate vaultTemplate;

    @Autowired
    SecurityConfigurationProperties securityConfigurationProperties;

    @Bean
    public SigaVaultProperties sigaVaultProperties() {
        requireNonNull(securityConfigurationProperties.getVault(), "siga.security.vault properties not set!");
        VaultResponseSupport<SigaVaultProperties> response = vaultTemplate
                .read(securityConfigurationProperties.getVault().getPath(), SigaVaultProperties.class);
        return response.getData();
    }
}
