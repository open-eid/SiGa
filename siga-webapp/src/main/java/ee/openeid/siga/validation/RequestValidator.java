package ee.openeid.siga.validation;

import ee.openeid.siga.auth.properties.SecurityConfigurationProperties;
import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.common.exception.InvalidCertificateException;
import ee.openeid.siga.common.exception.RequestValidationException;
import ee.openeid.siga.common.model.MobileIdInformation;
import ee.openeid.siga.common.model.ServiceType;
import ee.openeid.siga.common.model.SmartIdInformation;
import ee.openeid.siga.common.util.Base64Util;
import ee.openeid.siga.common.util.CertificateUtil;
import ee.openeid.siga.common.util.FileUtil;
import ee.openeid.siga.common.util.PhoneNumberUtil;
import ee.openeid.siga.service.signature.configuration.MobileIdClientConfigurationProperties;
import ee.openeid.siga.service.signature.configuration.SmartIdClientConfigurationProperties;
import ee.openeid.siga.util.SupportedCertificateEncoding;
import ee.openeid.siga.webapp.json.DataFile;
import ee.openeid.siga.webapp.json.HashcodeDataFile;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.digidoc4j.SignatureProfile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class RequestValidator {

    @NonNull
    private final MobileIdClientConfigurationProperties mobileIdClientConfigurationProperties;
    @NonNull
    private final SmartIdClientConfigurationProperties smartIdClientConfigurationProperties;
    @NonNull
    private final SecurityConfigurationProperties securityConfigurationProperties;

    private static final Pattern VALID_PERSON_IDENTIFIER_PATTERN = Pattern.compile("^([0-9]{11}|[0-9-]{12})$");
    private static final Pattern VALID_DOCUMENT_NUMBER = Pattern.compile("^(PNO)[A-Z]{2}-[0-9A-Z*\\-]{1,40}-[0-9A-Z]{4}-(NQ|Q)$");
    private static final String INVALID_DATA_FILE_NAME = "Data file name is invalid";
    private static final List<String> MOBILE_ID_LANGUAGES = Arrays.asList("EST", "ENG", "RUS", "LIT");

    public void validateHashcodeDataFiles(List<HashcodeDataFile> dataFiles) {
        if (CollectionUtils.isEmpty(dataFiles)) {
            throw new RequestValidationException("Must be at least one data file in request");
        }
        dataFiles.forEach(this::validateHashcodeDataFile);
    }

    public void validateDataFiles(List<DataFile> dataFiles) {
        if (CollectionUtils.isEmpty(dataFiles)) {
            throw new RequestValidationException("Must be at least one data file in request");
        }
        dataFiles.forEach(this::validateDataFile);
    }

    public void validateContainerName(String fileName) {
        validateFileName(fileName, "Container name is invalid");
    }

    public void validateContainerId(String containerId) {
        if (StringUtils.isBlank(containerId) || containerId.length() > 36) {
            throw new RequestValidationException("Container Id is invalid");
        }
    }

    public void validateSignatureId(String containerId) {
        if (StringUtils.isBlank(containerId) || containerId.length() > 36) {
            throw new RequestValidationException("Signature Id is invalid");
        }
    }

    public void validateCertificateId(String containerId) {
        if (StringUtils.isBlank(containerId) || containerId.length() > 36) {
            throw new RequestValidationException("Certificate Id is invalid");
        }
    }

    public void validateFileContent(String content) {
        if (StringUtils.isBlank(content) || isNotBase64StringEncoded(content)) {
            throw new RequestValidationException("File content is invalid");
        }
    }

    public void validateFileName(String fileName) {
        validateFileName(fileName, INVALID_DATA_FILE_NAME);
    }

    private void validateHashcodeDataFile(HashcodeDataFile dataFile) {
        validateFileName(dataFile.getFileName(), INVALID_DATA_FILE_NAME);
        validateFileSize(dataFile.getFileSize());
        validateHashSha256(dataFile.getFileHashSha256());
        SigaUserDetails sigaUserDetails = (SigaUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (ServiceType.REST == sigaUserDetails.getServiceType()) {
            validateHashSha512(dataFile.getFileHashSha512());
        }
    }

    public void validateDataFile(DataFile dataFile) {
        validateFileName(dataFile.getFileName(), INVALID_DATA_FILE_NAME);
        validateBase64(dataFile.getFileContent());
    }

    public void validateSigningCertificate(String signingCertificate) {
        if (StringUtils.isBlank(signingCertificate) || Stream.of(SupportedCertificateEncoding.values()).noneMatch(e -> e.isDecodable(signingCertificate))) {
            throw new RequestValidationException("Invalid signing certificate");
        }
    }

    public void validateRemoteSigning(X509Certificate signingCertificate, String signatureProfile) {
        validateSignatureProfile(signatureProfile);
        if (signingCertificate == null || !CertificateUtil.isCertificateActive(signingCertificate) || !CertificateUtil.isSigningCertificate(signingCertificate)) {
            throw new InvalidCertificateException("Invalid signing certificate");
        }

        if (CertificateUtil.hasProhibitedPolicies(signingCertificate, securityConfigurationProperties.getProhibitedPoliciesForRemoteSigning())) {
            throw new InvalidCertificateException("Remote signing endpoint prohibits signing with Mobile-Id/Smart-Id certificate");
        }
    }

    public void validateSignatureProfile(String signatureProfile) {
        SignatureProfile generatedSignatureProfile = SignatureProfile.findByProfile(signatureProfile);
        if (!(SignatureProfile.LT == generatedSignatureProfile || SignatureProfile.LT_TM == generatedSignatureProfile || SignatureProfile.LTA == generatedSignatureProfile)) {
            throw new RequestValidationException("Invalid signature profile");
        }
    }

    public void validateSignatureValue(String signatureValue) {
        if (StringUtils.isBlank(signatureValue) || isNotBase64StringEncoded(signatureValue)) {
            throw new RequestValidationException("Invalid signature value");
        }
    }

    public void validateMobileIdInformation(MobileIdInformation mobileIdInformation) {
        validateLanguage(mobileIdInformation.getLanguage());
        validateMessageToDisplay(mobileIdInformation.getMessageToDisplay());
        validatePhoneNo(mobileIdInformation.getPhoneNo());
        validatePersonIdentifier(mobileIdInformation.getPersonIdentifier());
    }

    public void validateSmartIdInformationForSigning(SmartIdInformation smartIdInformation) {
        validateDisplayText(smartIdInformation.getMessageToDisplay());
        validateDocumentNumber(smartIdInformation.getDocumentNumber());
    }

    public void validateSmartIdInformationForCertChoice(SmartIdInformation smartIdInformation) {
        validateCountry(smartIdInformation.getCountry(), smartIdClientConfigurationProperties.getAllowedCountries());
        validatePersonIdentifier(smartIdInformation.getPersonIdentifier());
    }

    public void validateRoles(List<String> roles) {
        if (CollectionUtils.isEmpty(roles)) {
            return;
        }

        if (roles.stream().anyMatch(StringUtils::isBlank)) {
            throw new RequestValidationException("Roles may not include blank values");
        }
    }

    private void validatePersonIdentifier(String personIdentifier) {
        if (StringUtils.isBlank(personIdentifier) || !VALID_PERSON_IDENTIFIER_PATTERN.matcher(personIdentifier).matches()) {
            throw new RequestValidationException("Invalid person identifier");
        }
    }

    private void validatePhoneNo(String phoneNo) {
        if (StringUtils.isBlank(phoneNo) || !PhoneNumberUtil.isPhoneNumberValid(phoneNo)) {
            throw new RequestValidationException("Invalid phone No.");
        }
        PhoneNumberUtil.CountryCallingCode countryNumber = PhoneNumberUtil.CountryCallingCode.getCountryByPrefix(phoneNo.substring(0, 4));
        if (countryNumber == null || !mobileIdClientConfigurationProperties.getAllowedCountries().contains(countryNumber.name())) {
            throw new RequestValidationException("Invalid international calling code");
        }
    }

    private void validateLanguage(String language) {
        if (!MOBILE_ID_LANGUAGES.contains(language)) {
            throw new RequestValidationException("Invalid Mobile-Id language");
        }
    }

    private void validateCountry(String country, List<String> allowedCountries) {
        if (country == null || country.length() != 2) {
            throw new RequestValidationException("Invalid Smart-Id country");
        }
        if (!allowedCountries.contains(country)) {
            throw new RequestValidationException(country + " is not allowed for Smart-Id country");
        }
    }

    private void validateMessageToDisplay(String messageToDisplay) {
        if (messageToDisplay != null && messageToDisplay.length() > 40) {
            throw new RequestValidationException("Invalid Mobile-Id message to display");
        }
    }

    private void validateDisplayText(String displayText) {
        if (displayText != null && displayText.length() > 60) {
            throw new RequestValidationException("Invalid Smart-Id message to display");
        }
    }

    private void validateDocumentNumber(String documentNumber) {
        if (StringUtils.isBlank(documentNumber) || !VALID_DOCUMENT_NUMBER.matcher(documentNumber).matches()) {
            throw new RequestValidationException("Invalid Smart-Id documentNumber");
        }
        String country = documentNumber.substring(3, 5);
        if (!smartIdClientConfigurationProperties.getAllowedCountries().contains(country)) {
            throw new RequestValidationException("Invalid Smart-Id country inside documentNumber");
        }
    }

    private void validateHashSha256(String hash) {
        validateBase64(hash);
        if (hash.length() != 44) {
            throw new RequestValidationException("File hash SHA256 length is invalid");
        }
    }

    private void validateHashSha512(String hash) {
        validateBase64(hash);
        if (hash.length() != 88) {
            throw new RequestValidationException("File hash SHA512 length is invalid");
        }
    }

    private void validateBase64(String hash) {
        if (StringUtils.isBlank(hash) || isNotBase64StringEncoded(hash)) {
            throw new RequestValidationException("Base64 content is invalid");
        }
    }

    private boolean isNotBase64StringEncoded(String base64String) {
        return !Base64Util.isValidBase64(base64String);
    }

    private void validateFileSize(Integer fileSize) {
        if (fileSize == null || fileSize < 1) {
            throw new RequestValidationException("File size is invalid");
        }
    }

    private void validateFileName(String fileName, String errorMessage) {
        if (StringUtils.isBlank(fileName)
                || fileName.length() > 260
                || !FileUtil.isFilenameValid(fileName)) {
            throw new RequestValidationException(errorMessage);
        }
    }

}
