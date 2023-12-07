package ee.openeid.siga.auth.model;

import ee.openeid.siga.auth.EncryptedStringConverter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

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
    @Convert(converter = EncryptedStringConverter.class)
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
