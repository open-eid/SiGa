package ee.openeid.siga.auth.repository;

import ee.openeid.siga.auth.helper.TestConfiguration;
import ee.openeid.siga.auth.model.SigaConnection;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.UUID;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@ActiveProfiles("test")
@SpringBootTest(classes = {TestConfiguration.class}, webEnvironment = RANDOM_PORT,
        properties = {"spring.main.allow-bean-definition-overriding=true",
                "siga.security.hmac.expiration=120",
                "siga.security.hmac.clock-skew=2"})
public class ConnectionRepositoryTest {

    @Autowired
    private ConnectionRepository connectionRepository;

    @Test
    public void deleteByContainerIdShouldReturnZeroWhenNoSigaConnectionWithSuchContainerIdExists() {
        UUID containerId = UUID.randomUUID();

        Assert.assertFalse(connectionRepository.findAllByContainerId(containerId.toString()).isPresent());

        int result = connectionRepository.deleteByContainerId(containerId.toString());
        Assert.assertFalse(connectionRepository.findAllByContainerId(containerId.toString()).isPresent());
        Assert.assertEquals(0, result);
    }

    @Test
    public void deleteByContainerIdShouldDeleteSigaConnectionWithSpecifiedContainerId() {
        UUID containerId = UUID.randomUUID();

        connectionRepository.save(createDefaultConnection(containerId.toString()));
        Assert.assertTrue(connectionRepository.findAllByContainerId(containerId.toString()).isPresent());

        int result = connectionRepository.deleteByContainerId(containerId.toString());
        Assert.assertFalse(connectionRepository.findAllByContainerId(containerId.toString()).isPresent());
        Assert.assertEquals(1, result);
    }

    @Test
    public void deleteByContainerIdShouldNotDeleteSigaConnectionsWithUnrelatedContainerId() {
        UUID otherContainerId = UUID.randomUUID();
        UUID containerId = UUID.randomUUID();

        connectionRepository.save(createDefaultConnection(otherContainerId.toString()));
        Assert.assertTrue(connectionRepository.findAllByContainerId(otherContainerId.toString()).isPresent());
        Assert.assertFalse(connectionRepository.findAllByContainerId(containerId.toString()).isPresent());

        int result = connectionRepository.deleteByContainerId(containerId.toString());
        Assert.assertTrue(connectionRepository.findAllByContainerId(otherContainerId.toString()).isPresent());
        Assert.assertFalse(connectionRepository.findAllByContainerId(containerId.toString()).isPresent());
        Assert.assertEquals(0, result);
    }

    private SigaConnection createDefaultConnection(String containerId) {
        SigaConnection sigaConnection = new SigaConnection();
        sigaConnection.setContainerId(containerId);
        return sigaConnection;
    }
}
