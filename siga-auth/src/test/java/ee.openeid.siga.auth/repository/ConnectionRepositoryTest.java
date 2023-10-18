package ee.openeid.siga.auth.repository;

import ee.openeid.siga.auth.helper.TestConfiguration;
import ee.openeid.siga.auth.model.SigaClient;
import ee.openeid.siga.auth.model.SigaConnection;
import ee.openeid.siga.auth.model.SigaService;
import org.jetbrains.annotations.NotNull;
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
    @Autowired
    private ServiceRepository serviceRepository;
    @Autowired
    private ClientRepository clientRepository;

    @Test
    void deleteByContainerIdAndServiceIdShouldReturnZeroWhenNoSuchSigaConnectionExists() {
        UUID containerId = UUID.randomUUID();

        assertFalse(connectionRepository.findAllByContainerId(containerId.toString()).isPresent());

        int result = connectionRepository.deleteByContainerIdAndServiceId(containerId.toString(), 1);
        assertFalse(connectionRepository.findAllByContainerId(containerId.toString()).isPresent());
        assertEquals(0, result);
    }

    @Test
    void deleteByContainerIdAndServiceIdShouldDeleteSigaConnectionWithSpecifiedContainerIdAndServiceId() {
        UUID containerId = UUID.randomUUID();
        SigaConnection sigaConnection = createDefaultConnection(containerId.toString());
        int serviceId = sigaConnection.getService().getId();
        connectionRepository.save(sigaConnection);
        assertTrue(connectionRepository.findAllByContainerId(containerId.toString()).isPresent());

        int result = connectionRepository.deleteByContainerIdAndServiceId(containerId.toString(), serviceId);

        assertFalse(connectionRepository.findAllByContainerId(containerId.toString()).isPresent());
        assertEquals(1, result);
    }

    @Test
    void deleteByContainerIdAndServiceIdShouldNotDeleteSigaConnectionsWithUnrelatedContainerId() {
        UUID containerId = UUID.randomUUID();
        UUID unrelatedContainerId = UUID.randomUUID();
        SigaConnection unrelatedConnection = createDefaultConnection(unrelatedContainerId.toString());
        connectionRepository.save(unrelatedConnection);
        int serviceId = unrelatedConnection.getService().getId();
        assertTrue(connectionRepository.findAllByContainerId(unrelatedContainerId.toString()).isPresent());
        assertFalse(connectionRepository.findAllByContainerId(containerId.toString()).isPresent());

        int result = connectionRepository.deleteByContainerIdAndServiceId(containerId.toString(), serviceId);

        assertTrue(connectionRepository.findAllByContainerId(unrelatedContainerId.toString()).isPresent());
        assertFalse(connectionRepository.findAllByContainerId(containerId.toString()).isPresent());
        assertEquals(0, result);
    }

    @Test
    void deleteByContainerIdAndServiceIdShouldNotDeleteSigaConnectionsWithUnrelatedServiceId() {
        UUID containerId = UUID.randomUUID();
        int serviceId = 22;
        SigaConnection connection = createDefaultConnection(containerId.toString());
        connectionRepository.save(connection);
        int unrelatedServiceId = connection.getService().getId();
        assertTrue(connectionRepository.findAllByServiceId(unrelatedServiceId).isPresent());
        assertTrue(connectionRepository.findAllByServiceId(serviceId).get().isEmpty());

        int result = connectionRepository.deleteByContainerIdAndServiceId(containerId.toString(), serviceId);

        assertTrue(connectionRepository.findAllByServiceId(unrelatedServiceId).isPresent());
        assertTrue(connectionRepository.findAllByServiceId(serviceId).get().isEmpty());
        assertEquals(0, result);
    }

    private SigaConnection createDefaultConnection(String containerId) {
        SigaConnection sigaConnection = new SigaConnection();
        sigaConnection.setContainerId(containerId);
        SigaService sigaService = createDefaultSigaService();
        sigaConnection.setService(sigaService);
        return sigaConnection;
    }

    @NotNull
    private SigaService createDefaultSigaService() {
        SigaService sigaService = new SigaService();
        sigaService.setUuid(UUID.randomUUID().toString());
        sigaService.setName(sigaService.getUuid());
        sigaService.setSigningSecret("secret");
        sigaService.setSkRelyingPartyName("Relying party");
        sigaService.setSkRelyingPartyUuid("fc4122ac-71d2-11ee-ad3e-9f030f80c56d");
        SigaClient client = createDefaultSigaClient();
        sigaService.setClient(client);
        serviceRepository.save(sigaService);
        return sigaService;
    }

    @NotNull
    private SigaClient createDefaultSigaClient() {
        SigaClient client = new SigaClient();
        client.setUuid(UUID.randomUUID().toString());
        client.setName(client.getUuid());
        clientRepository.save(client);
        return client;
    }
}
