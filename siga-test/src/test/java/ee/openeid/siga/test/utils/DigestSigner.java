package ee.openeid.siga.test.utils;

import org.digidoc4j.DigestAlgorithm;
import org.digidoc4j.signers.PKCS12SignatureToken;

import java.util.Base64;

public class DigestSigner {

    public static String signDigest(String digestToSign, String algo) {
        return signDigestWithKeystore(digestToSign, algo, "sign_ESTEID2018.p12", "1234");
    }

    public static String signDigestWithKeystore(String digestToSign, String algo, String keystoreName, String keystorePassword) {
        ClassLoader classLoader = RequestBuilder.class.getClassLoader();
        String path = classLoader.getResource(keystoreName).getPath();
        PKCS12SignatureToken signatureToken = new PKCS12SignatureToken(path, keystorePassword.toCharArray());
        byte[] signed = signatureToken.sign(DigestAlgorithm.valueOf(algo), Base64.getDecoder().decode(digestToSign));
        return Base64.getEncoder().encodeToString(signed);
    }
}
