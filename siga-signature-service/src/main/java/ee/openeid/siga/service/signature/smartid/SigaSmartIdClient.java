package ee.openeid.siga.service.signature.smartid;

import ee.openeid.siga.common.event.LogParam;
import ee.openeid.siga.common.event.Param;
import ee.openeid.siga.common.event.SigaEventLog;
import ee.openeid.siga.common.event.SigaEventName;
import ee.openeid.siga.common.event.XPath;
import ee.openeid.siga.common.exception.ClientException;
import ee.openeid.siga.common.exception.SigaSmartIdException;
import ee.openeid.siga.common.model.SmartIdInformation;
import ee.sk.smartid.HashType;
import ee.sk.smartid.SignableHash;
import ee.sk.smartid.SmartIdCertificate;
import ee.sk.smartid.SmartIdClient;
import ee.sk.smartid.exception.CertificateNotFoundException;
import ee.sk.smartid.exception.DocumentUnusableException;
import ee.sk.smartid.exception.SessionNotFoundException;
import ee.sk.smartid.exception.SessionTimeoutException;
import ee.sk.smartid.exception.SmartIdException;
import ee.sk.smartid.exception.UserRefusedException;
import ee.sk.smartid.rest.SmartIdConnector;
import ee.sk.smartid.rest.dao.NationalIdentity;
import ee.sk.smartid.rest.dao.SessionStatus;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.spi.DSSUtils;
import org.digidoc4j.DataToSign;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.ServerErrorException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class SigaSmartIdClient {

    private static final String SMART_ID_CERTIFICATE_LEVEL = "QUALIFIED";
    private static final String SMART_ID_SERVICE_ERROR = "Smart-ID service error";
    private static final String SMART_ID_SERVICE_UNEXPECTED_RESPONSE = "Smart-ID service returned unexpected response";

    private static final Map<String, SmartIdSessionStatus> COMPLETE_STATE_STATUS_MAPPINGS = Map.of(
            "OK", SmartIdSessionStatus.OK,
            "USER_REFUSED", SmartIdSessionStatus.USER_REFUSED,
            "TIMEOUT", SmartIdSessionStatus.TIMEOUT,
            "DOCUMENT_UNUSABLE", SmartIdSessionStatus.DOCUMENT_UNUSABLE
    );

    private SmartIdServiceConfigurationProperties smartIdServiceConfigurationProperties;

    @SigaEventLog(eventName = SigaEventName.GET_SMART_ID_CERTIFICATE,
            logStaticParameters = {@LogParam(name = SigaEventName.EventParam.REQUEST_URL, value = "${siga.sid.url}")})
    public SmartIdCertificate getCertificate(SmartIdInformation smartIdInformation) {
        try {
            NationalIdentity nationalIdentity = new NationalIdentity(smartIdInformation.getCountry(), smartIdInformation.getPersonIdentifier());
            SmartIdClient smartIdClient = createSmartIdClient(smartIdInformation);
            return smartIdClient
                    .getCertificate()
                    .withCertificateLevel(SMART_ID_CERTIFICATE_LEVEL)
                    .withNationalIdentity(nationalIdentity)
                    .fetch();
        } catch (CertificateNotFoundException e) {
            throw new SigaSmartIdException(SmartIdErrorStatus.NOT_FOUND.getSigaMessage());
        } catch (UserRefusedException e) {
            throw new SigaSmartIdException(SmartIdSessionStatus.USER_REFUSED.getSigaMessage());
        } catch (SessionTimeoutException e) {
            throw new SigaSmartIdException(SmartIdSessionStatus.TIMEOUT.getSigaMessage());
        } catch (DocumentUnusableException e) {
            throw new SigaSmartIdException(SmartIdSessionStatus.DOCUMENT_UNUSABLE.getSigaMessage());
        } catch (SmartIdException | ServerErrorException e) {
            throw new ClientException(SMART_ID_SERVICE_ERROR, e);
        }
    }

    @SigaEventLog(eventName = SigaEventName.SMART_ID_SIGN_HASH,
            logParameters = {@Param(index = 1, fields = {@XPath(name = "relying_party_name", xpath = "relyingPartyName")})},
            logReturnObject = {@XPath(name = "sid_session_id", xpath = "sessionCode")},
            logStaticParameters = {@LogParam(name = SigaEventName.EventParam.REQUEST_URL, value = "${siga.sid.url}")})
    public InitSmartIdSignatureResponse initSmartIdSigning(SmartIdInformation smartIdInformation, DataToSign dataToSign, String documentNumber) {
        ee.sk.smartid.SmartIdClient smartIdClient = createSmartIdClient(smartIdInformation);
        SignableHash signableHash = createSignableHash(dataToSign);
        String challengeId = signableHash.calculateVerificationCode();
        try {
            String sessionCode = smartIdClient
                    .createSignature()
                    .withDocumentNumber(documentNumber)
                    .withSignableHash(signableHash)
                    .withDisplayText(smartIdInformation.getMessageToDisplay())
                    .withCertificateLevel(SMART_ID_CERTIFICATE_LEVEL)
                    .initiateSigning();
            InitSmartIdSignatureResponse initSmartIdSignatureResponse = new InitSmartIdSignatureResponse();
            initSmartIdSignatureResponse.setSessionCode(sessionCode);
            initSmartIdSignatureResponse.setChallengeId(challengeId);
            return initSmartIdSignatureResponse;
        } catch (SmartIdException | ServerErrorException e) {
            throw new ClientException(SMART_ID_SERVICE_ERROR, e);
        }
    }

    @SigaEventLog(eventName = SigaEventName.SMART_ID_GET_SIGN_HASH_STATUS,
            logParameters = {@Param(name = "sid_session_id", index = 1)},
            logReturnObject = {@XPath(name = "sid_status", xpath = "result.endResult")},
            logStaticParameters = {@LogParam(name = SigaEventName.EventParam.REQUEST_URL, value = "${siga.sid.url}")})
    public SmartIdStatusResponse getSmartIdStatus(SmartIdInformation smartIdInformation, String sessionCode) {
        try {
            ee.sk.smartid.SmartIdClient smartIdClient = createSmartIdClient(smartIdInformation);
            SmartIdConnector connector = smartIdClient.getSmartIdConnector();
            connector.setSessionStatusResponseSocketOpenTime(TimeUnit.MILLISECONDS, smartIdServiceConfigurationProperties.getSessionStatusResponseSocketOpenTime());
            SessionStatus sessionStatus = connector.getSessionStatus(sessionCode);
            return mapToSmartIdStatusResponse(sessionStatus);
        } catch (SessionNotFoundException e) {
            throw new SigaSmartIdException(SmartIdErrorStatus.SESSION_NOT_FOUND.getSigaMessage());
        } catch (ServerErrorException e) {
            throw new ClientException(SMART_ID_SERVICE_ERROR, e);
        }
    }

    private SignableHash createSignableHash(DataToSign dataToSign) {
        byte[] digest = DSSUtils.digest(DigestAlgorithm.SHA512, dataToSign.getDataToSign());
        SignableHash signableHash = new SignableHash();
        signableHash.setHash(digest);
        signableHash.setHashType(HashType.SHA512);
        return signableHash;
    }

    SmartIdClient createSmartIdClient(SmartIdInformation smartIdInformation) {
        SmartIdClient client = new SmartIdClient();
        client.setHostUrl(smartIdServiceConfigurationProperties.getUrl());
        client.setSessionStatusResponseSocketOpenTime(TimeUnit.MILLISECONDS, smartIdServiceConfigurationProperties.getSessionStatusResponseSocketOpenTime());
        client.setRelyingPartyName(smartIdInformation.getRelyingPartyName());
        client.setRelyingPartyUUID(smartIdInformation.getRelyingPartyUuid());
        return client;
    }

    private SmartIdStatusResponse mapToSmartIdStatusResponse(SessionStatus sessionStatus) {
        if ("RUNNING".equals(sessionStatus.getState())) {
            return SmartIdStatusResponse.builder()
                    .status(SmartIdSessionStatus.RUNNING)
                    .build();
        } else if ("COMPLETE".equals(sessionStatus.getState())) {
            return mapToSmartIdSessionCompleteStatusResponse(sessionStatus);
        }

        throw new ClientException(SMART_ID_SERVICE_UNEXPECTED_RESPONSE,
                new IllegalStateException("Smart-ID service responded with unexpected state: " + sessionStatus.getState()));
    }

    private SmartIdStatusResponse mapToSmartIdSessionCompleteStatusResponse(SessionStatus sessionStatus) {
        String endResult = sessionStatus.getResult().getEndResult();
        SmartIdSessionStatus status = COMPLETE_STATE_STATUS_MAPPINGS.get(endResult);

        if (status == null) {
            throw new ClientException(SMART_ID_SERVICE_UNEXPECTED_RESPONSE,
                    new IllegalStateException("Smart-ID service responded with unexpected end result: " + endResult));
        }

        if (status == SmartIdSessionStatus.OK) {
            return SmartIdStatusResponse.builder()
                    .status(status)
                    .signature(extractSignature(sessionStatus))
                    .build();
        } else {
            return SmartIdStatusResponse.builder()
                    .status(status)
                    .build();
        }
    }

    private byte[] extractSignature(SessionStatus sessionStatus) {
        try {
            return Base64.getDecoder().decode(sessionStatus.getSignature().getValue().getBytes());
        } catch (Exception e) {
            throw new ClientException(SMART_ID_SERVICE_UNEXPECTED_RESPONSE,
                    new IllegalStateException("Unable to extract signature from Smart-ID service response: ", e));
        }
    }

    @Autowired
    public void setSmartIdServiceConfigurationProperties(SmartIdServiceConfigurationProperties
                                                                 smartIdServiceConfigurationProperties) {
        this.smartIdServiceConfigurationProperties = smartIdServiceConfigurationProperties;
    }
}
