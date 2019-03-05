package ee.openeid.siga.auth.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import static lombok.AccessLevel.PRIVATE;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = PRIVATE)
public class SigaVaultProperties {

    JasyptEncryptionConf jasyptEncryptionConf;

    @Data
    @NoArgsConstructor
    @FieldDefaults(level = PRIVATE)
    public static class JasyptEncryptionConf {
        String algorithm;
        String key;
    }
}
