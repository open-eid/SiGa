package ee.openeid.siga.auth;

import ee.openeid.siga.auth.properties.SecurityConfigurationProperties;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jasypt.hibernate5.encryptor.HibernatePBEStringEncryptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HibernateStringEncryptorConfiguration {

    public static final String HIBERNATE_STRING_ENCRYPTOR = "HIBERNATE_STRING_ENCRYPTOR";

    @Bean
    public HibernatePBEStringEncryptor hibernatePBEStringEncryptor(SecurityConfigurationProperties securityConfigurationProperties) {
        HibernatePBEStringEncryptor hibernateEncryptor = new HibernatePBEStringEncryptor();
        hibernateEncryptor.setRegisteredName(HIBERNATE_STRING_ENCRYPTOR);
        hibernateEncryptor.setProvider(new BouncyCastleProvider());
        // TODO SIGA-703: jasypt seems to be a dead project and not updated for Hibernate 6. It should be replaced with something else.
        hibernateEncryptor.setPassword(securityConfigurationProperties.getJasypt().getEncryptionKey());
        hibernateEncryptor.setAlgorithm(securityConfigurationProperties.getJasypt().getEncryptionAlgo());
        return hibernateEncryptor;
    }
}
