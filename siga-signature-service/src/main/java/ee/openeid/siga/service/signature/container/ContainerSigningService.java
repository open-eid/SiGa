package ee.openeid.siga.service.signature.container;

import ee.openeid.siga.common.event.SigaEvent;
import ee.openeid.siga.common.event.SigaEventLogger;
import ee.openeid.siga.common.event.SigaEventName;
import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.common.exception.SignatureCreationException;
import ee.openeid.siga.common.model.DataToSignWrapper;
import ee.openeid.siga.common.model.Result;
import ee.openeid.siga.common.model.SigningType;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.common.session.SignatureSession;
import ee.openeid.siga.common.util.LoggingContextUtil;
import ee.openeid.siga.common.util.UUIDGenerator;
import ee.openeid.siga.service.signature.configuration.MobileIdClientConfigurationProperties;
import ee.openeid.siga.service.signature.configuration.SessionStatusReprocessingProperties;
import ee.openeid.siga.service.signature.configuration.SmartIdClientConfigurationProperties;
import ee.openeid.siga.service.signature.mobileid.MobileIdApiClient;
import ee.openeid.siga.service.signature.smartid.SmartIdApiClient;
import ee.openeid.siga.session.SessionService;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.digidoc4j.Configuration;
import org.digidoc4j.DataToSign;
import org.digidoc4j.ServiceType;
import org.digidoc4j.Signature;
import org.digidoc4j.SignatureParameters;
import org.digidoc4j.ValidationResult;
import org.digidoc4j.X509Cert;
import org.digidoc4j.exceptions.CertificateValidationException;
import org.digidoc4j.exceptions.NetworkException;
import org.digidoc4j.exceptions.OCSPRequestFailedException;
import org.digidoc4j.exceptions.TechnicalException;
import org.digidoc4j.impl.ServiceAccessListener;
import org.digidoc4j.impl.ServiceAccessScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.function.Predicate;

import static ee.openeid.siga.common.event.SigaEvent.EventResultType.EXCEPTION;
import static ee.openeid.siga.common.event.SigaEventName.ErrorCode.SIGNATURE_FINALIZING_ERROR;
import static ee.openeid.siga.common.event.SigaEventName.ErrorCode.SIGNATURE_FINALIZING_REQUEST_ERROR;
import static ee.openeid.siga.common.event.SigaEventName.EventParam.ISSUING_CA;
import static ee.openeid.siga.common.event.SigaEventName.EventParam.REQUEST_URL;
import static ee.openeid.siga.common.event.SigaEventName.EventParam.SIGNATURE_ID;
import static ee.openeid.siga.common.event.SigaEventName.FINALIZE_SIGNATURE;

@Slf4j
@Getter(AccessLevel.PACKAGE)
@Setter(onMethod_ = {@Autowired})
public abstract class ContainerSigningService {
    protected Configuration configuration;
    static final String UNABLE_TO_FINALIZE_SIGNATURE = "Unable to finalize signature";
    @Getter
    @Delegate
    private final MobileIdSigningDelegate mobileIdSigningDelegate = new MobileIdSigningDelegate(this);
    @Getter
    @Delegate
    private final SmartIdSigningDelegate smartIdSigningDelegate = new SmartIdSigningDelegate(this);
    private SmartIdClientConfigurationProperties smartIdConfigurationProperties;
    private MobileIdClientConfigurationProperties mobileIdConfigurationProperties;
    private SessionStatusReprocessingProperties reprocessingProperties;
    @Getter
    private SessionService sessionService;
    private SigaEventLogger sigaEventLogger;
    private MobileIdApiClient mobileIdApiClient;
    private SmartIdApiClient smartIdApiClient;
    private ThreadPoolTaskExecutor taskExecutor;
    private Ignite ignite;

    public DataToSignWrapper createDataToSign(String containerId, SignatureParameters signatureParameters) {
        Session sessionHolder = getSession(containerId);
        verifySigningObjectExistence(sessionHolder);

        DataToSign dataToSign = buildDataToSign(sessionHolder, signatureParameters);

        String generatedSignatureId = UUIDGenerator.generateUUID();
        SignatureSession signatureSession = SignatureSession.builder()
                .dataToSign(dataToSign)
                .signingType(SigningType.REMOTE)
                .dataFilesHash(generateDataFilesHash(sessionHolder))
                .build();

        sessionHolder.addSignatureSession(generatedSignatureId, signatureSession);
        sessionService.update(sessionHolder);

        return DataToSignWrapper.builder()
                .dataToSign(dataToSign)
                .generatedSignatureId(generatedSignatureId)
                .build();
    }

