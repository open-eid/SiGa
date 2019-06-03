package ee.openeid.siga.service.signature.container;

import ee.openeid.siga.common.DataToSignWrapper;
import ee.openeid.siga.common.MobileIdChallenge;
import ee.openeid.siga.common.MobileIdInformation;
import ee.openeid.siga.common.Result;
import ee.openeid.siga.common.SigningType;
import ee.openeid.siga.common.event.SigaEvent;
import ee.openeid.siga.common.event.SigaEventLogger;
import ee.openeid.siga.common.event.SigaEventName;
import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.common.exception.SignatureCreationException;
import ee.openeid.siga.common.session.DataToSignHolder;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.mobileid.client.DigiDocService;
import ee.openeid.siga.mobileid.client.MobileIdService;
import ee.openeid.siga.mobileid.model.dds.GetMobileCertificateResponse;
import ee.openeid.siga.mobileid.model.mid.GetMobileSignHashStatusResponse;
import ee.openeid.siga.mobileid.model.mid.MobileSignHashResponse;
import ee.openeid.siga.mobileid.model.mid.ProcessStatusType;
import ee.openeid.siga.service.signature.session.SessionIdGenerator;
import ee.openeid.siga.session.SessionService;
import eu.europa.esig.dss.DSSUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.digidoc4j.DataToSign;
import org.digidoc4j.Signature;
import org.digidoc4j.SignatureParameters;
import org.digidoc4j.X509Cert;
import org.digidoc4j.exceptions.TechnicalException;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Base64;
import java.util.function.Predicate;

import static ee.openeid.siga.common.event.SigaEvent.EventResultType.EXCEPTION;
import static ee.openeid.siga.common.event.SigaEventName.ErrorCode.SIGNATURE_FINALIZING_ERROR;
import static ee.openeid.siga.common.event.SigaEventName.ErrorCode.SIGNATURE_FINALIZING_REQUEST_ERROR;
import static ee.openeid.siga.common.event.SigaEventName.EventParam.ISSUING_CA;
import static ee.openeid.siga.common.event.SigaEventName.EventParam.SIGNATURE_ID;
import static ee.openeid.siga.common.event.SigaEventName.FINALIZE_SIGNATURE;
import static ee.openeid.siga.common.util.CertificateUtil.createX509Certificate;

@Slf4j
public abstract class ContainerSigningService {

    private SigaEventLogger sigaEventLogger;
    protected SessionService sessionService;
    private static final String OK_RESPONSE = Result.OK.name();
    private DigiDocService digiDocService;
    private MobileIdService mobileIdService;

    public DataToSignWrapper createDataToSign(String containerId, SignatureParameters signatureParameters) {
        Session sessionHolder = getSession(containerId);
        verifySigningObjectExistence(sessionHolder);
        DataToSign dataToSign = buildDataToSign(sessionHolder, signatureParameters);
        String generatedSignatureId = SessionIdGenerator.generateSessionId();
        sessionHolder.addDataToSign(generatedSignatureId, DataToSignHolder.builder().dataToSign(dataToSign).signingType(SigningType.REMOTE).build());
        sessionService.update(containerId, sessionHolder);
        return DataToSignWrapper.builder().dataToSign(dataToSign).generatedSignatureId(generatedSignatureId).build();
    }

    public Result finalizeSigning(String containerId, String signatureId, String signatureValue) {
        Session sessionHolder = getSession(containerId);
        DataToSignHolder dataToSignHolder = sessionHolder.getDataToSignHolder(signatureId);
        validateRemoteSession(dataToSignHolder, signatureId);
        DataToSign dataToSign = dataToSignHolder.getDataToSign();

        byte[] base64Decoded = Base64.getDecoder().decode(signatureValue.getBytes());
        Signature signature = finalizeSignature(dataToSign, base64Decoded);

        addSignatureToSession(sessionHolder, signature, signatureId);
        sessionService.update(containerId, sessionHolder);
        return Result.OK;
    }

    public MobileIdChallenge startMobileIdSigning(String containerId, MobileIdInformation mobileIdInformation, SignatureParameters signatureParameters) {
        Session sessionHolder = getSession(containerId);
        verifySigningObjectExistence(sessionHolder);

        GetMobileCertificateResponse signingCertificate = digiDocService.getMobileCertificate(mobileIdInformation.getPersonIdentifier(), mobileIdInformation.getPhoneNo());
        signatureParameters.setSigningCertificate(createX509Certificate(signingCertificate.getSignCertData().getBytes()));
        DataToSign dataToSign = buildDataToSign(sessionHolder, signatureParameters);

        MobileSignHashResponse response = initMobileSign(dataToSign, mobileIdInformation);

        String generatedSignatureId = SessionIdGenerator.generateSessionId();
        sessionHolder.addDataToSign(generatedSignatureId, DataToSignHolder.builder().dataToSign(dataToSign).signingType(SigningType.MOBILE_ID).sessionCode(response.getSesscode()).build());
        sessionService.update(containerId, sessionHolder);

        return MobileIdChallenge.builder().challengeId(response.getChallengeID()).generatedSignatureId(generatedSignatureId).build();
    }

    public String processMobileStatus(String containerId, String signatureId) {
        Session sessionHolder = getSession(containerId);
        validateMobileIdSession(sessionHolder.getDataToSignHolder(signatureId), signatureId);
        DataToSignHolder dataToSignHolder = sessionHolder.getDataToSignHolder(signatureId);
        GetMobileSignHashStatusResponse getMobileSignHashStatusResponse = mobileIdService.getMobileSignHashStatus(dataToSignHolder.getSessionCode());
        ProcessStatusType status = getMobileSignHashStatusResponse.getStatus();
        if (ProcessStatusType.SIGNATURE == status) {
            DataToSign dataToSign = dataToSignHolder.getDataToSign();
            Signature signature = finalizeSignature(dataToSign, getMobileSignHashStatusResponse.getSignature());

            addSignatureToSession(sessionHolder, signature, signatureId);
            sessionService.update(containerId, sessionHolder);
        }
        return status.name();
    }


