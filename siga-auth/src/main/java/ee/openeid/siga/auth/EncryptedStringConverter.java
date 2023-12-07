package ee.openeid.siga.auth;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.jasypt.hibernate5.encryptor.HibernatePBEStringEncryptor;
import org.springframework.beans.factory.annotation.Autowired;

@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Autowired
    HibernatePBEStringEncryptor stringEncryptor;

    @Override
    public String convertToDatabaseColumn(String s) {
        return stringEncryptor.encrypt(s);
    }

    @Override
    public String convertToEntityAttribute(String s) {
        return stringEncryptor.decrypt(s);
    }
}
