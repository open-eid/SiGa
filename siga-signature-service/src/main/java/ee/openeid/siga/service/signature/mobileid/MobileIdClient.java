package ee.openeid.siga.service.signature.mobileid;

import ee.openeid.siga.common.model.MobileIdInformation;
import ee.openeid.siga.common.model.RelyingPartyInfo;
import org.digidoc4j.DataToSign;

import java.security.cert.X509Certificate;

public interface MobileIdClient {

    X509Certificate getCertificate(RelyingPartyInfo relyingPartyInfo, MobileIdInformation mobileIdInformation);

    InitMidSignatureResponse initMobileSigning(RelyingPartyInfo relyingPartyInfo, DataToSign dataToSign, MobileIdInformation mobileIdInformation);

    GetStatusResponse getStatus(String sessionCode, RelyingPartyInfo relyingPartyInfo);
}
