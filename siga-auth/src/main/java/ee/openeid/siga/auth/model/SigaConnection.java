package ee.openeid.siga.auth.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class SigaConnection {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "SERVICE_ID", nullable = false, updatable = false)
    SigaService service;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Exclude
    private int id;
    @NonNull
    private String containerId;
    private long size;
}
