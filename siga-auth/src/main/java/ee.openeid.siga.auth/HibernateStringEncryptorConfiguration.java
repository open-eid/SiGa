package ee.openeid.siga.auth;

import ee.openeid.siga.auth.properties.SigaVaultProperties;
import lombok.experimental.FieldDefaults;
import org.jasypt.encryption.pbe.PBEStringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.hibernate5.encryptor.HibernatePBEStringEncryptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static java.util.Objects.requireNonNull;
import static lombok.AccessLevel.PRIVATE;

@Configuration
@FieldDefaults(level = PRIVATE)
public class HibernateStringEncryptorConfiguration {

    public final static String HIBERNATE_STRING_ENCRYPTOR = "HIBERNATE_STRING_ENCRYPTOR";

    @Autowired
    SigaVaultProperties vaultProperties;

    @Bean
    public HibernatePBEStringEncryptor hibernatePBEStringEncryptor(PBEStringEncryptor stringEncryptor) {
        HibernatePBEStringEncryptor hibernateEncryptor = new HibernatePBEStringEncryptor();
        hibernateEncryptor.setRegisteredName(HIBERNATE_STRING_ENCRYPTOR);
        hibernateEncryptor.setEncryptor(stringEncryptor);
        return hibernateEncryptor;
    }

    @Bean
    public PBEStringEncryptor pbeStringEncryptor() {
        requireNonNull(vaultProperties.getJasyptEncryptionConf(), "Jasypt encryption configuration " +
                "properties not set in Vault!");
        PooledPBEStringEncryptor stringEncryptor = new PooledPBEStringEncryptor();
        stringEncryptor.setPassword(vaultProperties.getJasyptEncryptionConf().getKey());
        stringEncryptor.setAlgorithm(vaultProperties.getJasyptEncryptionConf().getAlgorithm());
        stringEncryptor.setPoolSize(4);
        return stringEncryptor;
    }
}
