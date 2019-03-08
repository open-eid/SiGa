package ee.openeid.siga.test.utils;

import org.digidoc4j.DigestAlgorithm;
import org.digidoc4j.signers.PKCS12SignatureToken;

import java.util.Base64;

public class digestSigner {

    public static String  signDigest (String digestToSign) {
        ClassLoader classLoader = RequestBuilder.class.getClassLoader();
        String path = classLoader.getResource("sign_keystore.p12").getPath().substring(1);
        PKCS12SignatureToken signatureToken = new PKCS12SignatureToken(path, "1234".toCharArray());
        byte[] signed = signatureToken.sign(DigestAlgorithm.SHA512, Base64.getDecoder().decode(digestToSign));
        return Base64.getEncoder().encodeToString(signed);
    }
}
