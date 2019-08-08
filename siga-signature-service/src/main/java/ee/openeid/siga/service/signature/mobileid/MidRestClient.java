package ee.openeid.siga.service.signature.mobileid;

import ee.openeid.siga.common.MobileIdInformation;
import ee.openeid.siga.common.exception.InvalidLanguageException;
import ee.openeid.siga.service.signature.configuration.MidRestConfigurationProperties;
import ee.sk.mid.MidClient;
import ee.sk.mid.MidHashToSign;
import ee.sk.mid.MidHashType;
import ee.sk.mid.MidLanguage;
import ee.sk.mid.rest.dao.MidSessionStatus;
import ee.sk.mid.rest.dao.request.MidCertificateRequest;
import ee.sk.mid.rest.dao.request.MidSessionStatusRequest;
import ee.sk.mid.rest.dao.request.MidSignatureRequest;
import ee.sk.mid.rest.dao.response.MidCertificateChoiceResponse;
import ee.sk.mid.rest.dao.response.MidSignatureResponse;
import org.digidoc4j.DataToSign;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.cert.X509Certificate;
import java.util.Base64;

public class MidRestClient implements MobileIdClient {

    private MidRestConfigurationProperties configurationProperties;
    private static final String SIGNATURE_COMPLETED_STATE = "COMPLETE";

    @Override
    public X509Certificate getCertificate(MobileIdInformation mobileIdInformation) {
        MidCertificateRequest request = MidCertificateRequest.newBuilder()
                .withPhoneNumber(mobileIdInformation.getPhoneNo())
                .withNationalIdentityNumber(mobileIdInformation.getPersonIdentifier())
                .build();
        MidClient midClient = createMidRestClient();
        MidCertificateChoiceResponse response = midClient.getMobileIdConnector().getCertificate(request);

        return midClient.createMobileIdCertificate(response);
    }

    @Override
    public InitMidSignatureResponse initMobileSigning(DataToSign dataToSign, MobileIdInformation mobileIdInformation) {
        MidHashType midHashType = getMidHashType(dataToSign);

        MidHashToSign hashToSign = MidHashToSign.newBuilder()
                .withDataToHash(dataToSign.getDataToSign())
                .withHashType(midHashType)
                .build();

        String verificationCode = hashToSign.calculateVerificationCode();
        MidLanguage midLanguage = getLanguage(mobileIdInformation.getLanguage());

        MidSignatureRequest request = MidSignatureRequest.newBuilder()
                .withPhoneNumber(mobileIdInformation.getPhoneNo())
                .withNationalIdentityNumber(mobileIdInformation.getPersonIdentifier())
                .withHashToSign(hashToSign)
                .withLanguage(midLanguage)
                .withDisplayText(mobileIdInformation.getMessageToDisplay())
                .build();
        MidClient midClient = createMidRestClient();

        MidSignatureResponse midSignatureResponse = midClient.getMobileIdConnector().sign(request);

        InitMidSignatureResponse response = new InitMidSignatureResponse();
        response.setChallengeId(verificationCode);
        response.setSessionCode(midSignatureResponse.getSessionID());
        return response;
    }

    @Override
    public GetStatusResponse getStatus(String sessionCode) {
        MidClient midClient = createMidRestClient();
        MidSessionStatusRequest request = new MidSessionStatusRequest(sessionCode);
        MidSessionStatus sessionStatus = midClient.getMobileIdConnector().getSignatureSessionStatus(request);
        GetStatusResponse response = new GetStatusResponse();
        response.setStatus(sessionStatus.getResult());

        if (SIGNATURE_COMPLETED_STATE.equals(sessionStatus.getState()) && OK_RESPONSE.equals(sessionStatus.getResult())) {
            response.setSignature(Base64.getDecoder().decode(sessionStatus.getSignature().getValue().getBytes()));
        }
        return response;
    }

    private MidLanguage getLanguage(String language) {
        if (language == null) {
            return MidLanguage.EST;
        }
        for (MidLanguage midLanguage : MidLanguage.values()) {
            if (midLanguage.name().equals(language))
                return midLanguage;
        }
        throw new InvalidLanguageException("Invalid language");
    }

    private MidHashType getMidHashType(DataToSign dataToSign) {
        for (MidHashType midhashType : MidHashType.values()) {
            if (midhashType.getHashTypeName().equals(dataToSign.getDigestAlgorithm().name())) {
                return midhashType;
            }
        }
        throw new IllegalArgumentException("Invalid mid hash type");
    }

    private MidClient createMidRestClient() {
        return MidClient.newBuilder().withHostUrl(configurationProperties.getUrl())
                .withPollingSleepTimeoutSeconds(2)
                .withRelyingPartyName("DEMO")//TODO: change it
                .withRelyingPartyUUID("00000000-0000-0000-0000-000000000000")//TODO: change it
                .build();
    }

    @Autowired
    public void setConfigurationProperties(MidRestConfigurationProperties configurationProperties) {
        this.configurationProperties = configurationProperties;
    }
}