    public Result finalizeSigning(String containerId, String signatureId, String signatureValue) {
        Session sessionHolder = getSession(containerId);
        SignatureSession signatureSession = sessionHolder.getSignatureSession(signatureId);
        validateSession(signatureSession, signatureId, SigningType.REMOTE);

        byte[] base64Decoded = Base64.getDecoder().decode(signatureValue.getBytes());
        Signature signature = finalizeSignature(sessionHolder, signatureId, base64Decoded);

        addSignatureToSession(sessionHolder, signature, signatureId);
        sessionService.update(sessionHolder);

        if (SigningType.REMOTE.equals(signatureSession.getSigningType())) {
            X509Certificate certificate = signature.getSigningCertificate().getX509Certificate();
            LoggingContextUtil.addCertificatePolicyOIDsToEventLoggingContext(certificate);
        }

        return Result.OK;
    }

    protected Signature finalizeSignature(Session session, String signatureId, byte[] base64Decoded) {
        validateContainerDataFilesUnchanged(session, signatureId);
        DataToSign dataToSign = session.getSignatureSession(signatureId).getDataToSign();
        SigaEvent startEvent = sigaEventLogger.logStartEvent(FINALIZE_SIGNATURE).addEventParameter(SIGNATURE_ID, dataToSign.getSignatureParameters().getSignatureId());

        Signature signature;
        ServiceAccessListener listener = createServiceAccessListener();

        try (ServiceAccessScope ignored = new ServiceAccessScope(listener)) {
            signature = dataToSign.finalize(base64Decoded);
            validateFinalizedSignature(signature, startEvent);
            logSignatureFinalizationEndEvent(startEvent, signature);
        } catch (CertificateValidationException | TechnicalException e) {
            logSignatureFinalizationExceptionEvent(startEvent, e);
            throw new SignatureCreationException(UNABLE_TO_FINALIZE_SIGNATURE + ". " + e.getMessage(), e);
        } catch (OCSPRequestFailedException e) {
            logSignatureFinalizationExceptionEvent(startEvent, e);
            throw new SignatureCreationException(UNABLE_TO_FINALIZE_SIGNATURE + ". OCSP request failed. Issuing certificate may not be trusted.", e);
        } catch (Exception e) {
            logSignatureFinalizationExceptionEvent(startEvent, e);

            if (e instanceof IllegalArgumentException && e.getMessage().equals("XAdES-LTA requires complete binaries of signed documents! Extension with a DigestDocument is not possible.")) {
                throw new SignatureCreationException(e.getMessage());
            } else {
                throw e;
            }
        }

        return signature;
    }

    void validateContainerDataFilesUnchanged(Session session, String signatureId) {
        SignatureSession signatureSession = session.getSignatureSession(signatureId);

        if (signatureSession.getDataFilesHash() == null) {
            throw new IllegalStateException("Trying to finalize signature without container data files hash in session for data to sign");
        }

        if (!generateDataFilesHash(session).equals(signatureSession.getDataFilesHash())) {
            session.clearSigningSession(signatureId);
            sessionService.update(session);
            throw new InvalidSessionDataException(UNABLE_TO_FINALIZE_SIGNATURE + ". Container data files have been changed after signing was initiated. Repeat signing process");
        }
    }

    private ServiceAccessListener createServiceAccessListener() {
        return e -> {
            if (ServiceType.TSP == e.getServiceType()) {
                sigaEventLogger.logEvent(SigaEvent.buildEventWithParameter(SigaEventName.TSA_REQUEST, REQUEST_URL, e.getServiceUrl()));
            } else {
                sigaEventLogger.logEvent(SigaEvent.buildEventWithParameter(SigaEventName.OCSP_REQUEST, REQUEST_URL, e.getServiceUrl()));
            }
        };
    }

