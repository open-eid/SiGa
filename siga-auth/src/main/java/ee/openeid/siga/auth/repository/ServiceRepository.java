package ee.openeid.siga.auth.repository;

import ee.openeid.siga.auth.model.SigaService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface ServiceRepository extends JpaRepository<SigaService, Integer> {
    Optional<SigaService> findByUuid(@Param("uuid") String uuid);
}