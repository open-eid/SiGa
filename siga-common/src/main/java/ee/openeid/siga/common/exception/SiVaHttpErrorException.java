package ee.openeid.siga.common.exception;

public class SiVaHttpErrorException extends RuntimeException {

    private static final String DEFAULT_MESSAGE = "Unable to get a valid response from SiVa";

    public SiVaHttpErrorException(Throwable cause) {
        super(DEFAULT_MESSAGE, cause);
    }
}
