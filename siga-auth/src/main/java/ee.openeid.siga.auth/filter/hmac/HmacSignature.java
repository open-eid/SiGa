package ee.openeid.siga.auth.filter.hmac;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static java.util.Objects.requireNonNull;
import static lombok.AccessLevel.PRIVATE;

@Getter
@Builder
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class HmacSignature {
    static byte DELIMITER = ':';
    String macAlgorithm;
    String signature;
    String serviceUuid;
    String timestamp;
    byte[] payload;

    public boolean isValid(byte[] signingSecret) throws DecoderException, NoSuchAlgorithmException,
            InvalidKeyException {
        requireNonNull(signingSecret, "signingSecret");
        final byte[] calculatedSignature = getSignature(signingSecret);
        return MessageDigest.isEqual(calculatedSignature, Hex.decodeHex(signature));
    }

    public String getSignature(String signingSecret) throws NoSuchAlgorithmException, InvalidKeyException {
        return Hex.encodeHexString(getSignature(signingSecret.getBytes()));
    }

    private byte[] getSignature(byte[] signingSecret) throws NoSuchAlgorithmException, InvalidKeyException {
        final Mac hmac = Mac.getInstance(macAlgorithm);
        SecretKeySpec secretKey = new SecretKeySpec(signingSecret, macAlgorithm);
        hmac.init(secretKey);
        hmac.update(serviceUuid.getBytes(StandardCharsets.UTF_8));
        hmac.update(DELIMITER);
        hmac.update(timestamp.getBytes(StandardCharsets.UTF_8));
        hmac.update(DELIMITER);
        hmac.update(payload);
        final byte[] signatureBytes = hmac.doFinal();
        hmac.reset();
        return signatureBytes;
    }
}
