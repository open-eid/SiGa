package ee.openeid.siga;

import ee.openeid.siga.common.CertificateUtil;
import ee.openeid.siga.common.MobileIdInformation;
import ee.openeid.siga.webapp.json.CreateHashCodeMobileIdSigningRequest;
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

    public static SignatureParameters transformMobileIdSignatureParameters(CreateHashCodeMobileIdSigningRequest request) {
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

    public static MobileIdInformation transformMobileIdInformation(CreateHashCodeMobileIdSigningRequest request) {
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
