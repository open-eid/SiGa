package ee.openeid.siga.client.hmac;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

@Slf4j
public class HmacTokenAuthorizationHeaderInterceptor implements ClientHttpRequestInterceptor {
    public static final String X_AUTHORIZATION_TIMESTAMP = "X-Authorization-Timestamp";
    public static final String X_AUTHORIZATION_SERVICE_UUID = "X-Authorization-ServiceUUID";
    public static final String X_AUTHORIZATION_SIGNATURE = "X-Authorization-Signature";
    private final String HMAC_ALGORITHM;
    private final String HMAC_SERVICE_UUID;
    private final String HMAC_SHARED_SIGNING_KEY;

    public HmacTokenAuthorizationHeaderInterceptor(String HMAC_ALGORITHM, String HMAC_SERVICE_UUID, String HMAC_SHARED_SIGNING_KEY) {
        this.HMAC_ALGORITHM = HMAC_ALGORITHM;
        this.HMAC_SERVICE_UUID = HMAC_SERVICE_UUID;
        this.HMAC_SHARED_SIGNING_KEY = HMAC_SHARED_SIGNING_KEY;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        final String timestamp = String.valueOf(Instant.now().getEpochSecond());
        try {
            HmacSignature token = HmacSignature.builder()
                    .macAlgorithm(HMAC_ALGORITHM)
                    .uri(request.getURI().getPath())
                    .requestMethod(request.getMethod().name())
                    .serviceUuid(HMAC_SERVICE_UUID)
                    .payload(body)
                    .timestamp(timestamp).build();
            String signature = token.getSignature(HMAC_SHARED_SIGNING_KEY);
            log.info(token.toString());
            log.info("Signature: {}", signature);
            request.getHeaders().add(X_AUTHORIZATION_TIMESTAMP, timestamp);
            request.getHeaders().add(X_AUTHORIZATION_SERVICE_UUID, HMAC_SERVICE_UUID);
            request.getHeaders().add(X_AUTHORIZATION_SIGNATURE, signature);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
        return execution.execute(request, body);
    }
}
