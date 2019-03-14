package ee.openeid.siga;

import ee.openeid.siga.common.CertificateUtil;
import ee.openeid.siga.common.MobileIdInformation;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerMobileIdSigningRequest;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerRemoteSigningRequest;
import org.digidoc4j.SignatureParameters;
import org.digidoc4j.SignatureProfile;

import java.security.cert.X509Certificate;
import java.util.Base64;

public class RequestTransformer {

    public static SignatureParameters transformRemoteRequest(CreateHashcodeContainerRemoteSigningRequest remoteSigningRequest) {
        SignatureParameters signatureParameters = new SignatureParameters();
        byte[] base64DecodedCertificate = Base64.getDecoder().decode(remoteSigningRequest.getSigningCertificate().getBytes());
        X509Certificate x509Certificate = CertificateUtil.createX509Certificate(base64DecodedCertificate);

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

    public static SignatureParameters transformMobileIdSignatureParameters(CreateHashcodeContainerMobileIdSigningRequest request) {
        SignatureParameters signatureParameters = new SignatureParameters();
        SignatureProfile signatureProfile = SignatureProfile.findByProfile(request.getSignatureProfile());
        signatureParameters.setSignatureProfile(signatureProfile);
        signatureParameters.setCountry(request.getCountry());
        signatureParameters.setStateOrProvince(request.getStateOrProvince());
        signatureParameters.setCity(request.getCity());
        signatureParameters.setPostalCode(request.getPostalCode());
        signatureParameters.setRoles(request.getRoles());
        return signatureParameters;

    }

    public static MobileIdInformation transformMobileIdInformation(CreateHashcodeContainerMobileIdSigningRequest request) {
        MobileIdInformation mobileIdInformation = new MobileIdInformation();
        mobileIdInformation.setLanguage(request.getLanguage());
        mobileIdInformation.setMessageToDisplay(request.getMessageToDisplay());
        mobileIdInformation.setPersonIdentifier(request.getPersonIdentifier());
        mobileIdInformation.setCountry(request.getOriginCountry());
        mobileIdInformation.setPhoneNo(request.getPhoneNo());
        mobileIdInformation.setServiceName(request.getServiceName());
        return mobileIdInformation;
    }

}
