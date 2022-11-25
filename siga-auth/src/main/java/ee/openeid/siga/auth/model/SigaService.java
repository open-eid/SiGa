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

import javax.persistence.*;

import java.util.HashSet;
import java.util.Set;

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
    private String billingEmail;
    private long maxConnectionCount;
    private long maxConnectionsSize;
    private long maxConnectionSize;
    private boolean inactive;
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY, mappedBy = "service")
    @ToString.Exclude
    private Set<SigaIpPermission> ipPermissions = new HashSet<>();
}
