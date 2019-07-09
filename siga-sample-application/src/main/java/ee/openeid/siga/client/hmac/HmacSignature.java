package ee.openeid.siga.client.hmac;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
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

@Getter
@Builder
@ToString
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
    @ToString.Exclude
    private final byte[] payload;

    public static boolean isTimestampValid(Instant hmacTimestamp, long expirationInSeconds, long clockSkew) {
        return isTimestampValid(now(), hmacTimestamp, expirationInSeconds, clockSkew);
    }

    public static boolean isTimestampValid(Instant serverTimestamp, Instant hmacTimestamp, long expirationInSeconds, long clockSkew) {
        boolean cannotBeInFuture = hmacTimestamp.toEpochMilli() <= serverTimestamp.plusSeconds(clockSkew).toEpochMilli();
        boolean cannotBeExpired = hmacTimestamp.plusSeconds(expirationInSeconds).toEpochMilli() >= serverTimestamp.minusSeconds(clockSkew).toEpochMilli();
        return (cannotBeInFuture && cannotBeExpired);
    }

    public boolean isSignatureValid(byte[] signingSecret) throws DecoderException, NoSuchAlgorithmException, InvalidKeyException {
        final byte[] calculatedSignature = getSignature(signingSecret);
        return MessageDigest.isEqual(calculatedSignature, Hex.decodeHex(signature));
    }

    public boolean isTimestampValid(long expirationInSeconds, long clockSkew) {
        return HmacSignature.isTimestampValid(ofEpochSecond(parseLong(timestamp)), expirationInSeconds, clockSkew);
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
