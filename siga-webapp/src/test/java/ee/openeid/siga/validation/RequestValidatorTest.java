package ee.openeid.siga.validation;

import ee.openeid.siga.common.MobileIdInformation;
import ee.openeid.siga.common.exception.RequestValidationException;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerRequest;
import ee.openeid.siga.webapp.json.HashcodeDataFile;
import org.apache.commons.lang3.StringUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class RequestValidatorTest {

    public static final String CONTENT = "dGVzdCBmaWxlIGNvbnRlbnQ=";

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    public static MobileIdInformation getMobileInformationRequest() {
        return MobileIdInformation.builder()
                .relyingPartyName("Testimiseks")
                .phoneNo("+37253410832")
                .personIdentifier("3489348234")
                .country("EE")
                .language("EST")
                .messageToDisplay("Random display").build();
    }

    public static CreateHashcodeContainerRequest getCreateHashcodeContainerRequest() {
        CreateHashcodeContainerRequest request = new CreateHashcodeContainerRequest();
        HashcodeDataFile dataFile = new HashcodeDataFile();
        dataFile.setFileName("first datafile.txt");
        dataFile.setFileSize(6);
        dataFile.setFileHashSha256("VKZIO4rKVcnfKjW69x2ZZd39YjRo2B1RIpvV630eHBs=");
        dataFile.setFileHashSha512("hIVQtdcSnvLY9JK3VnZkKrJ41s1fHYFqzpiNFY4ZlkVeXiPL5Nu7Kd/cVXYEBuME26QIeI2q6gI7OjLIbl9SUw==");
        request.getDataFiles().add(dataFile);
        return request;
    }

    @Test
    public void successfulCreateContainerHashcodeRequest() {
        RequestValidator.validateHashcodeDataFiles(getCreateHashcodeContainerRequest().getDataFiles());
    }

    @Test
    public void successfulFileContent() {
        RequestValidator.validateFileContent(CONTENT);
    }

    @Test
    public void containerContentEmpty() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("File content is invalid");
        RequestValidator.validateFileContent("");
    }

    @Test
    public void containerContentNotBase64() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("File content is invalid");
        RequestValidator.validateFileContent("?&%");
    }

    @Test
    public void createContainer_NoDataFiles() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Data files are needed");
        CreateHashcodeContainerRequest request = getCreateHashcodeContainerRequest();
        request.getDataFiles().clear();
        RequestValidator.validateHashcodeDataFiles(request.getDataFiles());
    }

    @Test
    public void createContainer_DataFileContentIsEmpty() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Data file name is invalid");
        CreateHashcodeContainerRequest request = getCreateHashcodeContainerRequest();
        request.getDataFiles().clear();
        request.getDataFiles().add(new HashcodeDataFile());
        RequestValidator.validateHashcodeDataFiles(request.getDataFiles());
    }

    @Test
    public void createContainer_DataFileNameInvalid() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Data file name is invalid");
        CreateHashcodeContainerRequest request = getCreateHashcodeContainerRequest();
        request.getDataFiles().add(new HashcodeDataFile());
        request.getDataFiles().get(0).setFileName("*/random.txt");
        RequestValidator.validateHashcodeDataFiles(request.getDataFiles());
    }

    @Test
    public void createContainer_DataFileHashIsNotBase64() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("File hash is invalid");
        CreateHashcodeContainerRequest request = getCreateHashcodeContainerRequest();
        request.getDataFiles().get(0).setFileHashSha256(StringUtils.repeat("a", 101));
        RequestValidator.validateHashcodeDataFiles(request.getDataFiles());
    }

    @Test
    public void createContainer_DataFileHashTooLong() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("File hash is invalid");
        CreateHashcodeContainerRequest request = getCreateHashcodeContainerRequest();
        request.getDataFiles().get(0).setFileHashSha256("+=?!%");
        RequestValidator.validateHashcodeDataFiles(request.getDataFiles());
    }

    @Test
    public void containerIdIsNull() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Container Id is invalid");
        RequestValidator.validateContainerId(null);
    }

    @Test
    public void containerIdIsEmpty() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Container Id is invalid");
        RequestValidator.validateContainerId("");
    }

    @Test
    public void containerIdIsTooLong() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Container Id is invalid");
        RequestValidator.validateContainerId(StringUtils.repeat("a", 37));
    }

    @Test
    public void successfulRemoteSigning() {
        RequestValidator.validateRemoteSigning("dGVzdCBoYXNo", "LT");
    }

    @Test
    public void invalidSigningCertificate() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid signing certificate");
        RequestValidator.validateRemoteSigning("+=?!%", "LT");
    }

    @Test
    public void emptySigningCertificate() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid signing certificate");
        RequestValidator.validateRemoteSigning("", "LT");
    }

    @Test
    public void oldSignatureProfile() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid signature profile");
        RequestValidator.validateRemoteSigning("dGVzdCBoYXNo", "B_BES");
    }

    @Test
    public void invalidSignatureProfile() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid signature profile");
        RequestValidator.validateRemoteSigning("dGVzdCBoYXNo", "TL");
    }

    @Test
    public void emptySignatureProfile() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid signature profile");
        RequestValidator.validateRemoteSigning("dGVzdCBoYXNo", "");
    }

    @Test
    public void successfulSignatureValue() {
        RequestValidator.validateSignatureValue("dGVzdCBoYXNo");
    }

    @Test
    public void emptySignatureValue() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid signature value");
        RequestValidator.validateSignatureValue("");
    }

    @Test
    public void invalidSignatureValue() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid signature value");
        RequestValidator.validateSignatureValue("+=?!%");
    }

    @Test
    public void successfulMobileInformation() {
        RequestValidator.validateMobileIdInformation(getMobileInformationRequest());
    }

    @Test
    public void nullLanguage() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid Mobile-Id language");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setLanguage(null);
        RequestValidator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void emptyLanguage() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid Mobile-Id language");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setLanguage("");
        RequestValidator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void invalidLanguage() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid Mobile-Id language");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setLanguage("ESTO");
        RequestValidator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void invalidMessageToDisplay() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid Mobile-Id message to display");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setMessageToDisplay(StringUtils.repeat("a", 41));
        RequestValidator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void nullMessageToDisplay() {
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setMessageToDisplay(null);
        RequestValidator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void nullPhoneNo() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid phone No.");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setPhoneNo(null);
        RequestValidator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void emptyPhoneNo() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid phone No.");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setPhoneNo("");
        RequestValidator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void invalidPhoneNo() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid phone No.");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setPhoneNo(StringUtils.repeat("a", 21));
        RequestValidator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void nullPersonIdentifier() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid person identifier");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setPersonIdentifier(null);
        RequestValidator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void emptyPersonIdentifier() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid person identifier");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setPersonIdentifier("");
        RequestValidator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void invalidPersonIdentifier() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid person identifier");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setPersonIdentifier(StringUtils.repeat("a", 31));
        RequestValidator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void nullCountry() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid country of origin");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setCountry(null);
        RequestValidator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void emptyCountry() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid country of origin");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setCountry("");
        RequestValidator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void invalidCountry() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid country of origin");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setCountry("EST");
        RequestValidator.validateMobileIdInformation(mobileIdInformation);
    }
}
