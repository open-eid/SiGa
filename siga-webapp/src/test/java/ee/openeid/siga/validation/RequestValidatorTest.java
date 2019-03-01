package ee.openeid.siga.validation;

import ee.openeid.siga.common.exception.InvalidRequestException;
import ee.openeid.siga.webapp.json.CreateHashCodeContainerRequest;
import ee.openeid.siga.webapp.json.HashCodeDataFile;
import org.apache.commons.lang3.StringUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class RequestValidatorTest {

    public static final String CONTENT = "dGVzdCBmaWxlIGNvbnRlbnQ=";

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void successfulCreateContainerHashCodeRequest() {
        RequestValidator.validateHashCodeDataFiles(getCreateHashCodeContainerRequest().getDataFiles());
    }

    @Test
    public void successfulFileContent() {
        RequestValidator.validateFileContent(CONTENT);
    }

    @Test
    public void containerContentEmpty() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("File content is invalid");
        RequestValidator.validateFileContent("");
    }

    @Test
    public void containerContentNotBase64() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("File content is invalid");
        RequestValidator.validateFileContent("?&%");
    }

    @Test
    public void createContainer_NoDataFiles() {
        CreateHashCodeContainerRequest request = getCreateHashCodeContainerRequest();
        request.getDataFiles().clear();
        RequestValidator.validateHashCodeDataFiles(request.getDataFiles());
    }

    @Test
    public void createContainer_DataFileContentIsEmpty() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("Data file name is invalid");
        CreateHashCodeContainerRequest request = getCreateHashCodeContainerRequest();
        request.getDataFiles().clear();
        request.getDataFiles().add(new HashCodeDataFile());
        RequestValidator.validateHashCodeDataFiles(request.getDataFiles());
    }

    @Test
    public void createContainer_DataFileHashIsNotBase64() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("File hash is invalid");
        CreateHashCodeContainerRequest request = getCreateHashCodeContainerRequest();
        request.getDataFiles().get(0).setFileHashSha256(StringUtils.repeat("a", 101));
        RequestValidator.validateHashCodeDataFiles(request.getDataFiles());
    }

    @Test
    public void createContainer_DataFileHashTooLong() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("File hash is invalid");
        CreateHashCodeContainerRequest request = getCreateHashCodeContainerRequest();
        request.getDataFiles().get(0).setFileHashSha256("+=?!%");
        RequestValidator.validateHashCodeDataFiles(request.getDataFiles());
    }

    @Test
    public void containerIdIsNull() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("Container Id is invalid");
        RequestValidator.validateContainerId(null);
    }

    @Test
    public void containerIdIsEmpty() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("Container Id is invalid");
        RequestValidator.validateContainerId("");
    }

    @Test
    public void containerIdIsTooLong() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("Container Id is invalid");
        RequestValidator.validateContainerId(StringUtils.repeat("a", 37));
    }

    public static CreateHashCodeContainerRequest getCreateHashCodeContainerRequest() {
        CreateHashCodeContainerRequest request = new CreateHashCodeContainerRequest();
        HashCodeDataFile dataFile = new HashCodeDataFile();
        dataFile.setFileName("first datafile.txt");
        dataFile.setFileSize(6);
        dataFile.setFileHashSha256("K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols");
        dataFile.setFileHashSha512("vSsar3708Jvp9Szi2NWZZ02Bqp1qRCFpbcTZPdBhnWgs5WtNZKnvCXdhztmeD2cmW192CF5bDufKRpayrW/isg");
        request.getDataFiles().add(dataFile);
        return request;
    }
}
