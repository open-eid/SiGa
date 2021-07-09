package ee.openeid.siga.service.signature.smartid;

import ee.openeid.siga.common.event.LogParam;
import ee.openeid.siga.common.event.Param;
import ee.openeid.siga.common.event.SigaEventLog;
import ee.openeid.siga.common.event.SigaEventName;
import ee.openeid.siga.common.event.XPath;
import ee.openeid.siga.common.exception.ClientException;
import ee.openeid.siga.common.exception.SigaSmartIdException;
import ee.openeid.siga.common.model.RelyingPartyInfo;
import ee.openeid.siga.common.model.SmartIdInformation;
import ee.openeid.siga.common.util.CertificateUtil;
import ee.openeid.siga.common.util.TokenGenerator;
import ee.sk.smartid.HashType;
import ee.sk.smartid.SignableHash;
import ee.sk.smartid.SmartIdCertificate;
import ee.sk.smartid.SmartIdClient;
import ee.sk.smartid.exception.SessionNotFoundException;
import ee.sk.smartid.exception.SmartIdException;
import ee.sk.smartid.exception.useraccount.DocumentUnusableException;
import ee.sk.smartid.exception.useraccount.UserAccountNotFoundException;
import ee.sk.smartid.exception.useraction.SessionTimeoutException;
import ee.sk.smartid.exception.useraction.UserRefusedException;
import ee.sk.smartid.rest.SmartIdConnector;
import ee.sk.smartid.rest.dao.CertificateRequest;
import ee.sk.smartid.rest.dao.Interaction;
import ee.sk.smartid.rest.dao.SemanticsIdentifier;
import ee.sk.smartid.rest.dao.SessionStatus;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.spi.DSSUtils;
import org.apache.commons.lang3.StringUtils;
import org.digidoc4j.DataToSign;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.ServerErrorException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@Component
public class SigaSmartIdClient {

    private static final String SMART_ID_CERTIFICATE_LEVEL = "QUALIFIED";
    private static final String SMART_ID_SERVICE_ERROR = "Smart-ID service error";
    private static final String SMART_ID_SERVICE_UNEXPECTED_RESPONSE = "Smart-ID service returned unexpected response";
    private static final String PERSON_SEMANTICS_IDENTIFIER = "PNO";
    private static final String MINIMUM_CERTIFICATE_LEVEL = "QSCD";
    private static final Map<String, SmartIdSessionStatus> COMPLETE_STATE_STATUS_MAPPINGS;

    static {
        LinkedHashMap<String, SmartIdSessionStatus> statuses = new LinkedHashMap<>();
        statuses.put("OK", SmartIdSessionStatus.OK);
        statuses.put("USER_REFUSED", SmartIdSessionStatus.USER_REFUSED);
        statuses.put("USER_REFUSED_CERT_CHOICE", SmartIdSessionStatus.USER_REFUSED_CERT_CHOICE);
        statuses.put("USER_REFUSED_CONFIRMATIONMESSAGE", SmartIdSessionStatus.USER_REFUSED_CONFIRMATIONMESSAGE);
        statuses.put("USER_REFUSED_CONFIRMATIONMESSAGE_WITH_VC_CHOICE", SmartIdSessionStatus.USER_REFUSED_CONFIRMATIONMESSAGE_WITH_VC_CHOICE);
        statuses.put("USER_REFUSED_DISPLAYTEXTANDPIN", SmartIdSessionStatus.USER_REFUSED_DISPLAYTEXTANDPIN);
        statuses.put("USER_REFUSED_VC_CHOICE", SmartIdSessionStatus.USER_REFUSED_VC_CHOICE);
        statuses.put("WRONG_VC", SmartIdSessionStatus.WRONG_VC);
        statuses.put("TIMEOUT", SmartIdSessionStatus.TIMEOUT);
        statuses.put("DOCUMENT_UNUSABLE", SmartIdSessionStatus.DOCUMENT_UNUSABLE);
        statuses.put("REQUIRED_INTERACTION_NOT_SUPPORTED_BY_APP", SmartIdSessionStatus.REQUIRED_INTERACTION_NOT_SUPPORTED_BY_APP);
        COMPLETE_STATE_STATUS_MAPPINGS = Collections.unmodifiableMap(statuses);
    }

