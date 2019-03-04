package ee.openeid.siga;

import ee.openeid.siga.common.CertificateUtil;
import ee.openeid.siga.webapp.json.CreateHashCodeRemoteSigningRequest;
import org.digidoc4j.SignatureParameters;
import org.digidoc4j.SignatureProfile;

import java.security.cert.X509Certificate;

public class RequestTransformer {

    public static SignatureParameters transformRemoteRequest(CreateHashCodeRemoteSigningRequest remoteSigningRequest) {
        SignatureParameters signatureParameters = new SignatureParameters();
        X509Certificate x509Certificate = CertificateUtil.createX509Certificate(remoteSigningRequest.getSigningCertificate().getBytes());

        signatureParameters.setSigningCertificate(x509Certificate);
        SignatureProfile signatureProfile = SignatureProfile.findByProfile(remoteSigningRequest.getSignatureProfile());
        signatureParameters.setSignatureProfile(signatureProfile);
        signatureParameters.setCountry(remoteSigningRequest.getCountry());
        signatureParameters.setStateOrProvince(remoteSigningRequest.getStateOrProvince());
        signatureParameters.setCity(remoteSigningRequest.getCity());
        signatureParameters.setPostalCode(remoteSigningRequest.getPostalCode());
        signatureParameters.setRoles(remoteSigningRequest.getRoles());
        return signatureParameters;
    }
}
