package ee.openeid.siga.client.hmac;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.time.Instant;

@Slf4j
public class HmacTokenAuthorizationHeaderInterceptor implements ClientHttpRequestInterceptor {
    public static final String X_AUTHORIZATION_TIMESTAMP = "X-Authorization-Timestamp";
    public static final String X_AUTHORIZATION_SERVICE_UUID = "X-Authorization-ServiceUUID";
    public static final String X_AUTHORIZATION_SIGNATURE = "X-Authorization-Signature";
    private final String SIGA_API_URL;
    private final String HMAC_ALGORITHM;
    private final String HMAC_SERVICE_UUID;
    private final String HMAC_SHARED_SIGNING_KEY;

    public HmacTokenAuthorizationHeaderInterceptor(String sigaApiUri, String hmacAlgorithm, String hmacServiceUuid, String hmacSharedSigningKey) {
        this.SIGA_API_URL = StringUtils.removeEnd(sigaApiUri, "/");
        this.HMAC_ALGORITHM = hmacAlgorithm;
        this.HMAC_SERVICE_UUID = hmacServiceUuid;
        this.HMAC_SHARED_SIGNING_KEY = hmacSharedSigningKey;
    }

    @Override
    @SneakyThrows
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        final String timestamp = String.valueOf(Instant.now().getEpochSecond());
        HmacSignature token = HmacSignature.builder()
                .macAlgorithm(HMAC_ALGORITHM)
                .uri(request.getURI().toString().replace(SIGA_API_URL, StringUtils.EMPTY))
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
        return execution.execute(request, body);
    }
}
