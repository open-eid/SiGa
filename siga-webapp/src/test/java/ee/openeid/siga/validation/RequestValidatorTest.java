package ee.openeid.siga.validation;

import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.common.exception.RequestValidationException;
import ee.openeid.siga.common.model.MobileIdInformation;
import ee.openeid.siga.common.model.SmartIdInformation;
import ee.openeid.siga.webapp.json.CreateContainerRequest;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerRequest;
import ee.openeid.siga.webapp.json.DataFile;
import ee.openeid.siga.webapp.json.HashcodeDataFile;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static java.lang.String.valueOf;
import static java.time.Instant.now;

@RunWith(MockitoJUnitRunner.class)
@TestPropertySource(locations = "application-test.properties")

public class RequestValidatorTest {

    public static final String CONTENT = "dGVzdCBmaWxlIGNvbnRlbnQ=";

    @InjectMocks
    private RequestValidator validator;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setup() {
        List<String> allowedCountries = new ArrayList<>(Arrays.asList("EE", "LT"));
        validator = new RequestValidator(allowedCountries, allowedCountries);
    }

    private static MobileIdInformation getMobileInformationRequest() {
        return MobileIdInformation.builder()
                .relyingPartyName("Testimiseks")
                .phoneNo("+37253410832")
                .personIdentifier("3489348234")
                .language("EST")
                .messageToDisplay("Random display").build();
    }

    private static CreateHashcodeContainerRequest getCreateHashcodeContainerRequest() {
        CreateHashcodeContainerRequest request = new CreateHashcodeContainerRequest();
        HashcodeDataFile dataFile = new HashcodeDataFile();
        dataFile.setFileName("first datafile.txt");
        dataFile.setFileSize(6);
        dataFile.setFileHashSha256("VKZIO4rKVcnfKjW69x2ZZd39YjRo2B1RIpvV630eHBs=");
        dataFile.setFileHashSha512("hIVQtdcSnvLY9JK3VnZkKrJ41s1fHYFqzpiNFY4ZlkVeXiPL5Nu7Kd/cVXYEBuME26QIeI2q6gI7OjLIbl9SUw==");
        request.getDataFiles().add(dataFile);
        return request;
    }

    private static CreateContainerRequest getCreateContainerRequest() {
        CreateContainerRequest request = new CreateContainerRequest();
        request.setContainerName("container.asice");
        DataFile dataFile = new DataFile();
        dataFile.setFileName("first datafile.txt");
        dataFile.setFileContent("VKZIO4rKVcnfKjW69x2ZZd39YjRo2B1RIpvV630eHBs=");
        request.getDataFiles().add(dataFile);
        return request;
    }

    @Test
    public void successfulCreateContainerHashcodeRequest() {
        Authentication authentication = Mockito.mock(Authentication.class);
        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        Mockito.when(authentication.getPrincipal()).thenReturn(SigaUserDetails.builder().build());
        Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        validator.validateHashcodeDataFiles(getCreateHashcodeContainerRequest().getDataFiles());
    }

    @Test
    public void successfulCreateContainerRequest() {
        validator.validateDataFiles(getCreateContainerRequest().getDataFiles());
    }

    @Test
    public void successfulFileContent() {
        validator.validateFileContent(CONTENT);
    }

