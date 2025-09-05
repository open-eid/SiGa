package ee.openeid.siga.common.client;

public class HttpClientTimeoutException extends HttpClientException {

    public HttpClientTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
