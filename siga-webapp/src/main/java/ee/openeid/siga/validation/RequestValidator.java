package ee.openeid.siga.validation;

import ee.openeid.siga.common.MobileIdInformation;
import ee.openeid.siga.common.exception.InvalidRequestException;
import ee.openeid.siga.webapp.json.HashCodeDataFile;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.digidoc4j.SignatureProfile;

import java.util.List;

public class RequestValidator {

    public static void validateHashCodeDataFiles(List<HashCodeDataFile> dataFiles) {
        if (CollectionUtils.isEmpty(dataFiles)) {
            throw new InvalidRequestException("Data files are needed");
        }
        dataFiles.forEach(RequestValidator::validateHashCodeDataFile);
    }

    public static void validateContainerId(String containerId) {
        if (StringUtils.isBlank(containerId) || containerId.length() > 36) {
            throw new InvalidRequestException("Container Id is invalid");
        }
    }

    public static void validateFileContent(String content) {
        if (StringUtils.isBlank(content) || !isBase64StringEncoded(content)) {
            throw new InvalidRequestException("File content is invalid");
        }
    }

    private static void validateFileName(String fileName, String errorMessage) {
        if (StringUtils.isBlank(fileName) || fileName.length() < 1 || fileName.length() > 260) {
            throw new InvalidRequestException(errorMessage);
        }
    }

    private static void validateHashCodeDataFile(HashCodeDataFile dataFile) {
        validateFileName(dataFile.getFileName(), "Data file name is invalid");
        validateFileSize(dataFile.getFileSize());
        validateHash(dataFile.getFileHashSha256());
        validateHash(dataFile.getFileHashSha512());
    }

    private static void validateHash(String hash) {
        if (StringUtils.isBlank(hash) || !isBase64StringEncoded(hash) || hash.length() > 100) {
            throw new InvalidRequestException("File hash is invalid");
        }
    }

    private static boolean isBase64StringEncoded(String base64String) {
        return Base64.isBase64(base64String.getBytes());
    }

    private static void validateFileSize(Integer fileSize) {
        if (fileSize == null || fileSize < 1) {
            throw new InvalidRequestException("File size is invalid");
        }
    }

    public static void validateRemoteSigning(String signingCertificate, String signatureProfile) {
        if (StringUtils.isBlank(signingCertificate) || !isBase64StringEncoded(signingCertificate)) {
            throw new InvalidRequestException("Invalid signing certificate");
        }
        validateSignatureProfile(signatureProfile);
    }

    public static void validateSignatureProfile(String signatureProfile) {
        if (SignatureProfile.findByProfile(signatureProfile) == null) {
            throw new InvalidRequestException("Invalid signature profile");
        }
    }

    public static void validateSignatureValue(String signatureValue) {
        if (StringUtils.isBlank(signatureValue) || !isBase64StringEncoded(signatureValue)) {
            throw new InvalidRequestException("Invalid signature value");
        }
    }

    public static void validateMobileIdInformation(MobileIdInformation mobileIdInformation) {
        validateLanguage(mobileIdInformation.getLanguage());
        validateMessageToDisplay(mobileIdInformation.getMessageToDisplay());
        validatePhoneNo(mobileIdInformation.getPhoneNo());
        validatePersonIdentifier(mobileIdInformation.getPersonIdentifier());
        validateServiceName(mobileIdInformation.getServiceName());
        validateOriginCounty(mobileIdInformation.getCountry());

    }

    private static void validateOriginCounty(String country) {
        if (StringUtils.isBlank(country) || country.length() != 2) {
            throw new InvalidRequestException("Invalid country of origin");
        }
    }

    private static void validateServiceName(String serviceName) {
        if (StringUtils.isBlank(serviceName) || serviceName.length() > 20) {
            throw new InvalidRequestException("Invalid Mobile-Id service name");
        }
    }

    private static void validatePersonIdentifier(String personIdentifier) {
        if (StringUtils.isBlank(personIdentifier) || personIdentifier.length() > 30) {
            throw new InvalidRequestException("Invalid person identifier");
        }
    }

    private static void validatePhoneNo(String phoneNo) {
        if (StringUtils.isBlank(phoneNo) || phoneNo.length() > 20)
            throw new InvalidRequestException("Invalid phone No.");
    }

    private static void validateLanguage(String language) {
        if (StringUtils.isBlank(language) || language.length() != 3) {
            throw new InvalidRequestException("Invalid Mobile-Id language");
        }
    }

    private static void validateMessageToDisplay(String messageToDisplay) {
        if (messageToDisplay != null && messageToDisplay.length() > 40) {
            throw new InvalidRequestException("Invalid Mobile-Id message to display");
        }
    }


}