    private final ResourceLoader resourceLoader;
    private final SmartIdServiceConfigurationProperties smartIdServiceConfigurationProperties;


    @Autowired
    public SigaSmartIdClient(ResourceLoader resourceLoader, SmartIdServiceConfigurationProperties smartIdServiceConfigurationProperties) {
        this.resourceLoader = resourceLoader;
        this.smartIdServiceConfigurationProperties = smartIdServiceConfigurationProperties;
    }

    @SigaEventLog(eventName = SigaEventName.SMART_ID_CERTIFICATE_CHOICE,
            logParameters = {@Param(index = 0, fields = {@XPath(name = "relying_party_name", xpath = "name")})},
            logStaticParameters = {@LogParam(name = SigaEventName.EventParam.REQUEST_URL, value = "${siga.sid.url}")})
    public String initiateCertificateChoice(RelyingPartyInfo relyingPartyInfo, SmartIdInformation smartIdInformation) {
        SemanticsIdentifier semanticsIdentifier = new SemanticsIdentifier(createSemanticsIdentifier(smartIdInformation.getCountry(),
                smartIdInformation.getPersonIdentifier()));
        CertificateRequest certificateRequest = createCertificateRequest(relyingPartyInfo);
        SmartIdConnector connector = getSmartIdConnector(relyingPartyInfo);
        try {
            return connector.getCertificate(semanticsIdentifier, certificateRequest).getSessionID();
        } catch (UserAccountNotFoundException e) {
            throw new SigaSmartIdException(SmartIdErrorStatus.NOT_FOUND.getSigaMessage());
        } catch (ClientErrorException e) {
            verifyClientError(e.getResponse().getStatus());
            throw new ClientException(SMART_ID_SERVICE_ERROR, e);
        } catch (SmartIdException | ServerErrorException e) {
            throw new ClientException(SMART_ID_SERVICE_ERROR, e);
        }
    }

    @SigaEventLog(eventName = SigaEventName.SMART_ID_GET_CERTIFICATE,
            logStaticParameters = {@LogParam(name = SigaEventName.EventParam.REQUEST_URL, value = "${siga.sid.url}")})
    public SmartIdCertificate getCertificate(RelyingPartyInfo relyingPartyInfo, SmartIdInformation smartIdInformation) {
        try {
            SmartIdClient smartIdClient = createSmartIdClient(relyingPartyInfo);
            return smartIdClient
                    .getCertificate()
                    .withCertificateLevel(SMART_ID_CERTIFICATE_LEVEL)
                    .withDocumentNumber(smartIdInformation.getDocumentNumber())
                    .withNonce(TokenGenerator.generateToken(30))
                    .fetch();
        } catch (UserAccountNotFoundException e) {
            throw new SigaSmartIdException(SmartIdErrorStatus.NOT_FOUND.getSigaMessage());
        } catch (UserRefusedException e) {
            throw new SigaSmartIdException(SmartIdSessionStatus.USER_REFUSED.getSigaSigningMessage());
        } catch (SessionTimeoutException e) {
            throw new SigaSmartIdException(SmartIdSessionStatus.TIMEOUT.getSigaSigningMessage());
        } catch (DocumentUnusableException e) {
            throw new SigaSmartIdException(SmartIdSessionStatus.DOCUMENT_UNUSABLE.getSigaSigningMessage());
        } catch (ClientErrorException e) {
            verifyClientError(e.getResponse().getStatus());
            throw new ClientException(SMART_ID_SERVICE_ERROR, e);
        } catch (SmartIdException | ServerErrorException e) {
            throw new ClientException(SMART_ID_SERVICE_ERROR, e);
        }
    }

