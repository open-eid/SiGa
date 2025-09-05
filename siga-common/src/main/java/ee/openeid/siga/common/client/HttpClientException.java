package ee.openeid.siga.common.client;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public abstract class HttpClientException extends RuntimeException {

    public HttpClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
