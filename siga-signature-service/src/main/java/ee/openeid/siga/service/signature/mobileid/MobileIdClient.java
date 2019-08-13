package ee.openeid.siga.service.signature.mobileid;

import ee.openeid.siga.common.MobileIdInformation;
import ee.openeid.siga.common.Result;
import org.digidoc4j.DataToSign;

public interface MobileIdClient {

    String OK_RESPONSE = Result.OK.name();

    GetCertificateResponse getCertificate(MobileIdInformation mobileIdInformation);

    InitMidSignatureResponse initMobileSigning(DataToSign dataToSign, MobileIdInformation mobileIdInformation);

    GetStatusResponse getStatus(String sessionCode, MobileIdInformation mobileIdInformation);
}
