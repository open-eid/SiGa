package ee.openeid.siga.auth.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.jasypt.hibernate5.type.EncryptedStringType;

import javax.persistence.*;

import static ee.openeid.siga.auth.HibernateStringEncryptorConfiguration.HIBERNATE_STRING_ENCRYPTOR;
import static lombok.AccessLevel.PRIVATE;

@TypeDef(
        name = "encryptedString", typeClass = EncryptedStringType.class,
        parameters = {
                @Parameter(name = "encryptorRegisteredName", value = HIBERNATE_STRING_ENCRYPTOR)
        }
)

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@FieldDefaults(level = PRIVATE)
public class SigaService {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Exclude
    int id;

    @NonNull String name;
    @NonNull String uuid;
    @Type(type = "encryptedString")
    @NonNull String signingSecret;
    @NonNull String skRelyingPartyName;
    @NonNull String skRelyingPartyUuid;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "CLIENT_ID", unique = true, nullable = false, updatable = false)
    SigaClient client;
}
