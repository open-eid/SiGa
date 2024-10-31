package ee.openeid.siga.validation;

import ee.openeid.siga.auth.properties.SecurityConfigurationProperties;
import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.common.exception.InvalidCertificateException;
import ee.openeid.siga.common.exception.RequestValidationException;
import ee.openeid.siga.common.model.MobileIdInformation;
import ee.openeid.siga.common.model.SmartIdInformation;
import ee.openeid.siga.common.util.CertificateUtil;
import ee.openeid.siga.service.signature.configuration.MobileIdClientConfigurationProperties;
import ee.openeid.siga.service.signature.configuration.SmartIdClientConfigurationProperties;
import ee.openeid.siga.webapp.json.CreateContainerRequest;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerRequest;
import ee.openeid.siga.webapp.json.DataFile;
import ee.openeid.siga.webapp.json.HashcodeDataFile;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


@ExtendWith(MockitoExtension.class)
@TestPropertySource(locations = "application-test.properties")
class RequestValidatorTest {

    static final String CONTENT = "dGVzdCBmaWxlIGNvbnRlbnQ=";

    @InjectMocks
    private RequestValidator validator;

    @Mock
    private SmartIdClientConfigurationProperties smartIdClientConfigurationProperties;

    @Mock
    private MobileIdClientConfigurationProperties mobileIdClientConfigurationProperties;

    @Mock
    private SecurityConfigurationProperties securityConfigurationProperties;

    @BeforeEach
    void setup() {
        Mockito.lenient().when(mobileIdClientConfigurationProperties.getAllowedCountries()).thenReturn(Arrays.asList("EE", "LT"));
        Mockito.lenient().when(smartIdClientConfigurationProperties.getAllowedCountries()).thenReturn(Arrays.asList("EE", "LT"));
        validator = new RequestValidator(mobileIdClientConfigurationProperties, smartIdClientConfigurationProperties, securityConfigurationProperties);
    }

    private static MobileIdInformation getMobileInformationRequest() {
        return MobileIdInformation.builder()
                .phoneNo("+37253410832")
                .personIdentifier("34893482349")
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
    void successfulCreateContainerHashcodeRequest() {
        Authentication authentication = Mockito.mock(Authentication.class);
        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        Mockito.when(authentication.getPrincipal()).thenReturn(SigaUserDetails.builder().build());
        Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        validator.validateHashcodeDataFiles(getCreateHashcodeContainerRequest().getDataFiles());
    }

    @Test
    void successfulCreateContainerRequest() {
        validator.validateDataFiles(getCreateContainerRequest().getDataFiles());
    }

    @Test
    void successfulFileContent() {
        validator.validateFileContent(CONTENT);
    }

    @Test
    void containerContentEmpty() {
        RequestValidationException caughtException = assertThrows(
                RequestValidationException.class,
                () -> validator.validateFileContent("")
        );
        assertEquals("File content is invalid", caughtException.getMessage());
    }

    @Test
    void containerContentNotBase64() {
        RequestValidationException caughtException = assertThrows(
                RequestValidationException.class,
                () -> validator.validateFileContent("?&%")
        );
        assertEquals("File content is invalid", caughtException.getMessage());
    }

    @Test
    void createHashcodeContainer_NoDataFiles() {
        CreateHashcodeContainerRequest request = getCreateHashcodeContainerRequest();
        request.getDataFiles().clear();

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateHashcodeDataFiles(request.getDataFiles())
        );
        assertEquals("Must be at least one data file in request", caughtException.getMessage());

    }

