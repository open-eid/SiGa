package ee.openeid.siga.validation;

import ee.openeid.siga.common.exception.InvalidRequestException;
import ee.openeid.siga.webapp.json.CreateHashCodeContainerRequest;
import ee.openeid.siga.webapp.json.HashCodeDataFile;
import ee.openeid.siga.webapp.json.UploadHashCodeContainerRequest;
import org.apache.commons.lang3.StringUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class RequestValidatorTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void successfulCreateContainerHashCodeRequest() {
        RequestValidator.validateCreateContainerRequest(getCreateHashCodeContainerRequest());
    }

    @Test
    public void successfulUploadContainerRequest() {
        RequestValidator.validateUploadContainerRequest(getUploadContainerRequest());
    }

    @Test
    public void uploadContainer_ContainerNameEmpty() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("Container name is invalid");
        UploadHashCodeContainerRequest request = getUploadContainerRequest();
        request.setContainerName("");
        RequestValidator.validateUploadContainerRequest(request);
    }

    @Test
    public void uploadContainer_ContainerNameTooLong() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("Container name is invalid");
        UploadHashCodeContainerRequest request = getUploadContainerRequest();
        request.setContainerName(StringUtils.repeat("a", 270));
        RequestValidator.validateUploadContainerRequest(request);
    }

    @Test
    public void uploadContainer_ContainerContentEmpty() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("File content is invalid");
        UploadHashCodeContainerRequest request = getUploadContainerRequest();
        request.setContainer("");
        RequestValidator.validateUploadContainerRequest(request);
    }

    @Test
    public void uploadContainer_ContainerContentNotBase64() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("File content is invalid");
        UploadHashCodeContainerRequest request = getUploadContainerRequest();
        request.setContainer("?&%");
        RequestValidator.validateUploadContainerRequest(request);
    }


    @Test
    public void createContainer_ContainerNameEmpty() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("Container name is invalid");
        CreateHashCodeContainerRequest request = getCreateHashCodeContainerRequest();
        request.setContainerName("");
        RequestValidator.validateCreateContainerRequest(request);
    }

    @Test
    public void createContainer_ContainerNameTooLong() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("Container name is invalid");
        CreateHashCodeContainerRequest request = getCreateHashCodeContainerRequest();
        request.setContainerName(StringUtils.repeat("a", 270));
        RequestValidator.validateCreateContainerRequest(request);
    }

    @Test
    public void createContainer_NoDataFiles() {
        CreateHashCodeContainerRequest request = getCreateHashCodeContainerRequest();
        request.getDataFiles().clear();
        RequestValidator.validateCreateContainerRequest(request);
    }

    @Test
    public void createContainer_DataFileContentIsEmpty() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("Data file name is invalid");
        CreateHashCodeContainerRequest request = getCreateHashCodeContainerRequest();
        request.getDataFiles().clear();
        request.getDataFiles().add(new HashCodeDataFile());
        RequestValidator.validateCreateContainerRequest(request);
    }

    @Test
    public void createContainer_DataFileHashIsNotBase64() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("File hash is invalid");
        CreateHashCodeContainerRequest request = getCreateHashCodeContainerRequest();
        request.getDataFiles().get(0).setFileHashSha256(StringUtils.repeat("a", 101));
        RequestValidator.validateCreateContainerRequest(request);
    }

    @Test
    public void createContainer_DataFileHashTooLong() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("File hash is invalid");
        CreateHashCodeContainerRequest request = getCreateHashCodeContainerRequest();
        request.getDataFiles().get(0).setFileHashSha256("+=?!%");
        RequestValidator.validateCreateContainerRequest(request);
    }

    public static UploadHashCodeContainerRequest getUploadContainerRequest() {
        UploadHashCodeContainerRequest request = new UploadHashCodeContainerRequest();
        request.setContainerName("test.asice");
        request.setContainer("dGVzdCBmaWxlIGNvbnRlbnQ=");
        return request;
    }

    public static CreateHashCodeContainerRequest getCreateHashCodeContainerRequest() {
        CreateHashCodeContainerRequest request = new CreateHashCodeContainerRequest();
        request.setContainerName("test.asice");
        HashCodeDataFile dataFile = new HashCodeDataFile();
        dataFile.setFileName("first datafile.txt");
        dataFile.setFileSize(6);
        dataFile.setFileHashSha256("K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols");
        dataFile.setFileHashSha512("vSsar3708Jvp9Szi2NWZZ02Bqp1qRCFpbcTZPdBhnWgs5WtNZKnvCXdhztmeD2cmW192CF5bDufKRpayrW/isg");
        request.getDataFiles().add(dataFile);
        return request;
    }
}
