package ee.openeid.siga.common.client;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public class HttpStatusException extends HttpClientException {
    @Getter
    private final HttpStatus httpStatus;
    private final byte[] responseBody;

    public byte[] getResponseBody() {
        return responseBody != null ? responseBody.clone() : null;
    }
}
