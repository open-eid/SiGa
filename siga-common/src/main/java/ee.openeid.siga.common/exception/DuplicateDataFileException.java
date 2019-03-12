package ee.openeid.siga.common.exception;

public class DuplicateDataFileException extends RuntimeException implements LoggableException {

    public DuplicateDataFileException(String message) {
        super(message);
    }
}
