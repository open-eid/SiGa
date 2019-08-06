package ee.openeid.siga.service.signature.mobileid;

import ee.openeid.siga.common.MobileIdInformation;
import ee.openeid.siga.mobileid.client.DigiDocService;
import ee.openeid.siga.mobileid.client.MobileIdService;
import ee.openeid.siga.mobileid.model.dds.GetMobileCertificateResponse;
import ee.openeid.siga.mobileid.model.mid.GetMobileSignHashStatusResponse;
import ee.openeid.siga.mobileid.model.mid.MobileSignHashResponse;
import ee.openeid.siga.mobileid.model.mid.ProcessStatusType;
import eu.europa.esig.dss.DSSUtils;
import org.apache.commons.codec.binary.Hex;
import org.digidoc4j.DataToSign;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.cert.X509Certificate;

import static ee.openeid.siga.common.util.CertificateUtil.createX509Certificate;

public class DigiDocServiceClient implements MobileIdClient {

    private DigiDocService digiDocService;
    private MobileIdService mobileIdService;

    @Override
    public X509Certificate getCertificate(MobileIdInformation mobileIdInformation) {
        GetMobileCertificateResponse signingCertificate = digiDocService.getMobileCertificate(mobileIdInformation.getPersonIdentifier(), mobileIdInformation.getPhoneNo());
        return createX509Certificate(signingCertificate.getSignCertData().getBytes());
    }

    @Override
    public InitMidSignatureResponse initMobileSigning(DataToSign dataToSign, MobileIdInformation mobileIdInformation) {
        MobileSignHashResponse mobileSignHashResponse = initMobileSign(dataToSign, mobileIdInformation);
        InitMidSignatureResponse response = new InitMidSignatureResponse();
        response.setChallengeId(mobileSignHashResponse.getChallengeID());
        response.setSessionCode(mobileSignHashResponse.getSesscode());
        return response;
    }

    @Override
    public GetStatusResponse getStatus(String sessionCode) {
        GetMobileSignHashStatusResponse getMobileSignHashStatusResponse = mobileIdService.getMobileSignHashStatus(sessionCode);
        ProcessStatusType status = getMobileSignHashStatusResponse.getStatus();
        GetStatusResponse response = new GetStatusResponse();
        response.setStatus(status.name());
        if (ProcessStatusType.SIGNATURE == status) {
            response.setSignature(getMobileSignHashStatusResponse.getSignature());
        }
        return response;
    }

    private MobileSignHashResponse initMobileSign(DataToSign dataToSign, MobileIdInformation mobileIdInformation) {
        byte[] digest = DSSUtils.digest(dataToSign.getDigestAlgorithm().getDssDigestAlgorithm(), dataToSign.getDataToSign());
        MobileSignHashResponse response = mobileIdService.initMobileSignHash(mobileIdInformation, dataToSign.getDigestAlgorithm().name(), Hex.encodeHexString(digest));
        if (!OK_RESPONSE.equals(response.getStatus())) {
            throw new IllegalStateException("Invalid DigiDocService response");
        }
        return response;
    }

    @Autowired
    public void setDigiDocService(DigiDocService digiDocService) {
        this.digiDocService = digiDocService;
    }

    @Autowired
    public void setMobileIdService(MobileIdService mobileIdService) {
        this.mobileIdService = mobileIdService;
    }
}
