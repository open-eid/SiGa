package ee.openeid.siga.client.hmac;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum HmacHeader {
    X_AUTHORIZATION_TIMESTAMP("X-Authorization-Timestamp"),
    X_AUTHORIZATION_SERVICE_UUID("X-Authorization-ServiceUUID"),
    X_AUTHORIZATION_SIGNATURE("X-Authorization-Signature"),
    X_AUTHORIZATION_HMAC_ALGORITHM("X-Authorization-Hmac-Algorithm");

    @Getter
    private String value;
}
