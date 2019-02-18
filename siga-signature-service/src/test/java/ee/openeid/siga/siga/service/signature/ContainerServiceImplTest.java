package ee.openeid.siga.siga.service.signature;

import ee.openeid.siga.common.exception.InvalidRequestException;
import ee.openeid.siga.service.signature.ContainerServiceImpl;
import ee.openeid.siga.webapp.json.CreateContainerRequest;
import ee.openeid.siga.webapp.json.DataFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ContainerServiceImplTest {

    @InjectMocks
    ContainerServiceImpl containerService;

    @Test(expected = InvalidRequestException.class)
    public void containerTypeCouldNotDetermine() {
        CreateContainerRequest request = getCreateContainerRequest();
        DataFile hashCodeDataFile = new DataFile();
        hashCodeDataFile.setFileName("hashcode datafile.txt");
        hashCodeDataFile.setFileHashSha256("SGotKr7DQfmpUTMp4p6jhumLKigNONEqC0pTySrYsms");
        hashCodeDataFile.setFileHashSha512("8dvW2xdYgT9ZEJBTibWXsP9H3LTOToBaQ6McE3BoPHjRnXvVOc/REszydaAMG4Pizt9RdsdKHbd94wO/E4Kfyw");
        hashCodeDataFile.setFileSize(10);
        request.getDataFiles().add(hashCodeDataFile);
        containerService.createContainer(request);
    }

    private CreateContainerRequest getCreateContainerRequest() {
        CreateContainerRequest request = new CreateContainerRequest();
        request.setContainerName("test.asice");
        DataFile dataFile1 = new DataFile();
        dataFile1.setFileContent("dGVzdCBmaWxlIGNvbnRlbnQ=");
        dataFile1.setFileName("first datafile.txt");
        request.getDataFiles().add(dataFile1);
        DataFile dataFile2 = new DataFile();
        dataFile2.setFileContent("cmFuZG9tIGZpbGUgY29udGVudA==");
        dataFile2.setFileName("second datafile.txt");
        request.getDataFiles().add(dataFile2);
        return request;
    }
}