    private void validateFinalizedSignature(Signature signature, SigaEvent finalizationStartEvent) {
        ValidationResult validationResult = signature.validateSignature();
        if (!validationResult.isValid()) {
            IllegalStateException exception = new IllegalStateException("Signature validation failed");
            validationResult.getErrors().forEach(exception::addSuppressed);
            log.error(UNABLE_TO_FINALIZE_SIGNATURE, exception);
            sigaEventLogger.logExceptionEventFor(finalizationStartEvent, SIGNATURE_FINALIZING_ERROR, exception.getMessage());
            throw new SignatureCreationException(UNABLE_TO_FINALIZE_SIGNATURE);
        }
    }

    private void logSignatureFinalizationEndEvent(SigaEvent startEvent, Signature signature) {
        X509Cert tstCert = signature.getTimeStampTokenCertificate();
        if (tstCert != null) {
            sigaEventLogger.getLastMachingEvent(e -> SigaEventName.TSA_REQUEST.equals(e.getEventName())).ifPresent(e ->
                    e.addEventParameter(ISSUING_CA, tstCert.issuerName())
            );
        }
        X509Cert ocspCert = signature.getOCSPCertificate();
        if (ocspCert != null) {
            sigaEventLogger.getLastMachingEvent(e -> SigaEventName.OCSP_REQUEST.equals(e.getEventName())).ifPresent(e ->
                    e.addEventParameter(ISSUING_CA, ocspCert.issuerName())
            );
        }
        SigaEvent endEvent = sigaEventLogger.logEndEventFor(startEvent);
        endEvent.addEventParameter(SIGNATURE_ID, signature.getId());
    }

    private void logSignatureFinalizationExceptionEvent(SigaEvent startEvent, Exception e) {
        log.error(UNABLE_TO_FINALIZE_SIGNATURE, e);
        String errorMessage = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();

        if (e instanceof NetworkException) {
            NetworkException networkException = (NetworkException) e;
            String errorUrl = networkException.getServiceUrl();
            updateRequestLoggingEvent(errorUrl, startEvent, SIGNATURE_FINALIZING_REQUEST_ERROR, errorMessage);
            sigaEventLogger.logExceptionEventFor(startEvent, SIGNATURE_FINALIZING_REQUEST_ERROR, errorMessage);
        } else if (e instanceof CertificateValidationException) {
            CertificateValidationException certificateException = (CertificateValidationException) e;
            if (CertificateValidationException.CertificateValidationStatus.REVOKED != certificateException.getCertificateStatus()) {
                updateRequestLoggingEvent(certificateException.getServiceUrl(), startEvent, SIGNATURE_FINALIZING_ERROR, errorMessage);
            }
            sigaEventLogger.logExceptionEventFor(startEvent, SIGNATURE_FINALIZING_ERROR, errorMessage);
        } else {
            sigaEventLogger.logExceptionEventFor(startEvent, SIGNATURE_FINALIZING_ERROR, errorMessage);
        }
    }

    private void updateRequestLoggingEvent(String errorUrl, SigaEvent startEvent, SigaEventName.ErrorCode signatureFinalizingRequestError, String errorMessage) {
        Predicate<SigaEvent> predicate = event -> event.containsParameterWithValue(errorUrl);
        sigaEventLogger.getFirstMachingEventAfter(startEvent, predicate).ifPresent(serviceRequestEvent -> {
            serviceRequestEvent.setErrorCode(signatureFinalizingRequestError);
            serviceRequestEvent.setErrorMessage(errorMessage);
            serviceRequestEvent.setResultType(EXCEPTION);
        });
    }

    void validateSession(SignatureSession signatureSession, String signatureId, SigningType signingType) {
        if (signatureSession == null || signatureSession.getDataToSign() == null) {
            throw new InvalidSessionDataException(UNABLE_TO_FINALIZE_SIGNATURE + ". No data to sign with signature Id: " + signatureId);
        }
        if (signingType != signatureSession.getSigningType()) {
            throw new InvalidSessionDataException(UNABLE_TO_FINALIZE_SIGNATURE + " for signing type: " + signatureSession.getSigningType());
        }
    }

    protected abstract DataToSign buildDataToSign(Session session, SignatureParameters signatureParameters);

    protected abstract Session getSession(String containerId);

    protected abstract void addSignatureToSession(Session sessionHolder, Signature signature, String signatureId);

    protected abstract void verifySigningObjectExistence(Session session);

    public abstract String generateDataFilesHash(Session session);
}
