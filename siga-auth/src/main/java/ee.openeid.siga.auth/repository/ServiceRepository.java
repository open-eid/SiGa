package ee.openeid.siga.auth.repository;

import ee.openeid.siga.auth.model.SigaService;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
@CacheConfig(cacheNames = {"services"})
public interface ServiceRepository extends JpaRepository<SigaService, Integer> {

    @Cacheable
    Optional<SigaService> findByUuid(@Param("uuid") String uuid);
}

