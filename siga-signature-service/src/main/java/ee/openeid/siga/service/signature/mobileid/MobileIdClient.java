package ee.openeid.siga.service.signature.mobileid;

import ee.openeid.siga.common.model.MobileIdInformation;
import org.digidoc4j.DataToSign;

import java.security.cert.X509Certificate;

public interface MobileIdClient {

    X509Certificate getCertificate(MobileIdInformation mobileIdInformation);

    InitMidSignatureResponse initMobileSigning(DataToSign dataToSign, MobileIdInformation mobileIdInformation);

    GetStatusResponse getStatus(String sessionCode, MobileIdInformation mobileIdInformation);
}