    @Test
    void createContainer_NoDataFiles() {
        CreateContainerRequest request = getCreateContainerRequest();
        request.getDataFiles().clear();

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateDataFiles(request.getDataFiles())
        );
        assertEquals("Must be at least one data file in request", caughtException.getMessage());
    }

    @Test
    void createHashcodeContainer_DataFileContentIsEmpty() {
        CreateHashcodeContainerRequest request = getCreateHashcodeContainerRequest();
        request.getDataFiles().clear();
        request.getDataFiles().add(new HashcodeDataFile());

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateHashcodeDataFiles(request.getDataFiles())
        );
        assertEquals("Data file name is invalid", caughtException.getMessage());

    }

    @Test
    void createContainer_DataFileContentIsEmpty() {
        CreateContainerRequest request = getCreateContainerRequest();
        request.getDataFiles().clear();
        request.getDataFiles().add(new DataFile());

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateDataFiles(request.getDataFiles())
        );
        assertEquals("Data file name is invalid", caughtException.getMessage());

    }

    @Test
    void createHashcodeContainer_DataFileNameInvalid() {
        CreateHashcodeContainerRequest request = getCreateHashcodeContainerRequest();
        request.getDataFiles().add(new HashcodeDataFile());
        request.getDataFiles().get(0).setFileName("*/random.txt");

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateHashcodeDataFiles(request.getDataFiles())
        );
        assertEquals("Data file name is invalid", caughtException.getMessage());

    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "`", "?", "*", "\\", "<", ">", "|", "\"", ":", "\u0017", "\u0000"})
    void createContainer_DataFileNameContainsInvalidCharacters(String invalidCharacter) {
        CreateContainerRequest request = getCreateContainerRequest();
        request.getDataFiles().clear();
        request.getDataFiles().add(new DataFile());
        request.getDataFiles().get(0).setFileName("filename" + invalidCharacter + ".txt");

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateDataFiles(request.getDataFiles())
        );
        assertEquals("Data file name is invalid", caughtException.getMessage());
    }

    @Test
    void createHashcodeContainer_DataFileHashIsNotBase64() {
        CreateHashcodeContainerRequest request = getCreateHashcodeContainerRequest();
        request.getDataFiles().get(0).setFileHashSha256(StringUtils.repeat("a", 101));

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateHashcodeDataFiles(request.getDataFiles())
        );
        assertEquals("Base64 content is invalid", caughtException.getMessage());
    }

    @Test
    void createContainer_DataFileHashIsNotBase64() {
        CreateContainerRequest request = getCreateContainerRequest();
        request.getDataFiles().get(0).setFileContent(StringUtils.repeat("a", 101));

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateDataFiles(request.getDataFiles())
        );
        assertEquals("Base64 content is invalid", caughtException.getMessage());
    }

    @Test
    void createContainer_DataFileHashTooLong() {
        CreateHashcodeContainerRequest request = getCreateHashcodeContainerRequest();
        request.getDataFiles().get(0).setFileHashSha256("+=?!%");

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateHashcodeDataFiles(request.getDataFiles())
        );
        assertEquals("Base64 content is invalid", caughtException.getMessage());
    }

    @Test
    void containerIdIsNull() {
        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateContainerId(null)
        );
        assertEquals("Container Id is invalid", caughtException.getMessage());
    }

    @Test
    void containerIdIsEmpty() {
        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateContainerId("")
        );
        assertEquals("Container Id is invalid", caughtException.getMessage());
    }

    @Test
    void containerIdIsTooLong() {
        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateContainerId(StringUtils.repeat("a", 37))
        );
        assertEquals("Container Id is invalid", caughtException.getMessage());
    }

    @Test
    void validSigningCertificateWithBase64Certificate() {
        validator.validateSigningCertificate("dGVzdCBoYXNo");
    }

    @Test
    void validSigningCertificateWithHexCertificate() {
        validator.validateSigningCertificate("1237ABCDEF");
    }

    @Test
    void invalidSigningCertificate() {
        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateSigningCertificate("+=?!%")
        );
        assertEquals("Invalid signing certificate", caughtException.getMessage());
    }

    @Test
    void emptySigningCertificate() {
        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateSigningCertificate("")
        );
        assertEquals("Invalid signing certificate", caughtException.getMessage());
    }

    @Test
    void remoteSigning_authCertificateNotAllowed() throws IOException {
        Path documentPath = Paths.get(new ClassPathResource("mari-liis_auth.cer").getURI());
        InputStream inputStream = new ByteArrayInputStream(Files.readAllBytes(documentPath));

        InvalidCertificateException caughtException = assertThrows(
            InvalidCertificateException.class, () -> validator.validateRemoteSigning(CertificateUtil.createX509Certificate(Base64.getDecoder().decode(inputStream.readAllBytes())))
        );
        assertEquals("Invalid signing certificate", caughtException.getMessage());
    }

    @Test
    void remoteSigning_smartIdCertificateNotAllowed() throws IOException {
        Mockito.when(securityConfigurationProperties.getProhibitedPoliciesForRemoteSigning()).thenReturn(Arrays.asList("1.3.6.1.4.1.10015.3.17.2", "1.3.6.1.4.1.10015.3.1.3"));
        X509Certificate certificate = readCertificate("smart-id.cer");

        InvalidCertificateException caughtException = assertThrows(
            InvalidCertificateException.class, () -> validator.validateRemoteSigning(certificate)
        );
        assertEquals("Remote signing endpoint prohibits signing with Mobile-Id/Smart-Id certificate", caughtException.getMessage());
    }

    @Test
    void remoteSigning_mobileIdCertificateNotAllowed() throws IOException {
        Mockito.when(securityConfigurationProperties.getProhibitedPoliciesForRemoteSigning()).thenReturn(Arrays.asList("1.3.6.1.4.1.10015.3.1.3"));
        X509Certificate certificate = readCertificate("mobile-id.cer");

        InvalidCertificateException caughtException = assertThrows(
            InvalidCertificateException.class, () -> validator.validateRemoteSigning(certificate)
        );
        assertEquals("Remote signing endpoint prohibits signing with Mobile-Id/Smart-Id certificate", caughtException.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "123", "@!*", "UNKNOWN", "B_BES", "B_EPES", "LT_TM", "T"})
    void invalidSignatureProfileForDatafileEndpoint(String signatureProfile) {
        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateSignatureProfileForDatafileRequest(signatureProfile)
        );
        assertEquals("Invalid signature profile", caughtException.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "123", "@!*", "UNKNOWN", "B_BES", "B_EPES", "LT_TM", "T", "LTA"})
    void invalidSignatureProfileForHashcodeEndpoint(String signatureProfile) {
        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateSignatureProfileForHashcodeRequest(signatureProfile)
        );
        assertEquals("Invalid signature profile", caughtException.getMessage());
    }

    @Test
    void successfulSignatureValue() {
        validator.validateSignatureValue("dGVzdCBoYXNo");
    }

    @Test
    void emptySignatureValue() {
        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateSignatureValue("")
        );
        assertEquals("Invalid signature value", caughtException.getMessage());
    }

    @Test
    void invalidSignatureValue() {
        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateSignatureValue("+=?!%")
        );
        assertEquals("Invalid signature value", caughtException.getMessage());
    }

    @Test
    void successfulMobileInformation() {
        validator.validateMobileIdInformation(getMobileInformationRequest());
    }

    @Test
    void nullLanguage() {
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setLanguage(null);

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateMobileIdInformation(mobileIdInformation)
        );
        assertEquals("Invalid Mobile-Id language", caughtException.getMessage());
    }

    @Test
    void emptyLanguage() {
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setLanguage("");

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateMobileIdInformation(mobileIdInformation)
        );
        assertEquals("Invalid Mobile-Id language", caughtException.getMessage());
    }

    @Test
    void invalidLanguage() {
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setLanguage("ESTO");

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateMobileIdInformation(mobileIdInformation)
        );
        assertEquals("Invalid Mobile-Id language", caughtException.getMessage());
    }

    @Test
    void languageNotInTheAllowedList() {
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setLanguage("ESP");

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateMobileIdInformation(mobileIdInformation)
        );
        assertEquals("Invalid Mobile-Id language", caughtException.getMessage());
    }

    @Test
    void invalidMessageToDisplay() {
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setMessageToDisplay(StringUtils.repeat("a", 41));

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateMobileIdInformation(mobileIdInformation)
        );
        assertEquals("Invalid Mobile-Id message to display", caughtException.getMessage());
    }

    @Test
    void nullMessageToDisplay() {
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setMessageToDisplay(null);
        validator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    void validatePhoneNo_nullIsInvalid() {
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setPhoneNo(null);

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateMobileIdInformation(mobileIdInformation)
        );
        assertEquals("Invalid phone No.", caughtException.getMessage());
    }

    @Test
    void validatePhoneNo_emptyIsInvalid() {
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setPhoneNo("");

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateMobileIdInformation(mobileIdInformation)
        );
        assertEquals("Invalid phone No.", caughtException.getMessage());
    }

    @Test
    void validatePhoneNo_invalidFormat() {
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setPhoneNo(StringUtils.repeat("a", 21));

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateMobileIdInformation(mobileIdInformation)
        );
        assertEquals("Invalid phone No.", caughtException.getMessage());
    }

    @Test
    void validatePhoneNo_invalidCountryPrefix() {
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setPhoneNo("+3795394823");

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateMobileIdInformation(mobileIdInformation)
        );
        assertEquals("Invalid international calling code", caughtException.getMessage());
    }

    @Test
    void validatePhoneNo_notAllowedCountryPrefix() {
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setPhoneNo("+3715394823");

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateMobileIdInformation(mobileIdInformation)
        );
        assertEquals("Invalid international calling code", caughtException.getMessage());
    }

    @Test
    void nullPersonIdentifier() {
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setPersonIdentifier(null);

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateMobileIdInformation(mobileIdInformation)
        );
        assertEquals("Invalid person identifier", caughtException.getMessage());
    }

    @Test
    void emptyPersonIdentifier() {
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setPersonIdentifier("");

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateMobileIdInformation(mobileIdInformation)
        );
        assertEquals("Invalid person identifier", caughtException.getMessage());
    }

    @Test
    void invalidPersonIdentifierTooLong() {
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setPersonIdentifier(StringUtils.repeat("1", 13));

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateMobileIdInformation(mobileIdInformation)
        );
        assertEquals("Invalid person identifier", caughtException.getMessage());
    }

    @Test
    void invalidPersonIdentifierContainsLetter() {
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setPersonIdentifier(StringUtils.repeat("a", 11));

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateMobileIdInformation(mobileIdInformation)
        );
        assertEquals("Invalid person identifier", caughtException.getMessage());
    }

    @Test
    void latvianPersonIdentifier() {
        MobileIdInformation mobileIdInformation = getMobileInformationRequest();
        mobileIdInformation.setPersonIdentifier("111111-11111");
        validator.validateMobileIdInformation(mobileIdInformation);
    }

    @Test
    void validateRoles_nullIsValid() {
        validator.validateRoles(null);
    }

    @Test
    void validateRoles_emptyListIsValid() {
        validator.validateRoles(new ArrayList<>());
    }

    @Test
    void validateRoles_listWithValuesIsValid() {
        validator.validateRoles(Arrays.asList("role1", "role2"));
    }

    @Test
    void validateRoles_includingNullValueIsInvalid() {
        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateRoles(Collections.singletonList(null))
        );
        assertEquals("Roles may not include blank values", caughtException.getMessage());
    }

    @Test
    void validateRoles_includingEmptyValueIsInvalid() {
        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateRoles(Collections.singletonList(""))
        );
        assertEquals("Roles may not include blank values", caughtException.getMessage());
    }

    @Test
    void validateRoles_includingBlankValueIsInvalid() {
        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateRoles(Collections.singletonList(" "))
        );
        assertEquals("Roles may not include blank values", caughtException.getMessage());
    }

    @Test
    void validateCertificateId_isValid() {
        validator.validateCertificateId(UUID.randomUUID().toString());
    }

    @Test
    void validateCertificateId_nullIsInvalid() {
        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateCertificateId(null)
        );
        assertEquals("Certificate Id is invalid", caughtException.getMessage());
    }

    @Test
    void validateCertificateId_emptyStringIsInvalid() {
        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateCertificateId("")
        );
        assertEquals("Certificate Id is invalid", caughtException.getMessage());
    }

    @Test
    void validateCertificateId_tooLongStringIsInvalid() {
        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateCertificateId(UUID.randomUUID().toString() + "a")
        );
        assertEquals("Certificate Id is invalid", caughtException.getMessage());
    }

    @Test
    void validateSmartIdInformationForCertChoice_isValid() {
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        validator.validateSmartIdInformationForCertChoice(smartIdInformation);
    }

    @Test
    void validateSmartIdInformationForCertChoice_nullCountryIsInvalid() {
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        smartIdInformation.setCountry(null);

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateSmartIdInformationForCertChoice(smartIdInformation)
        );
        assertEquals("Invalid Smart-Id country", caughtException.getMessage());
    }

    @Test
    void validateSmartIdInformationForCertChoice_tooShortCountryIsInvalid() {
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        smartIdInformation.setCountry("E");

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateSmartIdInformationForCertChoice(smartIdInformation)
        );
        assertEquals("Invalid Smart-Id country", caughtException.getMessage());
    }

    @Test
    void validateSmartIdInformationForCertChoice_countryIsNotInAllowedList() {
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        smartIdInformation.setCountry("LV");

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateSmartIdInformationForCertChoice(smartIdInformation)
        );
        assertEquals("LV is not allowed for Smart-Id country", caughtException.getMessage());
    }

    @Test
    void validateSmartIdInformationForCertChoice_tooLongCountryIsInvalid() {
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        smartIdInformation.setCountry("EEE");

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateSmartIdInformationForCertChoice(smartIdInformation)
        );
        assertEquals("Invalid Smart-Id country", caughtException.getMessage());
    }

    @Test
    void validateSmartIdInformationForCertChoice_nullPersonIdentifierIsInvalid() {
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        smartIdInformation.setPersonIdentifier(null);

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateSmartIdInformationForCertChoice(smartIdInformation)
        );
        assertEquals("Invalid person identifier", caughtException.getMessage());
    }

    @Test
    void validateSmartIdInformationForCertChoice_tooLongPersonIdentifierIsInvalid() {
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        smartIdInformation.setPersonIdentifier(StringUtils.repeat("a", 31));

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateSmartIdInformationForCertChoice(smartIdInformation)
        );
        assertEquals("Invalid person identifier", caughtException.getMessage());
    }

    @Test
    void validateSmartIdInformationForCertChoice_emptyPersonIdentifierIsInvalid() {
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        smartIdInformation.setPersonIdentifier("");

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateSmartIdInformationForCertChoice(smartIdInformation)
        );
        assertEquals("Invalid person identifier", caughtException.getMessage());
    }

    @Test
    void validateSmartIdInformationForSigning_isValid() {
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        validator.validateSmartIdInformationForSigning(smartIdInformation);
    }

    @Test
    void validateSmartIdInformationForSigning_tooLongMessageIsInvalid() {
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        smartIdInformation.setMessageToDisplay(StringUtils.repeat("a", 61));

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateSmartIdInformationForSigning(smartIdInformation)
        );
        assertEquals("Invalid Smart-Id message to display", caughtException.getMessage());
    }

    @Test
    void validateSmartIdInformationForSigning_nullDocumentNumberIsInvalid() {
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        smartIdInformation.setDocumentNumber(null);

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateSmartIdInformationForSigning(smartIdInformation)
        );
        assertEquals("Invalid Smart-Id documentNumber", caughtException.getMessage());
    }

    @Test
    void validateSmartIdInformationForSigning_tooLongDocumentNumberIsInvalid() {
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        smartIdInformation.setDocumentNumber(StringUtils.repeat("a", 41));

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateSmartIdInformationForSigning(smartIdInformation)
        );
        assertEquals("Invalid Smart-Id documentNumber", caughtException.getMessage());
    }

    @Test
    void validateSmartIdInformationForSigning_emptyDocumentNumberIsInvalid() {
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        smartIdInformation.setDocumentNumber("");

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateSmartIdInformationForSigning(smartIdInformation)
        );
        assertEquals("Invalid Smart-Id documentNumber", caughtException.getMessage());
    }

    @Test
    void validateSmartIdInformationForSigning_tooShortDocumentNumberIsInvalid() {
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        smartIdInformation.setDocumentNumber("PNOEE-123");

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateSmartIdInformationForSigning(smartIdInformation)
        );
        assertEquals("Invalid Smart-Id documentNumber", caughtException.getMessage());
    }

    @Test
    void validateSmartIdInformationForSigning_documentNumberNotStartingPNOPrefix() {
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        smartIdInformation.setDocumentNumber("PNKEE-12345678-ZOKS-Q");

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateSmartIdInformationForSigning(smartIdInformation)
        );
        assertEquals("Invalid Smart-Id documentNumber", caughtException.getMessage());
    }

    @Test
    void validateSmartIdInformationForSigning_documentNumberNotAllowedCountryList() {
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        smartIdInformation.setDocumentNumber("PNOLV-12345678-ZOKS-Q");

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateSmartIdInformationForSigning(smartIdInformation)
        );
        assertEquals("Invalid Smart-Id country inside documentNumber", caughtException.getMessage());
    }

    @Test
    void validateSmartIdInformationForSigning_invalidCountry() {
        SmartIdInformation smartIdInformation = getDefaultSmartIdInformation();
        smartIdInformation.setDocumentNumber("PNOWW-12345678-ZOKS-Q");

        RequestValidationException caughtException = assertThrows(
            RequestValidationException.class, () -> validator.validateSmartIdInformationForSigning(smartIdInformation)
        );
        assertEquals("Invalid Smart-Id country inside documentNumber", caughtException.getMessage());
    }

    private X509Certificate readCertificate(String fileName) throws IOException {
        Path documentPath = Paths.get(new ClassPathResource(fileName).getURI());
        InputStream inputStream = new ByteArrayInputStream(Files.readAllBytes(documentPath));
        return CertificateUtil.createX509Certificate(Base64.getDecoder().decode(inputStream.readAllBytes()));
    }

    private SmartIdInformation getDefaultSmartIdInformation() {
        return SmartIdInformation.builder()
                .country("EE")
                .messageToDisplay("test message")
                .documentNumber("PNOEE-12345678912-QRTS-Q")
                .personIdentifier("12345678912")
                .build();
    }
}
