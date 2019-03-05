package ee.openeid.siga.auth.filter.hmac;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum HmacHeaders {
    X_AUTHORIZATION_TIMESTAMP("X-Authorization-Timestamp"),
    X_AUTHORIZATION_SERVICE_UUID("X-Authorization-ServiceUUID"),
    X_AUTHORIZATION_SIGNATURE("X-Authorization-Signature");

    @Getter
    private String value;

}
