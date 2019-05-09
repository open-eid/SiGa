package ee.openeid.siga.auth;

import ee.openeid.siga.auth.properties.SecurityConfigurationProperties;
import org.jasypt.hibernate5.encryptor.HibernatePBEStringEncryptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HibernateStringEncryptorConfiguration {
    public final static String HIBERNATE_STRING_ENCRYPTOR = "HIBERNATE_STRING_ENCRYPTOR";

    @Autowired
    private SecurityConfigurationProperties securityConfigurationProperties;

    @Bean
    public HibernatePBEStringEncryptor hibernatePBEStringEncryptor() {
        HibernatePBEStringEncryptor hibernateEncryptor = new HibernatePBEStringEncryptor();
        hibernateEncryptor.setRegisteredName(HIBERNATE_STRING_ENCRYPTOR);
        hibernateEncryptor.setPassword(securityConfigurationProperties.getJasypt().getEncryptionKey());
        hibernateEncryptor.setAlgorithm(securityConfigurationProperties.getJasypt().getEncryptionAlgo());
        return hibernateEncryptor;
    }
}
