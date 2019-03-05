package ee.openeid.siga.auth.repository;

import ee.openeid.siga.auth.model.SigaClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClientRepository extends JpaRepository<SigaClient, Integer> {
}

