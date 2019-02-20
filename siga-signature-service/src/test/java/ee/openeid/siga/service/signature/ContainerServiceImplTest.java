package ee.openeid.siga.service.signature;

import ee.openeid.siga.common.ContainerType;
import ee.openeid.siga.common.exception.InvalidRequestException;
import ee.openeid.siga.service.signature.test.HashCodeContainerFilesHolder;
import ee.openeid.siga.service.signature.test.RequestUtil;
import ee.openeid.siga.service.signature.test.TestUtil;
import ee.openeid.siga.session.SessionService;
import ee.openeid.siga.webapp.json.CreateContainerRequest;
import ee.openeid.siga.webapp.json.CreateContainerResponse;
import ee.openeid.siga.webapp.json.DataFile;
import org.digidoc4j.Configuration;
import org.digidoc4j.Container;
import org.digidoc4j.ContainerOpener;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;

@RunWith(MockitoJUnitRunner.class)
public class ContainerServiceImplTest {

    @InjectMocks
    ContainerServiceImpl containerService;

    @Mock
    private SessionService sessionService;

    @Test(expected = InvalidRequestException.class)
    public void containerTypeCouldNotDetermine() {
        CreateContainerRequest request = RequestUtil.getCreateContainerRequest();
        DataFile hashCodeDataFile = new DataFile();
        hashCodeDataFile.setFileName("hashcode datafile.txt");
        hashCodeDataFile.setFileHashSha256("SGotKr7DQfmpUTMp4p6jhumLKigNONEqC0pTySrYsms");
        hashCodeDataFile.setFileHashSha512("8dvW2xdYgT9ZEJBTibWXsP9H3LTOToBaQ6McE3BoPHjRnXvVOc/REszydaAMG4Pizt9RdsdKHbd94wO/E4Kfyw");
        hashCodeDataFile.setFileSize(10);
        request.getDataFiles().add(hashCodeDataFile);
        containerService.createContainer(request);
    }

    @Test
    public void attachedContainerType() {
        CreateContainerRequest request = RequestUtil.getCreateContainerRequest();
        containerService.setSessionService(sessionService);
        CreateContainerResponse response = containerService.createContainer(request);
        Assert.assertFalse(response.getSessionId().isBlank());
    }

    @Test
    public void detachedContainerType() {
        CreateContainerRequest request = RequestUtil.getHashCodeCreateContainerRequest();
        containerService.setSessionService(sessionService);
        CreateContainerResponse response = containerService.createContainer(request);
        Assert.assertFalse(response.getSessionId().isBlank());
    }

    @Test
    public void createAttachedContainer() {
        CreateContainerRequest request = RequestUtil.getCreateContainerRequest();
        byte[] container = containerService.createContainer(ContainerType.ATTACHED, request.getDataFiles());
        Container digidoc4jContainer = ContainerOpener.open(new ByteArrayInputStream(container), Configuration.getInstance());
        Assert.assertEquals(2, digidoc4jContainer.getDataFiles().size());
        Assert.assertEquals(0, digidoc4jContainer.getSignatures().size());
        Assert.assertEquals("ASICE", digidoc4jContainer.getType());
    }

    @Test
    public void createDetachedContainer() throws IOException {
        CreateContainerRequest request = RequestUtil.getHashCodeCreateContainerRequest();
        byte[] container = containerService.createContainer(ContainerType.DETACHED, request.getDataFiles());
        HashCodeContainerFilesHolder hashCodeContainerFilesHolder = TestUtil.getContainerFiles(container);
        Assert.assertEquals(TestUtil.MIMETYPE, hashCodeContainerFilesHolder.getMimeTypeContent());
        Assert.assertEquals(TestUtil.MANIFEST_CONTENT, hashCodeContainerFilesHolder.getManifestContent());
        Assert.assertEquals(TestUtil.HASHCODES_SHA256_CONTENT, hashCodeContainerFilesHolder.getHashCodesSha256Content());
        Assert.assertEquals(TestUtil.HASHCODES_SHA512_CONTENT, hashCodeContainerFilesHolder.getHashCodesSha512Content());
    }

}
