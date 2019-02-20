package ee.openeid.siga.validation;

import ee.openeid.siga.common.exception.InvalidRequestException;
import ee.openeid.siga.webapp.json.CreateContainerRequest;
import ee.openeid.siga.webapp.json.DataFile;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;

public class RequestValidator {

    public static void validateCreateContainerRequest(CreateContainerRequest request) {
        validateFileName(request.getContainerName(), "Container name is invalid");
        request.getDataFiles().forEach(dataFile -> validateDataFile(dataFile));
    }

    private static void validateFileName(String fileName, String errorMessage) {
        if (StringUtils.isBlank(fileName) || fileName.length() < 1 || fileName.length() > 260) {
            throw new InvalidRequestException(errorMessage);
        }
    }

    private static void validateDataFile(DataFile dataFile) {
        validateFileName(dataFile.getFileName(), "Data file name is invalid");
        if (StringUtils.isBlank(dataFile.getFileContent())) {
            validateFileSize(dataFile.getFileSize());
            validateHash(dataFile.getFileHashSha256());
            validateHash(dataFile.getFileHashSha512());
        } else {
            validateFileContent(dataFile.getFileContent());
        }
    }

    private static void validateFileContent(String content) {
        if (!isBase64StringEncoded(content)) {
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