    @Test
    public void containerContentEmpty() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("File content is invalid");
        validator.validateFileContent("");
    }

    @Test
    public void containerContentNotBase64() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("File content is invalid");
        validator.validateFileContent("?&%");
    }

    @Test
    public void createHashcodeContainer_NoDataFiles() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Must be at least one data file in request");
        CreateHashcodeContainerRequest request = getCreateHashcodeContainerRequest();
        request.getDataFiles().clear();
        validator.validateHashcodeDataFiles(request.getDataFiles());
    }

    @Test
    public void createContainer_NoDataFiles() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Must be at least one data file in request");
        CreateContainerRequest request = getCreateContainerRequest();
        request.getDataFiles().clear();
        validator.validateDataFiles(request.getDataFiles());
    }

    @Test
    public void createHashcodeContainer_DataFileContentIsEmpty() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Data file name is invalid");
        CreateHashcodeContainerRequest request = getCreateHashcodeContainerRequest();
        request.getDataFiles().clear();
        request.getDataFiles().add(new HashcodeDataFile());
        validator.validateHashcodeDataFiles(request.getDataFiles());
    }

    @Test
    public void createContainer_DataFileContentIsEmpty() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Data file name is invalid");
        CreateContainerRequest request = getCreateContainerRequest();
        request.getDataFiles().clear();
        request.getDataFiles().add(new DataFile());
        validator.validateDataFiles(request.getDataFiles());
    }

    @Test
    public void createHashcodeContainer_DataFileNameInvalid() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Data file name is invalid");
        CreateHashcodeContainerRequest request = getCreateHashcodeContainerRequest();
        request.getDataFiles().add(new HashcodeDataFile());
        request.getDataFiles().get(0).setFileName("*/random.txt");
        validator.validateHashcodeDataFiles(request.getDataFiles());
    }

    @Test
    public void createContainer_DataFileNameInvalid() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Data file name is invalid");
        CreateContainerRequest request = getCreateContainerRequest();
        request.getDataFiles().add(new DataFile());
        request.getDataFiles().get(0).setFileName("*/random.txt");
        validator.validateDataFiles(request.getDataFiles());
    }

    @Test
    public void createHashcodeContainer_DataFileHashIsNotBase64() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Base64 content is invalid");
        CreateHashcodeContainerRequest request = getCreateHashcodeContainerRequest();
        request.getDataFiles().get(0).setFileHashSha256(StringUtils.repeat("a", 101));
        validator.validateHashcodeDataFiles(request.getDataFiles());
    }

    @Test
    public void createContainer_DataFileHashIsNotBase64() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Base64 content is invalid");
        CreateContainerRequest request = getCreateContainerRequest();
        request.getDataFiles().get(0).setFileContent(StringUtils.repeat("a", 101));
        validator.validateDataFiles(request.getDataFiles());
    }

    @Test
    public void createContainer_DataFileHashTooLong() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Base64 content is invalid");
        CreateHashcodeContainerRequest request = getCreateHashcodeContainerRequest();
        request.getDataFiles().get(0).setFileHashSha256("+=?!%");
        validator.validateHashcodeDataFiles(request.getDataFiles());
    }

    @Test
    public void containerIdIsNull() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Container Id is invalid");
        validator.validateContainerId(null);
    }

    @Test
    public void containerIdIsEmpty() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Container Id is invalid");
        validator.validateContainerId("");
    }

    @Test
    public void containerIdIsTooLong() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Container Id is invalid");
        validator.validateContainerId(StringUtils.repeat("a", 37));
    }

    @Test
    public void successfulRemoteSigningWithBase64Certificate() {
        validator.validateRemoteSigning("dGVzdCBoYXNo", "LT");
    }

    @Test
    public void successfulRemoteSigningWithHexCertificate() {
        validator.validateRemoteSigning("1237ABCDEF", "LT");
    }

    @Test
    public void invalidSigningCertificate() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid signing certificate");
        validator.validateRemoteSigning("+=?!%", "LT");
    }

    @Test
    public void emptySigningCertificate() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid signing certificate");
        validator.validateRemoteSigning("", "LT");
    }

    @Test
    public void oldSignatureProfile() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid signature profile");
        validator.validateRemoteSigning("dGVzdCBoYXNo", "B_BES");
    }

    @Test
    public void invalidSignatureProfile() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid signature profile");
        validator.validateRemoteSigning("dGVzdCBoYXNo", "TL");
    }

    @Test
    public void emptySignatureProfile() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid signature profile");
        validator.validateRemoteSigning("dGVzdCBoYXNo", "");
    }

    @Test
    public void successfulSignatureValue() {
        validator.validateSignatureValue("dGVzdCBoYXNo");
    }

    @Test
    public void emptySignatureValue() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid signature value");
        validator.validateSignatureValue("");
    }

    @Test
    public void invalidSignatureValue() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid signature value");
        validator.validateSignatureValue("+=?!%");
    }

    @Test
    public void successfulMobileInformation() {
        validator.validateMobileIdInformation(getMobileInformationRequest());
    }

    @Test
    public void nullLanguage() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid Mobile-Id language");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setLanguage(null);
        validator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void emptyLanguage() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid Mobile-Id language");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setLanguage("");
        validator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void invalidLanguage() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid Mobile-Id language");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setLanguage("ESTO");
        validator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void invalidMessageToDisplay() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid Mobile-Id message to display");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setMessageToDisplay(StringUtils.repeat("a", 41));
        validator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void nullMessageToDisplay() {
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setMessageToDisplay(null);
        validator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void validatePhoneNo_nullIsInvalid() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid phone No.");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setPhoneNo(null);
        validator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void validatePhoneNo_emptyIsInvalid() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid phone No.");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setPhoneNo("");
        validator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void validatePhoneNo_invalidFormat() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid phone No.");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setPhoneNo(StringUtils.repeat("a", 21));
        validator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void validatePhoneNo_invalidCountryPrefix() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid phone No.");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setPhoneNo("+3795394823");
        validator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void validatePhoneNo_notAllowedCountryPrefix() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid phone No.");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setPhoneNo("+3715394823");
        validator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void nullPersonIdentifier() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid person identifier");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setPersonIdentifier(null);
        validator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void emptyPersonIdentifier() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid person identifier");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setPersonIdentifier("");
        validator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void invalidPersonIdentifier() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid person identifier");
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setPersonIdentifier(StringUtils.repeat("a", 31));
        validator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    public void validateRoles_nullIsValid() {
        validator.validateRoles(null);
    }

    @Test
    public void validateRoles_emptyListIsValid() {
        validator.validateRoles(new ArrayList<>());
    }

    @Test
    public void validateRoles_listWithValuesIsValid() {
        validator.validateRoles(Arrays.asList("role1", "role2"));
    }

    @Test
    public void validateRoles_includingNullValueIsInvalid() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Roles may not include blank values");
        validator.validateRoles(Collections.singletonList(null));
    }

    @Test
    public void validateRoles_includingEmptyValueIsInvalid() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Roles may not include blank values");
        validator.validateRoles(Collections.singletonList(""));
    }

    @Test
    public void validateRoles_includingBlankValueIsInvalid() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Roles may not include blank values");
        validator.validateRoles(Collections.singletonList(" "));
    }

    @Test
    public void validateCertificateId_isValid() {
        validator.validateCertificateId(UUID.randomUUID().toString());
    }

    @Test
    public void validateCertificateId_nullIsInvalid() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Certificate Id is invalid");
        validator.validateCertificateId(null);
    }

    @Test
    public void validateCertificateId_emptyStringIsInvalid() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Certificate Id is invalid");
        validator.validateCertificateId("");
    }

    @Test
    public void validateCertificateId_tooLongStringIsInvalid() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Certificate Id is invalid");
        validator.validateCertificateId(UUID.randomUUID().toString() + "a");
    }

    @Test
    public void validateSmartIdInformationForCertChoice_isValid() {
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        validator.validateSmartIdInformationForCertChoice(smartIdInformation);
    }

    @Test
    public void validateSmartIdInformationForCertChoice_nullCountryIsInvalid() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid Smart-Id country");
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        smartIdInformation.setCountry(null);
        validator.validateSmartIdInformationForCertChoice(smartIdInformation);
    }

    @Test
    public void validateSmartIdInformationForCertChoice_tooShortCountryIsInvalid() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid Smart-Id country");
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        smartIdInformation.setCountry("E");
        validator.validateSmartIdInformationForCertChoice(smartIdInformation);
    }

    @Test
    public void validateSmartIdInformationForCertChoice_countryIsNotInAllowedList() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid Smart-Id country");
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        smartIdInformation.setCountry("LV");
        validator.validateSmartIdInformationForCertChoice(smartIdInformation);
    }

    @Test
    public void validateSmartIdInformationForCertChoice_tooLongCountryIsInvalid() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid Smart-Id country");
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        smartIdInformation.setCountry("EEE");
        validator.validateSmartIdInformationForCertChoice(smartIdInformation);
    }

    @Test
    public void validateSmartIdInformationForCertChoice_nullPersonIdentifierIsInvalid() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid person identifier");
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        smartIdInformation.setPersonIdentifier(null);
        validator.validateSmartIdInformationForCertChoice(smartIdInformation);
    }

    @Test
    public void validateSmartIdInformationForCertChoice_tooLongPersonIdentifierIsInvalid() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid person identifier");
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        smartIdInformation.setPersonIdentifier(StringUtils.repeat("a", 31));
        validator.validateSmartIdInformationForCertChoice(smartIdInformation);
    }

    @Test
    public void validateSmartIdInformationForCertChoice_emptyPersonIdentifierIsInvalid() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid person identifier");
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        smartIdInformation.setPersonIdentifier("");
        validator.validateSmartIdInformationForCertChoice(smartIdInformation);
    }

    @Test
    public void validateSmartIdInformationForSigning_isValid() {
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        validator.validateSmartIdInformationForSigning(smartIdInformation);
    }

    @Test
    public void validateSmartIdInformationForSigning_tooLongMessageIsInvalid() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid Smart-Id message to display");
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        smartIdInformation.setMessageToDisplay(StringUtils.repeat("a", 61));
        validator.validateSmartIdInformationForSigning(smartIdInformation);
    }

    @Test
    public void validateSmartIdInformationForSigning_nullDocumentNumberIsInvalid() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid Smart-Id documentNumber");
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        smartIdInformation.setDocumentNumber(null);
        validator.validateSmartIdInformationForSigning(smartIdInformation);
    }

    @Test
    public void validateSmartIdInformationForSigning_tooLongDocumentNumberIsInvalid() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid Smart-Id documentNumber");
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        smartIdInformation.setDocumentNumber(StringUtils.repeat("a", 41));
        validator.validateSmartIdInformationForSigning(smartIdInformation);
    }

    @Test
    public void validateSmartIdInformationForSigning_emptyDocumentNumberIsInvalid() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid Smart-Id documentNumber");
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        smartIdInformation.setDocumentNumber("");
        validator.validateSmartIdInformationForSigning(smartIdInformation);
    }

    @Test
    public void validateSmartIdInformationForSigning_tooShortDocumentNumberIsInvalid() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid Smart-Id documentNumber");
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        smartIdInformation.setDocumentNumber("PNOEE-123");
        validator.validateSmartIdInformationForSigning(smartIdInformation);
    }

    @Test
    public void validateSmartIdInformationForSigning_documentNumberNotStartingPNOPrefix() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid Smart-Id documentNumber");
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        smartIdInformation.setDocumentNumber("PNKEE-12345678");
        validator.validateSmartIdInformationForSigning(smartIdInformation);
    }

    @Test
    public void validateSmartIdInformationForSigning_documentNumberNotAllowedCountryList() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid Smart-Id country inside documentNumber");
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        smartIdInformation.setDocumentNumber("PNOLV-12345678");
        validator.validateSmartIdInformationForSigning(smartIdInformation);
    }

    @Test
    public void validateSmartIdInformationForSigning_invalidCountry() {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage("Invalid Smart-Id country inside documentNumber");
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        smartIdInformation.setDocumentNumber("PNOWW-12345678");
        validator.validateSmartIdInformationForSigning(smartIdInformation);
    }

    private SmartIdInformation getDefaultSmartIdInformation() {
        return SmartIdInformation.builder()
                .country("EE")
                .messageToDisplay("test message")
                .documentNumber("PNOEE-12345678912-QRTS")
                .personIdentifier("12345678912")
                .relyingPartyName("name")
                .relyingPartyUuid("uuid")
                .build();
    }
}
