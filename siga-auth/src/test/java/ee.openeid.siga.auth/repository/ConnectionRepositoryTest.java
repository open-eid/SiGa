package ee.openeid.siga.auth.repository;

import ee.openeid.siga.auth.helper.TestConfiguration;
import ee.openeid.siga.auth.model.SigaConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
@SpringBootTest(classes = {TestConfiguration.class}, webEnvironment = RANDOM_PORT,
        properties = {"spring.main.allow-bean-definition-overriding=true",
                "siga.security.hmac.expiration=120",
                "siga.security.hmac.clock-skew=2"})
class ConnectionRepositoryTest {

    @Autowired
    private ConnectionRepository connectionRepository;

    @Test
    void deleteByContainerIdShouldReturnZeroWhenNoSigaConnectionWithSuchContainerIdExists() {
        UUID containerId = UUID.randomUUID();

        assertFalse(connectionRepository.findAllByContainerId(containerId.toString()).isPresent());

        int result = connectionRepository.deleteByContainerId(containerId.toString());
        assertFalse(connectionRepository.findAllByContainerId(containerId.toString()).isPresent());
        assertEquals(0, result);
    }

    @Test
    void deleteByContainerIdShouldDeleteSigaConnectionWithSpecifiedContainerId() {
        UUID containerId = UUID.randomUUID();

        connectionRepository.save(createDefaultConnection(containerId.toString()));
        assertTrue(connectionRepository.findAllByContainerId(containerId.toString()).isPresent());

        int result = connectionRepository.deleteByContainerId(containerId.toString());
        assertFalse(connectionRepository.findAllByContainerId(containerId.toString()).isPresent());
        assertEquals(1, result);
    }

    @Test
    void deleteByContainerIdShouldNotDeleteSigaConnectionsWithUnrelatedContainerId() {
        UUID otherContainerId = UUID.randomUUID();
        UUID containerId = UUID.randomUUID();

        connectionRepository.save(createDefaultConnection(otherContainerId.toString()));
        assertTrue(connectionRepository.findAllByContainerId(otherContainerId.toString()).isPresent());
        assertFalse(connectionRepository.findAllByContainerId(containerId.toString()).isPresent());

        int result = connectionRepository.deleteByContainerId(containerId.toString());
        assertTrue(connectionRepository.findAllByContainerId(otherContainerId.toString()).isPresent());
        assertFalse(connectionRepository.findAllByContainerId(containerId.toString()).isPresent());
        assertEquals(0, result);
    }

    private SigaConnection createDefaultConnection(String containerId) {
        SigaConnection sigaConnection = new SigaConnection();
        sigaConnection.setContainerId(containerId);
        return sigaConnection;
    }
}