    @SigaEventLog(eventName = SigaEventName.SMART_ID_SIGN_HASH,
            logParameters = {@Param(index = 0, fields = {@XPath(name = "relying_party_name", xpath = "name")})},
            logReturnObject = {@XPath(name = "sid_session_id", xpath = "sessionCode")},
            logStaticParameters = {@LogParam(name = SigaEventName.EventParam.REQUEST_URL, value = "${siga.sid.url}")})
    public InitSmartIdSignatureResponse initSmartIdSigning(RelyingPartyInfo relyingPartyInfo, SmartIdInformation smartIdInformation, DataToSign dataToSign) {
        SmartIdClient smartIdClient = createSmartIdClient(relyingPartyInfo);
        SignableHash signableHash = createSignableHash(dataToSign);
        List<Interaction> allowedInteractionsOrder = createAllowedInteractionsOrder(smartIdInformation.getMessageToDisplay());
        String challengeId = signableHash.calculateVerificationCode();
        try {
            String sessionCode = smartIdClient
                    .createSignature()
                    .withDocumentNumber(smartIdInformation.getDocumentNumber())
                    .withSignableHash(signableHash)
                    .withAllowedInteractionsOrder(allowedInteractionsOrder)
                    .withNonce(TokenGenerator.generateToken(30))
                    .withCertificateLevel(SMART_ID_CERTIFICATE_LEVEL)
                    .initiateSigning();
            InitSmartIdSignatureResponse initSmartIdSignatureResponse = new InitSmartIdSignatureResponse();
            initSmartIdSignatureResponse.setSessionCode(sessionCode);
            initSmartIdSignatureResponse.setChallengeId(challengeId);
            return initSmartIdSignatureResponse;
        } catch (SmartIdException | ServerErrorException | ClientErrorException e) {
            throw new ClientException(SMART_ID_SERVICE_ERROR, e);
        }
    }

    @SigaEventLog(eventName = SigaEventName.SMART_ID_GET_SIGN_HASH_STATUS,
            logParameters = {@Param(name = "sid_session_id", index = 1)},
            logReturnObject = {@XPath(name = "sid_status", xpath = "status")},
            logStaticParameters = {@LogParam(name = SigaEventName.EventParam.REQUEST_URL, value = "${siga.sid.url}")})
    public SmartIdStatusResponse getSmartIdSigningStatus(RelyingPartyInfo relyingPartyInfo, String sessionCode) {
        return getSmartIdStatus(relyingPartyInfo, sessionCode);
    }

    @SigaEventLog(eventName = SigaEventName.SMART_ID_GET_CERTIFICATE_STATUS,
            logParameters = {@Param(name = "sid_session_id", index = 1)},
            logReturnObject = {@XPath(name = "sid_status", xpath = "status")},
            logStaticParameters = {@LogParam(name = SigaEventName.EventParam.REQUEST_URL, value = "${siga.sid.url}")})
    public SmartIdStatusResponse getSmartIdCertificateStatus(RelyingPartyInfo relyingPartyInfo, String sessionCode) {
        return getSmartIdStatus(relyingPartyInfo, sessionCode);
    }

    private SmartIdStatusResponse getSmartIdStatus(RelyingPartyInfo relyingPartyInfo, String sessionCode) {
        try {
            SmartIdConnector connector = getSmartIdConnector(relyingPartyInfo);
            SessionStatus sessionStatus = connector.getSessionStatus(sessionCode);
            return mapToSmartIdStatusResponse(sessionStatus);
        } catch (SessionNotFoundException e) {
            throw new SigaSmartIdException(SmartIdErrorStatus.SESSION_NOT_FOUND.getSigaMessage());
        } catch (ClientErrorException e) {
            verifyClientError(e.getResponse().getStatus());
            throw new ClientException(SMART_ID_SERVICE_ERROR, e);
        } catch (ServerErrorException e) {
            throw new ClientException(SMART_ID_SERVICE_ERROR, e);
        }
    }

    private SmartIdConnector getSmartIdConnector(RelyingPartyInfo relyingPartyInfo) {
        SmartIdClient smartIdClient = createSmartIdClient(relyingPartyInfo);
        SmartIdConnector connector = smartIdClient.getSmartIdConnector();
        connector.setSessionStatusResponseSocketOpenTime(TimeUnit.MILLISECONDS, smartIdServiceConfigurationProperties.getSessionStatusResponseSocketOpenTime());
        return connector;
    }

    private String createSemanticsIdentifier(String country, String identityNumber) {
        return PERSON_SEMANTICS_IDENTIFIER + country + "-" + identityNumber;
    }