    /**
     * TSP/OCSP request events are currently generated by intercepting logging events from
     * digidoc4j library. Jira task DD4J-415 will introduce new features to observe
     * requests made to TSP/OCSP providers.
     *
     * @see <a href="https://jira.ria.ee/browse/DD4J-415">Jira task DD4J-415</a>
     */
    protected Signature finalizeSignature(DataToSign dataToSign, byte[] base64Decoded) {
        SigaEvent startEvent = sigaEventLogger.logStartEvent(FINALIZE_SIGNATURE).addEventParameter(SIGNATURE_ID, dataToSign.getSignatureParameters().getSignatureId());
        try {
            Signature signature = dataToSign.finalize(base64Decoded);
            logEndEvent(startEvent, signature);
            return signature;
        } catch (TechnicalException e) {
            log.error("Unable to finalize signature", e);
            logExceptionEvent(startEvent, e);
            throw new SignatureCreationException("Unable to finalize signature");
        }
    }

    private MobileSignHashResponse initMobileSign(DataToSign dataToSign, MobileIdInformation mobileIdInformation){
        byte[] digest = DSSUtils.digest(dataToSign.getDigestAlgorithm().getDssDigestAlgorithm(), dataToSign.getDataToSign());
        MobileSignHashResponse response = mobileIdService.initMobileSignHash(mobileIdInformation, dataToSign.getDigestAlgorithm().name(), Hex.encodeHexString(digest));
        if (!OK_RESPONSE.equals(response.getStatus())) {
            throw new IllegalStateException("Invalid DigiDocService response");
        }
        return response;
    }

    private void logEndEvent(SigaEvent startEvent, Signature signature) {
        X509Cert tstCert = signature.getTimeStampTokenCertificate();
        if (tstCert != null) {
            sigaEventLogger.getLastMachingEvent(e -> SigaEventName.TSA_REQUEST.equals(e.getEventName())).ifPresent(e -> {
                e.addEventParameter(ISSUING_CA, tstCert.issuerName());
            });
        }
        X509Cert ocspCert = signature.getOCSPCertificate();
        if (ocspCert != null) {
            sigaEventLogger.getLastMachingEvent(e -> SigaEventName.OCSP_REQUEST.equals(e.getEventName())).ifPresent(e -> {
                e.addEventParameter(ISSUING_CA, ocspCert.issuerName());
            });
        }
        SigaEvent endEvent = sigaEventLogger.logEndEventFor(startEvent);
        endEvent.addEventParameter(SIGNATURE_ID, signature.getId());
    }

    private void logExceptionEvent(SigaEvent ocspStartEvent, TechnicalException e) {
        String errorMessage = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
        if (StringUtils.contains(errorMessage, "Unable to process GET call for url")) {
            String errorUrl = StringUtils.substringBetween(e.getCause().getMessage(), "'", "'");
            sigaEventLogger.logExceptionEventFor(ocspStartEvent, SIGNATURE_FINALIZING_REQUEST_ERROR, errorMessage);
            Predicate<SigaEvent> predicate = event -> event.containsParameterWithValue(errorUrl);
            sigaEventLogger.getFirstMachingEventAfter(ocspStartEvent, predicate).ifPresent(requestEventFromDigidoc -> {
                requestEventFromDigidoc.setErrorCode(SIGNATURE_FINALIZING_REQUEST_ERROR);
                requestEventFromDigidoc.setErrorMessage(errorMessage);
                requestEventFromDigidoc.setResultType(EXCEPTION);
            });
        } else {
            sigaEventLogger.logExceptionEventForIntermediateEvents(ocspStartEvent, SIGNATURE_FINALIZING_ERROR, errorMessage);
        }
    }

    private void validateRemoteSession(DataToSignHolder dataToSignHolder, String signatureId) {
        validateSession(dataToSignHolder, signatureId, SigningType.REMOTE);
    }

    private void validateMobileIdSession(DataToSignHolder dataToSignHolder, String signatureId) {
        validateSession(dataToSignHolder, signatureId, SigningType.MOBILE_ID);
        if (StringUtils.isBlank(dataToSignHolder.getSessionCode())) {
            throw new InvalidSessionDataException("Unable to finalize signature. Session code not found");
        }
    }

    private void validateSession(DataToSignHolder dataToSignHolder, String signatureId, SigningType signingType) {
        if (dataToSignHolder == null || dataToSignHolder.getDataToSign() == null) {
            throw new InvalidSessionDataException("Unable to finalize signature. No data to sign with signature Id: " + signatureId);
        }
        if (signingType != dataToSignHolder.getSigningType()) {
            throw new InvalidSessionDataException("Unable to finalize signature for signing type: " + dataToSignHolder.getSigningType());
        }
    }

    public abstract DataToSign buildDataToSign(Session session, SignatureParameters signatureParameters);

    public abstract Session getSession(String containerId);

    public abstract void addSignatureToSession(Session sessionHolder, Signature signature, String signatureId);

    public SessionService getSessionService() {
        return sessionService;
    }

    public abstract void verifySigningObjectExistence(Session session);

    @Autowired
    public void setSessionService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Autowired
    public void setSigaEventLogger(SigaEventLogger sigaEventLogger) {
        this.sigaEventLogger = sigaEventLogger;
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
