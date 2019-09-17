package ee.openeid.siga.auth.repository;

import ee.openeid.siga.auth.model.SigaConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConnectionRepository extends JpaRepository<SigaConnection, Integer> {
    Optional<List<SigaConnection>> findAllByServiceId(@Param("service_id") int serviceId);
    Optional<SigaConnection> findAllByContainerId(@Param("container_id") String containerId);

    @Modifying
    @Transactional
    @Query("delete from SigaConnection c where c.containerId=:container_id")
    int deleteByContainerId(@Param("container_id") String containerId);
}
