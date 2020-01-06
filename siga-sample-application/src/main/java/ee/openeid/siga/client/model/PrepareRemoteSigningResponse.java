package ee.openeid.siga.client.model;

import ee.openeid.siga.webapp.json.CreateContainerRemoteSigningResponse;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerRemoteSigningResponse;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Getter
@EqualsAndHashCode
@ToString
public class PrepareRemoteSigningResponse {

    private final String generatedSignatureId;
    private final byte[] dataToSignHash;
    private final String digestAlgorithm;

    private PrepareRemoteSigningResponse(String generatedSignatureId, String dataToSign, String digestAlgorithm) {
        this.generatedSignatureId = generatedSignatureId;
        this.digestAlgorithm = DigestAlgorithm.valueOf(digestAlgorithm).getJavaName();

        try {
            MessageDigest messageDigest = MessageDigest.getInstance(this.digestAlgorithm);
            this.dataToSignHash = messageDigest.digest(Base64.getDecoder().decode(dataToSign));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static PrepareRemoteSigningResponse from(CreateContainerRemoteSigningResponse createContainerRemoteSigningResponse) {
        return new PrepareRemoteSigningResponse(
                createContainerRemoteSigningResponse.getGeneratedSignatureId(),
                createContainerRemoteSigningResponse.getDataToSign(),
                createContainerRemoteSigningResponse.getDigestAlgorithm()
        );
    }

    public static PrepareRemoteSigningResponse from(CreateHashcodeContainerRemoteSigningResponse createHashcodeContainerRemoteSigningResponse) {
        return new PrepareRemoteSigningResponse(
                createHashcodeContainerRemoteSigningResponse.getGeneratedSignatureId(),
                createHashcodeContainerRemoteSigningResponse.getDataToSign(),
                createHashcodeContainerRemoteSigningResponse.getDigestAlgorithm()
        );
    }
}
