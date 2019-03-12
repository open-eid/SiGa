package ee.openeid.siga.auth.model;

import lombok.*;
import lombok.experimental.FieldDefaults;

import javax.persistence.*;
import java.util.Set;

import static lombok.AccessLevel.PRIVATE;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@FieldDefaults(level = PRIVATE)
public class SigaClient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Exclude
    int id;

    @NonNull String name;
    @NonNull String contactName;
    @NonNull String contactEmail;
    @NonNull String contactPhone;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY, mappedBy = "client")
    @ToString.Exclude
    Set<SigaService> services;
}
