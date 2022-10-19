package ee.openeid.siga.service.signature.mobileid;

import ee.openeid.siga.common.event.*;
import ee.openeid.siga.common.exception.ClientException;
import ee.openeid.siga.common.exception.InvalidLanguageException;
import ee.openeid.siga.common.exception.MobileIdApiException;
import ee.openeid.siga.common.model.MobileIdInformation;
import ee.openeid.siga.common.model.RelyingPartyInfo;
import ee.openeid.siga.common.model.Result;
import ee.openeid.siga.service.signature.configuration.MobileIdClientConfigurationProperties;
import ee.sk.mid.MidClient;
import ee.sk.mid.MidHashToSign;
import ee.sk.mid.MidHashType;
import ee.sk.mid.MidLanguage;
import ee.sk.mid.exception.*;
import ee.sk.mid.rest.dao.MidSessionStatus;
import ee.sk.mid.rest.dao.request.MidCertificateRequest;
import ee.sk.mid.rest.dao.request.MidSignatureRequest;
import ee.sk.mid.rest.dao.response.MidCertificateChoiceResponse;
import ee.sk.mid.rest.dao.response.MidSignatureResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.digidoc4j.DataToSign;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import javax.ws.rs.ServerErrorException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Map;

import static java.lang.String.format;

@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(MobileIdClientConfigurationProperties.class)
public class MobileIdApiClient {

    private static final Map<Class<?>, MobileIdSessionStatus> errorMap = Map.of(
            MidMissingOrInvalidParameterException.class, MobileIdSessionStatus.INTERNAL_ERROR,
            MidSessionNotFoundException.class, MobileIdSessionStatus.INTERNAL_ERROR,
            MidUnauthorizedException.class, MobileIdSessionStatus.INTERNAL_ERROR,
            MidInternalErrorException.class, MobileIdSessionStatus.INTERNAL_ERROR,
            MidNotMidClientException.class, MobileIdSessionStatus.NOT_MID_CLIENT,
            MidPhoneNotAvailableException.class, MobileIdSessionStatus.PHONE_ABSENT,
            MidDeliveryException.class, MobileIdSessionStatus.SENDING_ERROR,
            MidInvalidUserConfigurationException.class, MobileIdSessionStatus.NOT_VALID,
            MidSessionTimeoutException.class, MobileIdSessionStatus.EXPIRED_TRANSACTION,
            MidUserCancellationException.class, MobileIdSessionStatus.USER_CANCEL);

    private final MobileIdClientConfigurationProperties configurationProperties;
    private final ResourceLoader resourceLoader;

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
            throw new MobileIdApiException(mapToCertificateStatus(midCertificateChoiceResponse.getResult()).name());
        } catch (MidInternalErrorException | ServerErrorException e) {
            throw new ClientException("Mobile-ID service error", e);
        } catch (MidMissingOrInvalidParameterException e) {
            throw new MobileIdApiException(CertificateStatus.NOT_FOUND.name(), e);
        }
    }

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

    @SigaEventLog(eventName = SigaEventName.MID_GET_MOBILE_SIGN_HASH_STATUS,
            logParameters = {@Param(name = "mid_session_id", index = 1)},
            logReturnObject = {@XPath(name = "mid_status", xpath = "status")},
            logStaticParameters = {@LogParam(name = SigaEventName.EventParam.REQUEST_URL, value = "${siga.midrest.url}")})
    public MobileIdStatusResponse getSignatureStatus(RelyingPartyInfo relyingPartyInfo, String sessionCode) {
        MobileIdStatusResponse response = new MobileIdStatusResponse();
        try {
            MidClient midClient = createMidRestClient(relyingPartyInfo);
            MidSessionStatus sessionStatus = midClient.getSessionStatusPoller().fetchFinalSessionStatus(sessionCode, format("/signature/session/%s", sessionCode));
            response.setStatus(mapToMidStatus(sessionStatus.getState(), sessionStatus.getResult()));
            if (response.getStatus() == MobileIdSessionStatus.SIGNATURE) {
                response.setSignature(Base64.getDecoder().decode(sessionStatus.getSignature().getValue().getBytes()));
            }
        } catch (MidException ex) {
            log.error("Unable to fetch final session status: {}", ex.getMessage(), ex);
            response.setStatus(errorMap.getOrDefault(ex.getClass(), MobileIdSessionStatus.INTERNAL_ERROR));
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
                .withLongPollingTimeoutSeconds((int) configurationProperties.getLongPollingTimeout().toSeconds())
                .withNetworkConnectionConfig(clientConfig())
                .withRelyingPartyName(relyingPartyInfo.getName())
                .withRelyingPartyUUID(relyingPartyInfo.getUuid())
                .build();
    }

    private ClientConfig clientConfig() {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.property(ClientProperties.CONNECT_TIMEOUT, (int) configurationProperties.getConnectTimeout().toMillis());
        clientConfig.property(ClientProperties.READ_TIMEOUT, (int) configurationProperties.getLongPollingTimeout().plusMillis(5000).toMillis());
        return clientConfig;
    }

    private KeyStore getMidTruststore() {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            InputStream is = resourceLoader.getResource(configurationProperties.getTruststorePath()).getInputStream();
            keyStore.load(is, configurationProperties.getTruststorePassword().toCharArray());
            return keyStore;
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static MobileIdSessionStatus mapToMidStatus(String state, String result) {
        if ("COMPLETE".equals(state) && "OK".equals(result)) {
            return MobileIdSessionStatus.SIGNATURE;
        } else {
            throw new ClientException("Mobile-ID service returned unexpected response",
                    new IllegalStateException("MID REST responded with: state=" + state + "; result=" + result));
        }
    }

    private static CertificateStatus mapToCertificateStatus(String result) {
        if (CertificateStatus.NOT_FOUND.name().equals(result)) {
            return CertificateStatus.NOT_FOUND;
        }
        return CertificateStatus.UNEXPECTED_STATUS;
    }
}