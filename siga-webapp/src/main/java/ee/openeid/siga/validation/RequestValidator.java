package ee.openeid.siga.validation;

import ee.openeid.siga.common.Base64Util;
import ee.openeid.siga.common.FileUtil;
import ee.openeid.siga.common.MobileIdInformation;
import ee.openeid.siga.common.exception.RequestValidationException;
import ee.openeid.siga.webapp.json.HashcodeDataFile;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.digidoc4j.SignatureProfile;

import java.util.List;

public class RequestValidator {

    public static void validateHashcodeDataFiles(List<HashcodeDataFile> dataFiles) {
        if (CollectionUtils.isEmpty(dataFiles)) {
            throw new RequestValidationException("Data files are needed");
        }
        dataFiles.forEach(RequestValidator::validateHashcodeDataFile);
    }

    public static void validateContainerId(String containerId) {
        if (StringUtils.isBlank(containerId) || containerId.length() > 36) {
            throw new RequestValidationException("Container Id is invalid");
        }
    }

    public static void validateFileContent(String content) {
        if (StringUtils.isBlank(content) || isNotBase64StringEncoded(content)) {
            throw new RequestValidationException("File content is invalid");
        }
    }

    private static void validateFileName(String fileName, String errorMessage) {
        if (StringUtils.isBlank(fileName) || !FileUtil.isFilenameValid(fileName) || fileName.length() < 1 || fileName.length() > 260) {
            throw new RequestValidationException(errorMessage);
        }
    }

    private static void validateHashcodeDataFile(HashcodeDataFile dataFile) {
        validateFileName(dataFile.getFileName(), "Data file name is invalid");
        validateFileSize(dataFile.getFileSize());
        validateHashSha256(dataFile.getFileHashSha256());
        validateHashSha512(dataFile.getFileHashSha512());
    }

    private static void validateHashSha256(String hash) {
        validateHash(hash);
        if (hash.length() != 44) {
            throw new RequestValidationException("File hash SHA256 length is invalid");
        }
    }

    private static void validateHashSha512(String hash) {
        validateHash(hash);
        if (hash.length() != 88) {
            throw new RequestValidationException("File hash SHA512 length is invalid");
        }
    }

    private static void validateHash(String hash) {
        if (StringUtils.isBlank(hash) || isNotBase64StringEncoded(hash)) {
            throw new RequestValidationException("File hash is invalid");
        }
    }

    private static boolean isNotBase64StringEncoded(String base64String) {
        return !Base64Util.isValidBase64(base64String);
    }

    private static void validateFileSize(Integer fileSize) {
        if (fileSize == null || fileSize < 1) {
            throw new RequestValidationException("File size is invalid");
        }
    }

    public static void validateRemoteSigning(String signingCertificate, String signatureProfile) {
        if (StringUtils.isBlank(signingCertificate) || isNotBase64StringEncoded(signingCertificate)) {
            throw new RequestValidationException("Invalid signing certificate");
        }
        validateSignatureProfile(signatureProfile);
    }

    public static void validateSignatureProfile(String signatureProfile) {
        SignatureProfile generatedSignatureProfile = SignatureProfile.findByProfile(signatureProfile);
        if (!(SignatureProfile.LT == generatedSignatureProfile || SignatureProfile.LT_TM == generatedSignatureProfile || SignatureProfile.LTA == generatedSignatureProfile)) {
            throw new RequestValidationException("Invalid signature profile");
        }
    }

    public static void validateSignatureValue(String signatureValue) {
        if (StringUtils.isBlank(signatureValue) || isNotBase64StringEncoded(signatureValue)) {
            throw new RequestValidationException("Invalid signature value");
        }
    }

    public static void validateMobileIdInformation(MobileIdInformation mobileIdInformation) {
        validateLanguage(mobileIdInformation.getLanguage());
        validateMessageToDisplay(mobileIdInformation.getMessageToDisplay());
        validatePhoneNo(mobileIdInformation.getPhoneNo());
        validatePersonIdentifier(mobileIdInformation.getPersonIdentifier());
        validateOriginCounty(mobileIdInformation.getCountry());

    }

    private static void validateOriginCounty(String country) {
        if (StringUtils.isBlank(country) || country.length() != 2) {
            throw new RequestValidationException("Invalid country of origin");
        }
    }

    private static void validatePersonIdentifier(String personIdentifier) {
        if (StringUtils.isBlank(personIdentifier) || personIdentifier.length() > 30) {
            throw new RequestValidationException("Invalid person identifier");
        }
    }

    private static void validatePhoneNo(String phoneNo) {
        if (StringUtils.isBlank(phoneNo) || phoneNo.length() > 20)
            throw new RequestValidationException("Invalid phone No.");
    }

    private static void validateLanguage(String language) {
        if (StringUtils.isBlank(language) || language.length() != 3) {
            throw new RequestValidationException("Invalid Mobile-Id language");
        }
    }

    private static void validateMessageToDisplay(String messageToDisplay) {
        if (messageToDisplay != null && messageToDisplay.length() > 40) {
            throw new RequestValidationException("Invalid Mobile-Id message to display");
        }
    }


}
