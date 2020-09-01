package ee.openeid.siga.service.signature.smartid;

import ee.openeid.siga.common.event.LogParam;
import ee.openeid.siga.common.event.Param;
import ee.openeid.siga.common.event.SigaEventLog;
import ee.openeid.siga.common.event.SigaEventName;
import ee.openeid.siga.common.event.XPath;
import ee.openeid.siga.common.exception.ClientException;
import ee.openeid.siga.common.model.SmartIdInformation;
import ee.sk.smartid.HashType;
import ee.sk.smartid.SignableHash;
import ee.sk.smartid.SmartIdCertificate;
import ee.sk.smartid.SmartIdClient;
import ee.sk.smartid.rest.SmartIdConnector;
import ee.sk.smartid.rest.dao.NationalIdentity;
import ee.sk.smartid.rest.dao.SessionStatus;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.spi.DSSUtils;
import org.digidoc4j.DataToSign;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.WebApplicationException;
import java.util.concurrent.TimeUnit;

@Component
public class SigaSmartIdClient {

    private SmartIdServiceConfigurationProperties smartIdServiceConfigurationProperties;
    private static final String SMART_ID_CERTIFICATE_LEVEL = "QUALIFIED";
    public static final String SMART_ID_FINISHED_STATE = "COMPLETE";

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
        } catch (WebApplicationException e) {
            throw new ClientException("Smart-ID service error", e);
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
        } catch (WebApplicationException e) {
            throw new ClientException("Smart-ID service error", e);
        }
    }

    @SigaEventLog(eventName = SigaEventName.SMART_ID_GET_SIGN_HASH_STATUS,
            logParameters = {@Param(name = "sid_session_id", index = 1)},
            logReturnObject = {@XPath(name = "sid_status", xpath = "result.endResult")},
            logStaticParameters = {@LogParam(name = SigaEventName.EventParam.REQUEST_URL, value = "${siga.sid.url}")})
    public SessionStatus getSmartIdStatus(SmartIdInformation smartIdInformation, String sessionCode) {
        try {
            ee.sk.smartid.SmartIdClient smartIdClient = createSmartIdClient(smartIdInformation);
            SmartIdConnector connector = smartIdClient.getSmartIdConnector();
            connector.setSessionStatusResponseSocketOpenTime(TimeUnit.MILLISECONDS, smartIdServiceConfigurationProperties.getSessionStatusResponseSocketOpenTime());
            return connector.getSessionStatus(sessionCode);
        } catch (WebApplicationException e) {
            throw new ClientException("Smart-ID service error", e);
        }
    }

    private SignableHash createSignableHash(DataToSign dataToSign) {
        byte[] digest = DSSUtils.digest(DigestAlgorithm.SHA512, dataToSign.getDataToSign());
        SignableHash signableHash = new SignableHash();
        signableHash.setHash(digest);
        signableHash.setHashType(HashType.SHA512);
        return signableHash;
    }

    private SmartIdClient createSmartIdClient(SmartIdInformation smartIdInformation) {
        SmartIdClient client = new SmartIdClient();
        client.setHostUrl(smartIdServiceConfigurationProperties.getUrl());
        client.setSessionStatusResponseSocketOpenTime(TimeUnit.MILLISECONDS, smartIdServiceConfigurationProperties.getSessionStatusResponseSocketOpenTime());
        client.setRelyingPartyName(smartIdInformation.getRelyingPartyName());
        client.setRelyingPartyUUID(smartIdInformation.getRelyingPartyUuid());
        return client;
    }

    @Autowired
    public void setSmartIdServiceConfigurationProperties(SmartIdServiceConfigurationProperties
                                                                 smartIdServiceConfigurationProperties) {
        this.smartIdServiceConfigurationProperties = smartIdServiceConfigurationProperties;
    }
}
