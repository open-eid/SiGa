package ee.openeid.siga.service.signature.test;

import ee.openeid.siga.webapp.json.CreateContainerRequest;
import ee.openeid.siga.webapp.json.DataFile;

public class RequestUtil {

    public static CreateContainerRequest getCreateContainerRequest() {
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

    public static CreateContainerRequest getHashCodeCreateContainerRequest() {
        CreateContainerRequest request = new CreateContainerRequest();
        request.setContainerName("test.asice");
        DataFile dataFile1 = new DataFile();
        dataFile1.setFileHashSha256("SGotKr7DQfmpUTMp4p6jhumLKigNONEqC0pTySrYsms");
        dataFile1.setFileHashSha512("8dvW2xdYgT9ZEJBTibWXsP9H3LTOToBaQ6McE3BoPHjRnXvVOc/REszydaAMG4Pizt9RdsdKHbd94wO/E4Kfyw");
        dataFile1.setFileSize(10);
        dataFile1.setFileName("first datafile.txt");
        request.getDataFiles().add(dataFile1);
        DataFile dataFile2 = new DataFile();
        dataFile2.setFileHashSha256("SGotKr7DQfmpUTMp4p6jhumLKigNONEqC0pTySrYsms");
        dataFile2.setFileHashSha512("8dvW2xdYgT9ZEJBTibWXsP9H3LTOToBaQ6McE3BoPHjRnXvVOc/REszydaAMG4Pizt9RdsdKHbd94wO/E4Kfyw");
        dataFile2.setFileSize(10);
        dataFile2.setFileName("second datafile.txt");
        request.getDataFiles().add(dataFile2);
        return request;
    }
}
