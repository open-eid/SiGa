package ee.openeid.siga;

import ee.openeid.siga.common.CertificateUtil;
import ee.openeid.siga.common.MobileIdInformation;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerMobileIdSigningRequest;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerRemoteSigningRequest;
import ee.openeid.siga.webapp.json.SignatureProductionPlace;
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
        SignatureProductionPlace signatureProductionPlace = remoteSigningRequest.getSignatureProductionPlace();
        if (signatureProductionPlace != null) {
            signatureParameters.setCountry(signatureProductionPlace.getCountry());
            signatureParameters.setStateOrProvince(signatureProductionPlace.getStateOrProvince());
            signatureParameters.setCity(signatureProductionPlace.getCity());
            signatureParameters.setPostalCode(signatureProductionPlace.getPostalCode());
        }
        signatureParameters.setRoles(remoteSigningRequest.getRoles());
        return signatureParameters;
    }

    public static SignatureParameters transformMobileIdSignatureParameters(CreateHashcodeContainerMobileIdSigningRequest request) {
        SignatureParameters signatureParameters = new SignatureParameters();
        SignatureProfile signatureProfile = SignatureProfile.findByProfile(request.getSignatureProfile());
        signatureParameters.setSignatureProfile(signatureProfile);
        SignatureProductionPlace signatureProductionPlace = request.getSignatureProductionPlace();
        if (signatureProductionPlace != null) {
            signatureParameters.setCountry(signatureProductionPlace.getCountry());
            signatureParameters.setStateOrProvince(signatureProductionPlace.getStateOrProvince());
            signatureParameters.setCity(signatureProductionPlace.getCity());
            signatureParameters.setPostalCode(signatureProductionPlace.getPostalCode());
        }
        signatureParameters.setRoles(request.getRoles());
        return signatureParameters;

    }

    public static MobileIdInformation transformMobileIdInformation(CreateHashcodeContainerMobileIdSigningRequest request) {
        MobileIdInformation mobileIdInformation = new MobileIdInformation();
        mobileIdInformation.setLanguage(request.getLanguage());
        mobileIdInformation.setMessageToDisplay(request.getMessageToDisplay());
        mobileIdInformation.setPersonIdentifier(request.getPersonIdentifier());
        mobileIdInformation.setCountry(request.getCountry());
        mobileIdInformation.setPhoneNo(request.getPhoneNo());
        return mobileIdInformation;
    }

}
