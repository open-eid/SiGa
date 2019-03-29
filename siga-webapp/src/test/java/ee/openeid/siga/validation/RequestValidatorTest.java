package ee.openeid.siga.validation;

import ee.openeid.siga.common.MobileIdInformation;
import ee.openeid.siga.common.exception.InvalidRequestException;
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
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("Data files are needed");
        CreateHashcodeContainerRequest request = getCreateHashcodeContainerRequest();
        request.getDataFiles().clear();
        RequestValidator.validateHashcodeDataFiles(request.getDataFiles());
    }

    @Test
    public void createContainer_DataFileContentIsEmpty() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("Data file name is invalid");
        CreateHashcodeContainerRequest request = getCreateHashcodeContainerRequest();
        request.getDataFiles().clear();
        request.getDataFiles().add(new HashcodeDataFile());
        RequestValidator.validateHashcodeDataFiles(request.getDataFiles());
    }

    @Test
    public void createContainer_DataFileHashIsNotBase64() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("File hash is invalid");
        CreateHashcodeContainerRequest request = getCreateHashcodeContainerRequest();
        request.getDataFiles().get(0).setFileHashSha256(StringUtils.repeat("a", 101));
        RequestValidator.validateHashcodeDataFiles(request.getDataFiles());
    }

    @Test
    public void createContainer_DataFileHashTooLong() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("File hash is invalid");
        CreateHashcodeContainerRequest request = getCreateHashcodeContainerRequest();
        request.getDataFiles().get(0).setFileHashSha256("+=?!%");
        RequestValidator.validateHashcodeDataFiles(request.getDataFiles());
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

    @Test
    public void successfulRemoteSigning() {
        RequestValidator.validateRemoteSigning("K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols", "LT");
    }

    @Test
    public void invalidSigningCertificate() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("Invalid signing certificate");
        RequestValidator.validateRemoteSigning("+=?!%", "LT");
    }

    @Test
    public void emptySigningCertificate() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("Invalid signing certificate");
        RequestValidator.validateRemoteSigning("", "LT");
    }

    @Test
    public void oldSignatureProfile() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("Invalid signature profile");
        RequestValidator.validateRemoteSigning("K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols", "B_BES");
    }

    @Test
    public void invalidSignatureProfile() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("Invalid signature profile");
        RequestValidator.validateRemoteSigning("K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols", "TL");
    }

    @Test
    public void emptySignatureProfile() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("Invalid signature profile");
        RequestValidator.validateRemoteSigning("K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols", "");
    }

    @Test
    public void successfulSignatureValue() {
        RequestValidator.validateSignatureValue("K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols");
    }

    @Test
    public void emptySignatureValue() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("Invalid signature value");
        RequestValidator.validateSignatureValue("");
    }

    @Test
    public void invalidSignatureValue() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("Invalid signature value");
        RequestValidator.validateSignatureValue("+=?!%");
    }

    @Test
    public void successfulMobileInformation() {
        RequestValidator.validateMobileIdInformation(getMobileInformationRequest());
    }

    @Test
    public void nullLanguage() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("Invalid Mobile-Id language");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setLanguage(null);
        RequestValidator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void emptyLanguage() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("Invalid Mobile-Id language");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setLanguage("");
        RequestValidator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void invalidLanguage() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("Invalid Mobile-Id language");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setLanguage("ESTO");
        RequestValidator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void invalidMessageToDisplay() {
        exceptionRule.expect(InvalidRequestException.class);
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
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("Invalid phone No.");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setPhoneNo(null);
        RequestValidator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void emptyPhoneNo() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("Invalid phone No.");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setPhoneNo("");
        RequestValidator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void invalidPhoneNo() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("Invalid phone No.");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setPhoneNo(StringUtils.repeat("a", 21));
        RequestValidator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void nullPersonIdentifier() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("Invalid person identifier");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setPersonIdentifier(null);
        RequestValidator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void emptyPersonIdentifier() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("Invalid person identifier");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setPersonIdentifier("");
        RequestValidator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void invalidPersonIdentifier() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("Invalid person identifier");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setPersonIdentifier(StringUtils.repeat("a", 31));
        RequestValidator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void nullCountry() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("Invalid country of origin");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setCountry(null);
        RequestValidator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void emptyCountry() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("Invalid country of origin");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setCountry("");
        RequestValidator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void invalidCountry() {
        exceptionRule.expect(InvalidRequestException.class);
        exceptionRule.expectMessage("Invalid country of origin");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setCountry("EST");
        RequestValidator.validateMobileIdInformation(mobileIdInformation);
    }

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
        dataFile.setFileHashSha256("K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols");
        dataFile.setFileHashSha512("vSsar3708Jvp9Szi2NWZZ02Bqp1qRCFpbcTZPdBhnWgs5WtNZKnvCXdhztmeD2cmW192CF5bDufKRpayrW/isg");
        request.getDataFiles().add(dataFile);
        return request;
    }
}
