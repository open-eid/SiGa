package ee.openeid.siga.auth.filter.hmac;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

import static java.lang.Long.parseLong;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Instant.now;
import static java.time.Instant.ofEpochSecond;
import static org.apache.commons.lang3.StringUtils.isNumeric;

@Getter
@Builder
public class HmacSignature {
    private static final String DELIMITER = ":";
    @NonNull
    private final String macAlgorithm;
    private final String signature;
    @NonNull
    private final String serviceUuid;
    @NonNull
    private final String requestMethod;
    @NonNull
    private final String uri;
    @NonNull
    private final String timestamp;
    private final byte[] payload;

    public static void validateTimestamp(String timestamp, long expirationInSeconds, long clockSkew) {
        if (timestamp.length() != 10 || !isNumeric(timestamp)) {
            throw new HmacAuthenticationException("Invalid X-Authorization-Timestamp header. Not in Unix epoch seconds format.");
        }
        Instant hmacTimestamp = ofEpochSecond(parseLong(timestamp));
        final Instant serverTimestamp = now();
        if(!isTokenNotInFuture(serverTimestamp, hmacTimestamp, clockSkew)) {
            throw new HmacAuthenticationException("HMAC token is expired. Token timestamp too far in future.");
        }
        if(!isTokenNotExpired(serverTimestamp, hmacTimestamp, expirationInSeconds, clockSkew)) {
            throw new HmacAuthenticationException("HMAC token is expired. Token timestamp too far in past.");
        }
    }

    public static boolean isTimestampValid(Instant serverTimestamp, Instant hmacTimestamp, long expirationInSeconds, long clockSkew) {
        return (isTokenNotInFuture(serverTimestamp, hmacTimestamp, clockSkew)
                && isTokenNotExpired(serverTimestamp, hmacTimestamp, expirationInSeconds, clockSkew));
    }

    private static boolean isTokenNotExpired(Instant serverTimestamp, Instant hmacTimestamp, long expirationInSeconds, long clockSkew) {
        return hmacTimestamp.plusSeconds(expirationInSeconds).toEpochMilli() >= serverTimestamp.minusSeconds(clockSkew).toEpochMilli();
    }

    private static boolean isTokenNotInFuture(Instant serverTimestamp, Instant hmacTimestamp, long clockSkew) {
        return hmacTimestamp.toEpochMilli() <= serverTimestamp.plusSeconds(clockSkew).toEpochMilli();
    }

    public void validateSignature(byte[] signingSecret) throws DecoderException, NoSuchAlgorithmException, InvalidKeyException {
        final byte[] calculatedSignature = getSignature(signingSecret);
        if(!MessageDigest.isEqual(calculatedSignature, Hex.decodeHex(signature))) {
           throw new HmacAuthenticationException("HMAC token provided signature and calculated signature do not match. Invalid signed token claims or invalid signature.");
        }
    }

    public String getSignature(String signingSecret) throws NoSuchAlgorithmException, InvalidKeyException {
        return Hex.encodeHexString(getSignature(signingSecret.getBytes()));
    }

    private byte[] getSignature(byte[] signingSecret) throws NoSuchAlgorithmException, InvalidKeyException {
        final Mac hmac = Mac.getInstance(macAlgorithm);
        SecretKeySpec secretKey = new SecretKeySpec(signingSecret, macAlgorithm);
        hmac.init(secretKey);
        hmac.update((serviceUuid + DELIMITER + timestamp + DELIMITER + requestMethod + DELIMITER + uri + DELIMITER).getBytes(UTF_8));
        hmac.update(payload);
        final byte[] signatureBytes = hmac.doFinal();
        hmac.reset();
        return signatureBytes;
    }
}
