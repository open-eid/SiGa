package ee.openeid.siga.validation;

import ee.openeid.siga.common.exception.InvalidRequestException;
import ee.openeid.siga.webapp.json.CreateHashCodeContainerRequest;
import ee.openeid.siga.webapp.json.CreateHashCodeValidationReportRequest;
import ee.openeid.siga.webapp.json.HashCodeDataFile;
import ee.openeid.siga.webapp.json.UploadHashCodeContainerRequest;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;

public class RequestValidator {

    public static void validateCreateContainerRequest(CreateHashCodeContainerRequest request) {
        validateFileName(request.getContainerName(), "Container name is invalid");
        request.getDataFiles().forEach(dataFile -> validateHashCodeDataFile(dataFile));
    }

    public static void validateUploadContainerRequest(UploadHashCodeContainerRequest request) {
        validateFileName(request.getContainerName(), "Container name is invalid");
        validateFileContent(request.getContainer());
    }

    public static void validateContainerId(String containerId) {
        validateFileName(containerId, "Container name is invalid");
    }

    public static void validateValidationReportRequest(CreateHashCodeValidationReportRequest request) {
        validateFileName(request.getContainerName(), "Container name is invalid");
        validateFileContent(request.getContainer());
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

    private static void validateFileContent(String content) {
        if (StringUtils.isBlank(content) || !isBase64StringEncoded(content)) {
            throw new InvalidRequestException("File content is invalid");
        }
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

}
