package ee.openeid.siga.service.signature.mobileid.midrest;

import ee.openeid.siga.common.event.LogParam;
import ee.openeid.siga.common.event.Param;
import ee.openeid.siga.common.event.SigaEventLog;
import ee.openeid.siga.common.event.SigaEventName;
import ee.openeid.siga.common.event.XPath;
import ee.openeid.siga.common.exception.ClientException;
import ee.openeid.siga.common.exception.InvalidLanguageException;
import ee.openeid.siga.common.exception.MidException;
import ee.openeid.siga.common.model.MobileIdInformation;
import ee.openeid.siga.common.model.RelyingPartyInfo;
import ee.openeid.siga.common.model.Result;
import ee.openeid.siga.service.signature.mobileid.CertificateStatus;
import ee.openeid.siga.service.signature.mobileid.GetStatusResponse;
import ee.openeid.siga.service.signature.mobileid.InitMidSignatureResponse;
import ee.openeid.siga.service.signature.mobileid.MidStatus;
import ee.openeid.siga.service.signature.mobileid.MobileIdClient;
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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.ws.rs.ServerErrorException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Map;

@Component
@EnableConfigurationProperties(MidRestConfigurationProperties.class)
public class MidRestClient implements MobileIdClient {

    private static final Map<String, MidStatus> COMPLETE_STATE_MAPPINGS = Map.of(
            "OK", MidStatus.SIGNATURE,
            "TIMEOUT", MidStatus.EXPIRED_TRANSACTION,
            "USER_CANCELLED", MidStatus.USER_CANCEL,
            "SIGNATURE_HASH_MISMATCH", MidStatus.NOT_VALID,
            "DELIVERY_ERROR", MidStatus.SENDING_ERROR,
            "SIM_ERROR", MidStatus.SIM_ERROR,
            "PHONE_ABSENT", MidStatus.PHONE_ABSENT
    );

    private final MidRestConfigurationProperties configurationProperties;

    @Autowired
    public MidRestClient(MidRestConfigurationProperties configurationProperties) {
        this.configurationProperties = configurationProperties;
    }

    @Override
    @SigaEventLog(eventName = SigaEventName.MID_GET_MOBILE_CERTIFICATE,
            logStaticParameters = {@LogParam(name = SigaEventName.EventParam.REQUEST_URL, value = "${siga.midrest.url}")})
    public X509Certificate getCertificate(RelyingPartyInfo relyingPartyInfo, MobileIdInformation mobileIdInformation) {
        MidClient midClient = createMidRestClient(relyingPartyInfo);
        MidCertificateRequest request = MidCertificateRequest.newBuilder()
                .withPhoneNumber(mobileIdInformation.getPhoneNo())
                .withNationalIdentityNumber(mobileIdInformation.getPersonIdentifier())
                .build();
        try {
            MidCertificateChoiceResponse midCertificateChoiceResponse = midClient.getMobileIdConnector().getCertificate(request);
            if (Result.OK.name().equals(midCertificateChoiceResponse.getResult())) {
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
            logParameters = {@Param(index = 0, fields = {@XPath(name = "relying_party_name", xpath = "name")})},
            logReturnObject = {@XPath(name = "mid_session_id", xpath = "sessionCode")},
            logStaticParameters = {@LogParam(name = SigaEventName.EventParam.REQUEST_URL, value = "${siga.midrest.url}")})
    public InitMidSignatureResponse initMobileSigning(RelyingPartyInfo relyingPartyInfo, DataToSign dataToSign, MobileIdInformation mobileIdInformation) {
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
        MidClient midClient = createMidRestClient(relyingPartyInfo);
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
            logParameters = {@Param(name = "mid_session_id", index = 0)},
            logReturnObject = {@XPath(name = "mid_status", xpath = "status")},
            logStaticParameters = {@LogParam(name = SigaEventName.EventParam.REQUEST_URL, value = "${siga.midrest.url}")})
    public GetStatusResponse getStatus(String sessionCode, RelyingPartyInfo relyingPartyInfo) {
        MidClient midClient = createMidRestClient(relyingPartyInfo);
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

    private MidClient createMidRestClient(RelyingPartyInfo relyingPartyInfo) {
        return MidClient.newBuilder().withHostUrl(configurationProperties.getUrl())
                .withTrustStore(getMidTruststore())
                .withPollingSleepTimeoutSeconds(2)
                .withRelyingPartyName(relyingPartyInfo.getName())
                .withRelyingPartyUUID(relyingPartyInfo.getUuid())
                .build();
    }

    private KeyStore getMidTruststore() {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            InputStream is = MidRestClient.class.getClassLoader().getResourceAsStream(configurationProperties.getTruststorePath());
            if (is == null) {
                throw new IllegalArgumentException("Unable to find Mid-rest truststore file");
            }
            keyStore.load(is, configurationProperties.getTruststorePassword().toCharArray());
            return keyStore;
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
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
        }
        return CertificateStatus.UNEXPECTED_STATUS;
    }
}
