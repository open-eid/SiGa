package ee.openeid.siga.validation;

import ee.openeid.siga.common.exception.InvalidRequestException;
import ee.openeid.siga.webapp.json.CreateContainerRequest;
import ee.openeid.siga.webapp.json.DataFile;
import org.apache.commons.lang3.StringUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class RequestValidatorTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void successfulCreateContainerRequest() {
        RequestValidator.validateCreateContainerRequest(getCreateContainerRequest());
    }

    @Test
    public void successfulCreateContainerHashCodeRequest() {
        RequestValidator.validateCreateContainerRequest(getCreateHashCodeContainerRequest());
    }

    @Test
    public void createContainerContainerNameEmpty() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("Container name is invalid");
        CreateContainerRequest request = getCreateContainerRequest();
        request.setContainerName("");
        RequestValidator.validateCreateContainerRequest(request);
    }

    @Test
    public void createContainerContainerNameTooLong() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("Container name is invalid");
        CreateContainerRequest request = getCreateContainerRequest();
        request.setContainerName(StringUtils.repeat("a", 270));
        RequestValidator.validateCreateContainerRequest(request);
    }

    @Test
    public void createContainerNoDataFiles() {
        CreateContainerRequest request = getCreateContainerRequest();
        request.getDataFiles().clear();
        RequestValidator.validateCreateContainerRequest(request);
    }

    @Test
    public void createContainerDataFileContentIsEmpty() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("Data file name is invalid");
        CreateContainerRequest request = getCreateContainerRequest();
        request.getDataFiles().clear();
        request.getDataFiles().add(new DataFile());
        RequestValidator.validateCreateContainerRequest(request);
    }

    @Test
    public void createContainerDataFileContainerContentIsEmpty() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("File size is invalid");
        CreateContainerRequest request = getCreateContainerRequest();
        request.getDataFiles().clear();
        DataFile dataFile = new DataFile();
        dataFile.setFileName("filename.txt");
        request.getDataFiles().add(dataFile);
        RequestValidator.validateCreateContainerRequest(request);
    }

    @Test
    public void createContainerDataFileHashIsNotBase64() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("File hash is invalid");
        CreateContainerRequest request = getCreateHashCodeContainerRequest();
        request.getDataFiles().get(0).setFileHashSha256(StringUtils.repeat("a", 101));
        RequestValidator.validateCreateContainerRequest(request);
    }

    @Test
    public void createContainerDataFileHashTooLong() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("File hash is invalid");
        CreateContainerRequest request = getCreateHashCodeContainerRequest();
        request.getDataFiles().get(0).setFileHashSha256("+=?!%");
        RequestValidator.validateCreateContainerRequest(request);
    }


    public static CreateContainerRequest getCreateContainerRequest() {
        CreateContainerRequest request = new CreateContainerRequest();
        request.setContainerName("test.asice");
        DataFile dataFile = new DataFile();
        dataFile.setFileContent("dGVzdCBmaWxlIGNvbnRlbnQ=");
        dataFile.setFileName("first datafile.txt");
        request.getDataFiles().add(dataFile);
        return request;
    }

    public static CreateContainerRequest getCreateHashCodeContainerRequest() {
        CreateContainerRequest request = new CreateContainerRequest();
        request.setContainerName("test.asice");
        DataFile dataFile = new DataFile();
        dataFile.setFileName("first datafile.txt");
        dataFile.setFileSize(6);
        dataFile.setFileHashSha256("K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols");
        dataFile.setFileHashSha512("vSsar3708Jvp9Szi2NWZZ02Bqp1qRCFpbcTZPdBhnWgs5WtNZKnvCXdhztmeD2cmW192CF5bDufKRpayrW/isg");
        request.getDataFiles().add(dataFile);
        return request;
    }
}
