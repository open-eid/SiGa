package ee.openeid.siga.service.signature.mobileid;

import ee.openeid.siga.common.MobileIdInformation;
import ee.openeid.siga.common.event.Param;
import ee.openeid.siga.common.event.SigaEventLog;
import ee.openeid.siga.common.event.SigaEventName;
import ee.openeid.siga.common.event.XPath;
import ee.openeid.siga.common.exception.ClientException;
import ee.openeid.siga.common.exception.InvalidLanguageException;
import ee.openeid.siga.common.exception.MidException;
import ee.openeid.siga.service.signature.configuration.MidRestConfigurationProperties;
import ee.sk.mid.MidClient;
import ee.sk.mid.MidHashToSign;
import ee.sk.mid.MidHashType;
import ee.sk.mid.MidLanguage;
import ee.sk.mid.exception.MidInternalErrorException;
import ee.sk.mid.exception.MidMissingOrInvalidParameterException;
import ee.sk.mid.rest.dao.MidSessionStatus;
import ee.sk.mid.rest.dao.request.MidCertificateRequest;
import ee.sk.mid.rest.dao.request.MidSessionStatusRequest;
import ee.sk.mid.rest.dao.request.MidSignatureRequest;
import ee.sk.mid.rest.dao.response.MidCertificateChoiceResponse;
import ee.sk.mid.rest.dao.response.MidSignatureResponse;
import org.digidoc4j.DataToSign;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.ServerErrorException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Map;

public class MidRestClient implements MobileIdClient {

    private MidRestConfigurationProperties configurationProperties;
    private static final Map<String, MidStatus> COMPLETE_STATE_MAPPINGS = Map.of(
            "OK", MidStatus.SIGNATURE,
            "TIMEOUT", MidStatus.EXPIRED_TRANSACTION,
            "USER_CANCELLED", MidStatus.USER_CANCEL,
            "SIGNATURE_HASH_MISMATCH", MidStatus.NOT_VALID,
            "DELIVERY_ERROR", MidStatus.SENDING_ERROR,
            "SIM_ERROR", MidStatus.SIM_ERROR,
            "PHONE_ABSENT", MidStatus.PHONE_ABSENT
    );

    @Override
    @SigaEventLog(eventName = SigaEventName.MID_GET_MOBILE_CERTIFICATE,
            logParameters = {@Param(index = 0, fields = {@XPath(name = "person_identifier", xpath = "personIdentifier")}), @Param(index = 0, fields = {@XPath(name = "phone_nr", xpath = "phoneNo")})})
    public X509Certificate getCertificate(MobileIdInformation mobileIdInformation) {
        MidClient midClient = createMidRestClient(mobileIdInformation);
        MidCertificateRequest request = MidCertificateRequest.newBuilder()
                .withPhoneNumber(mobileIdInformation.getPhoneNo())
                .withNationalIdentityNumber(mobileIdInformation.getPersonIdentifier())
                .build();
        try {
            MidCertificateChoiceResponse midCertificateChoiceResponse = midClient.getMobileIdConnector().getCertificate(request);
            if (OK_RESPONSE.equals(midCertificateChoiceResponse.getResult())) {
                return midClient.createMobileIdCertificate(midCertificateChoiceResponse);
            }
            throw new MidException(mapToCertificateStatus(midCertificateChoiceResponse.getResult()).name());
        } catch (MidInternalErrorException | ServerErrorException e) {
            throw new ClientException("Mobile-ID service error", e);
        } catch (MidMissingOrInvalidParameterException e) {
            throw new MidException(CertificateStatus.NOT_FOUND.name(), e);
        }
    }

    @Override
    @SigaEventLog(eventName = SigaEventName.MID_MOBILE_SIGN_HASH,
            logParameters = {@Param(index = 1, fields = {@XPath(name = "person_identifier", xpath = "personIdentifier")}), @Param(index = 1, fields = {@XPath(name = "relying_party_name", xpath = "relyingPartyName")})},
            logReturnObject = {@XPath(name = "session_code", xpath = "sessionCode")})
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
        MidClient midClient = createMidRestClient(mobileIdInformation);
        try {
            MidSignatureResponse midSignatureResponse = midClient.getMobileIdConnector().sign(request);

            InitMidSignatureResponse response = new InitMidSignatureResponse();
            response.setChallengeId(verificationCode);
            response.setSessionCode(midSignatureResponse.getSessionID());
            return response;
        } catch (MidInternalErrorException | ServerErrorException e) {
            throw new ClientException("Mobile-ID service error", e);
        }
    }

    @Override
    @SigaEventLog(eventName = SigaEventName.MID_GET_MOBILE_SIGN_HASH_STATUS,
            logParameters = {@Param(name = "session_code", index = 0)},
            logReturnObject = {@XPath(name = "mid_rest_result", xpath = "status")})
    public GetStatusResponse getStatus(String sessionCode, MobileIdInformation mobileIdInformation) {
        MidClient midClient = createMidRestClient(mobileIdInformation);
        MidSessionStatusRequest request = new MidSessionStatusRequest(sessionCode, 1);
        GetStatusResponse response = new GetStatusResponse();

        try {
            MidSessionStatus sessionStatus = midClient.getMobileIdConnector().getSignatureSessionStatus(request);
            response.setStatus(mapToMidStatus(sessionStatus.getState(), sessionStatus.getResult()));
            if (response.getStatus() == MidStatus.SIGNATURE) {
                response.setSignature(Base64.getDecoder().decode(sessionStatus.getSignature().getValue().getBytes()));
            }
        } catch (ServerErrorException e) {
            response.setStatus(MidStatus.INTERNAL_ERROR);
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

    private MidClient createMidRestClient(MobileIdInformation mobileIdInformation) {
        return MidClient.newBuilder().withHostUrl(configurationProperties.getUrl())
                .withPollingSleepTimeoutSeconds(2)
                .withRelyingPartyName(mobileIdInformation.getRelyingPartyName())
                .withRelyingPartyUUID(mobileIdInformation.getRelyingPartyUUID())
                .build();
    }

    private static MidStatus mapToMidStatus(String state, String result) {
        if ("RUNNING".equals(state)) {
            return MidStatus.OUTSTANDING_TRANSACTION;
        } else if ("COMPLETE".equals(state)) {
            MidStatus midStatus = COMPLETE_STATE_MAPPINGS.get(result);
            if (midStatus != null) return midStatus;
        }
        throw new ClientException("Mobile-ID service returned unexpected response",
                new IllegalStateException("MID REST responded with: state=" + state + "; result=" + result));
    }

    private static CertificateStatus mapToCertificateStatus(String result) {
        if (CertificateStatus.NOT_FOUND.name().equals(result)) {
            return CertificateStatus.NOT_FOUND;
        } else if (CertificateStatus.NOT_ACTIVE.name().equals(result)) {
            return CertificateStatus.NOT_ACTIVE;
        }
        return CertificateStatus.UNEXPECTED_STATUS;
    }

    @Autowired
    public void setConfigurationProperties(MidRestConfigurationProperties configurationProperties) {
        this.configurationProperties = configurationProperties;
    }
}
