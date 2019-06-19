package ee.openeid.siga.common.exception;

public class DuplicateDataFileException extends SigaApiException {

    public DuplicateDataFileException(String message) {
        super("DUPLICATE_DATA_FILE_EXCEPTION", message);
    }
}
