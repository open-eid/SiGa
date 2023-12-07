package ee.openeid.siga.auth.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class SigaClient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Exclude
    private int id;
    @NonNull
    private String uuid;
    @NonNull
    private String name;
    private String contactName;
    private String contactEmail;
    private String contactPhone;
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY, mappedBy = "client")
    @ToString.Exclude
    private Set<SigaService> services;
}
