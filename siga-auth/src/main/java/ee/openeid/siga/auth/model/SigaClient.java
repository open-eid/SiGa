package ee.openeid.siga.auth.model;

import lombok.*;

import javax.persistence.*;
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
    private String name;
    @NonNull
    private String contactName;
    @NonNull
    private String contactEmail;
    @NonNull
    private String contactPhone;
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY, mappedBy = "client")
    @ToString.Exclude
    private Set<SigaService> services;
}
