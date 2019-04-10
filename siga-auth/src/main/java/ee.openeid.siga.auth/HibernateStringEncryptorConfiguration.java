package ee.openeid.siga.auth;

import ee.openeid.siga.auth.properties.SecurityConfigurationProperties;
import org.jasypt.encryption.pbe.PBEStringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.hibernate5.encryptor.HibernatePBEStringEncryptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static java.util.Objects.requireNonNull;

@Configuration
public class HibernateStringEncryptorConfiguration {
    public final static String HIBERNATE_STRING_ENCRYPTOR = "HIBERNATE_STRING_ENCRYPTOR";

    @Autowired
    private SecurityConfigurationProperties securityConfigurationProperties;

    @Bean
    public HibernatePBEStringEncryptor hibernatePBEStringEncryptor() {
        requireNonNull(securityConfigurationProperties.getJasypt(), "Jasypt encryption configuration properties not set!");
        HibernatePBEStringEncryptor hibernateEncryptor = new HibernatePBEStringEncryptor();
        hibernateEncryptor.setRegisteredName(HIBERNATE_STRING_ENCRYPTOR);
        hibernateEncryptor.setPassword(securityConfigurationProperties.getJasypt().getEncryptionKey());
        hibernateEncryptor.setAlgorithm(securityConfigurationProperties.getJasypt().getEncryptionAlgo());
        return hibernateEncryptor;
    }
}