    private CertificateRequest createCertificateRequest(RelyingPartyInfo relyingPartyInfo) {
        CertificateRequest request = new CertificateRequest();
        request.setNonce(TokenGenerator.generateToken(30));
        request.setRelyingPartyUUID(relyingPartyInfo.getUuid());
        request.setRelyingPartyName(relyingPartyInfo.getName());
        request.setCertificateLevel(MINIMUM_CERTIFICATE_LEVEL);
        return request;
    }

    private SignableHash createSignableHash(DataToSign dataToSign) {
        byte[] digest = DSSUtils.digest(DigestAlgorithm.SHA512, dataToSign.getDataToSign());
        SignableHash signableHash = new SignableHash();
        signableHash.setHash(digest);
        signableHash.setHashType(HashType.SHA512);
        return signableHash;
    }

    private List<Interaction> createAllowedInteractionsOrder(String messageToDisplay) {
        if (messageToDisplay == null) {
            messageToDisplay = StringUtils.EMPTY; // Smart-ID client library does not allow null string
        }
        switch (smartIdServiceConfigurationProperties.getInteractionType()) {
            case DISPLAY_TEXT_AND_PIN:
                return List.of(Interaction.displayTextAndPIN(messageToDisplay));
            case VERIFICATION_CODE_CHOICE:
                return List.of(Interaction.verificationCodeChoice(messageToDisplay));
            default:
                throw new IllegalStateException("Unsupported Smart-ID interaction type: " +
                        smartIdServiceConfigurationProperties.getInteractionType()
                );
        }
    }

    private SmartIdClient createSmartIdClient(RelyingPartyInfo relyingPartyInfo) {
        SmartIdClient client = new SmartIdClient();
        client.setTrustStore(getSidTruststore());
        client.setHostUrl(smartIdServiceConfigurationProperties.getUrl());
        client.setSessionStatusResponseSocketOpenTime(TimeUnit.MILLISECONDS, smartIdServiceConfigurationProperties.getSessionStatusResponseSocketOpenTime());
        client.setRelyingPartyName(relyingPartyInfo.getName());
        client.setRelyingPartyUUID(relyingPartyInfo.getUuid());
        return client;
    }

    private KeyStore getSidTruststore() {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            InputStream is = resourceLoader.getResource(smartIdServiceConfigurationProperties.getTruststorePath()).getInputStream();
            keyStore.load(is, smartIdServiceConfigurationProperties.getTruststorePassword().toCharArray());
            return keyStore;
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
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
        String documentNumber = sessionStatus.getResult().getDocumentNumber();
        SmartIdSessionStatus status = COMPLETE_STATE_STATUS_MAPPINGS.get(endResult);

        if (status == null) {
            throw new ClientException(SMART_ID_SERVICE_UNEXPECTED_RESPONSE,
                    new IllegalStateException("Smart-ID service responded with unexpected end result: " + endResult));
        }

        if (status == SmartIdSessionStatus.OK) {

            SmartIdStatusResponse.SmartIdStatusResponseBuilder builder = SmartIdStatusResponse.builder()
                    .status(status);

            if (sessionStatus.getSignature() != null) {
                builder = builder.signature(extractSignature(sessionStatus));
            }
            if (sessionStatus.getCert() != null) {
                SmartIdCertificate smartIdCertificate = new SmartIdCertificate();
                smartIdCertificate.setCertificate(extractCertificate(sessionStatus));
                smartIdCertificate.setDocumentNumber(documentNumber);
                builder = builder.smartIdCertificate(smartIdCertificate);
            }

            return builder.build();
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

    private X509Certificate extractCertificate(SessionStatus sessionStatus) {
        try {
            byte[] decodedCert = Base64.getDecoder().decode(sessionStatus.getCert().getValue().getBytes());
            return CertificateUtil.createX509Certificate(decodedCert);
        } catch (Exception e) {
            throw new ClientException(SMART_ID_SERVICE_UNEXPECTED_RESPONSE,
                    new IllegalStateException("Unable to extract certificate from Smart-ID service response: ", e));
        }
    }

    private void verifyClientError(int status) {
        if (status == 471) {
            throw new ClientException("No suitable account of requested type found, but user has some other accounts");
        } else if (status == 472) {
            throw new ClientException("Person should view app or self-service portal now");
        }
    }

}
