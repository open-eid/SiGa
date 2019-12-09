package ee.openeid.siga.client.hmac;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum HmacAlgorithm {

    HMAC_SHA_256("HmacSHA256"),
    HMAC_SHA_384("HmacSHA384"),
    HMAC_SHA_512("HmacSHA512"),
    HMAC_SHA3_256("HmacSHA3-256"),
    HMAC_SHA3_384("HmacSHA3-384"),
    HMAC_SHA3_512("HmacSHA3-512");

    @Getter
    private String value;

    public static HmacAlgorithm fromString(String value) {
        if (value == null) {
            return null;
        }
        for (HmacAlgorithm algo : HmacAlgorithm.values()) {
            if (algo.value.equals(value)) {
                return algo;
            }
        }
        return null;
    }
}
