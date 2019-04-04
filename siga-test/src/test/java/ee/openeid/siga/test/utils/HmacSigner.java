package ee.openeid.siga.test.utils;

import org.apache.commons.codec.binary.Hex;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class HmacSigner {

    public static String generateHmacSignature(final String key, final String data, String hmacAlgo) throws NoSuchAlgorithmException, InvalidKeyException {
        if (hmacAlgo == null)
            hmacAlgo = "HmacSHA256";
        if (key == null || data == null) throw new NullPointerException();
        final Mac hMacSHA256 = Mac.getInstance(hmacAlgo);
        byte[] hmacKeyBytes = key.getBytes(StandardCharsets.UTF_8);
        final SecretKeySpec secretKey = new SecretKeySpec(hmacKeyBytes, hmacAlgo);
        hMacSHA256.init(secretKey);
        byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] res = hMacSHA256.doFinal(dataBytes);
        return Hex.encodeHexString(res);
    }
}
