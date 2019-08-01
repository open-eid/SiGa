package ee.openeid.siga.auth.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.jasypt.hibernate5.type.EncryptedStringType;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import static ee.openeid.siga.auth.HibernateStringEncryptorConfiguration.HIBERNATE_STRING_ENCRYPTOR;

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
public class SigaService {
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "CLIENT_ID", nullable = false, updatable = false)
    SigaClient client;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Exclude
    private int id;
    @NonNull
    private String name;
    @NonNull
    private String uuid;
    @Type(type = "encryptedString")
    @NonNull
    private String signingSecret;
    @NonNull
    private String skRelyingPartyName;
    @NonNull
    private String skRelyingPartyUuid;
    private String smartIdRelyingPartyName;
    private String smartIdRelyingPartyUuid;
    @NonNull
    private String billingEmail;
    private Boolean detailedBilling;
    private long maxConnectionCount;
    private long maxConnectionsSize;
}
